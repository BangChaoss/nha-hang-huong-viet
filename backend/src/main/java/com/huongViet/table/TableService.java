package com.huongViet.table;

import com.huongViet.reservation.Reservation;
import com.huongViet.reservation.ReservationRepository;
import com.huongViet.reservation.ReservationService;
import com.huongViet.reservation.ReservationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class TableService {

    private static final Logger logger = LoggerFactory.getLogger(TableService.class);

    private final TableRepository tableRepository;
    private final ZoneRepository zoneRepository;
    private final FloorPlanLayoutRepository floorPlanLayoutRepository;
    private final ReservationRepository reservationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public TableService(TableRepository tableRepository,
                        ZoneRepository zoneRepository,
                        FloorPlanLayoutRepository floorPlanLayoutRepository,
                        ReservationRepository reservationRepository,
                        SimpMessagingTemplate messagingTemplate) {
        this.tableRepository = tableRepository;
        this.zoneRepository = zoneRepository;
        this.floorPlanLayoutRepository = floorPlanLayoutRepository;
        this.reservationRepository = reservationRepository;
        this.messagingTemplate = messagingTemplate;
    }

    public List<TableResponse> listAll() {
        LocalDate today = LocalDate.now();
        LocalTime nowTime = LocalTime.now();
        List<Reservation> todayReservations = reservationRepository.findActiveReservationsForDate(today);

        return tableRepository.findAllActiveWithZone()
            .stream()
            .map(table -> TableResponse.from(table, calculateDynamicStatus(table, todayReservations, nowTime)))
            .toList();
    }

    public TableResponse getById(String id) {
        RestaurantTable table = findActiveWithZoneOrThrow(id);
        LocalDate today = LocalDate.now();
        LocalTime nowTime = LocalTime.now();
        List<Reservation> todayReservations = reservationRepository.findActiveReservationsForDate(today);
        return TableResponse.from(table, calculateDynamicStatus(table, todayReservations, nowTime));
    }

    /**
     * Dynamically calculates time-scoped table status based on active reservation window.
     */
    public TableStatus calculateDynamicStatus(RestaurantTable table, List<Reservation> todayReservations, LocalTime nowTime) {
        if (table.getStatus() != TableStatus.AVAILABLE) {
            return table.getStatus();
        }

        if (todayReservations == null || todayReservations.isEmpty()) {
            return TableStatus.AVAILABLE;
        }

        for (Reservation r : todayReservations) {
            boolean matchesTable = (table.getId().equals(r.getTableId()))
                || (r.getMergedTableIds() != null && r.getMergedTableIds().contains(table.getId()));

            if (!matchesTable) {
                continue;
            }

            LocalTime bookingTime = r.getReservationTime();
            LocalTime windowEnd = bookingTime.plus(ReservationService.DINING_WINDOW);

            if (r.getStatus() == ReservationStatus.ARRIVED) {
                if (!nowTime.isBefore(bookingTime.minusMinutes(30)) && !nowTime.isAfter(windowEnd)) {
                    return TableStatus.OCCUPIED;
                }
            } else if (r.getStatus() == ReservationStatus.CONFIRMED) {
                if (!nowTime.isBefore(bookingTime.minusMinutes(30)) && !nowTime.isAfter(windowEnd)) {
                    return TableStatus.RESERVED;
                }
            }
        }

        return TableStatus.AVAILABLE;
    }

    /**
     * BR-57: Unique Table Identification within restaurant.
     */
    @Transactional
    public TableResponse create(TableRequest request) {
        TableZone zone = validateZoneExists(request.zoneId());
        String trimmedNumber = request.tableNumber().trim();

        if (tableRepository.existsByTableNumberIgnoreCaseAndDeletedAtIsNull(trimmedNumber)) {
            logger.warn("Table number already exists: {}", trimmedNumber);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Số bàn đã tồn tại. Vui lòng nhập số bàn khác.");
        }

        RestaurantTable table = new RestaurantTable(
            UUID.randomUUID().toString(),
            zone.getId(),
            trimmedNumber,
            request.capacity()
        );
        table.setZone(zone);

        RestaurantTable saved = tableRepository.save(table);

        // Calculate smart optimal floor plan layout coordinates for new table
        FloorPlanLayout layout = calculateOptimalLayoutPosition(saved);
        floorPlanLayoutRepository.save(layout);

        logger.info("Created table: id={}, tableNumber={}, zone={}, position=({}, {})",
            saved.getId(), saved.getTableNumber(), zone.getZoneName(), layout.getPositionX(), layout.getPositionY());

        TableResponse response = TableResponse.from(saved, TableStatus.AVAILABLE);
        broadcastStatusChange(saved, null, saved.getStatus());
        return response;
    }

    /**
     * BR-56: Active Table Protection.
     * A table cannot be modified if it has status RESERVED or OCCUPIED.
     */
    @Transactional
    public TableResponse update(String id, TableRequest request) {
        RestaurantTable table = findActiveWithZoneOrThrow(id);
        checkActiveProtection(table, "chỉnh sửa");

        TableZone zone = validateZoneExists(request.zoneId());
        String trimmedNumber = request.tableNumber().trim();

        if (tableRepository.existsByTableNumberIgnoreCaseAndDeletedAtIsNullAndIdNot(trimmedNumber, id)) {
            logger.warn("Duplicate table number on update: {}", trimmedNumber);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Số bàn đã tồn tại. Vui lòng nhập số bàn khác.");
        }

        table.setZoneId(zone.getId());
        table.setZone(zone);
        table.setTableNumber(trimmedNumber);
        table.setCapacity(request.capacity());

        RestaurantTable saved = tableRepository.save(table);
        logger.info("Updated table: id={}, tableNumber={}, capacity={}", saved.getId(), saved.getTableNumber(), saved.getCapacity());
        return TableResponse.from(saved);
    }

    /**
     * BR-56: Active Table Protection.
     * A table cannot be deleted if it has status RESERVED or OCCUPIED.
     */
    @Transactional
    public void delete(String id) {
        RestaurantTable table = findActiveWithZoneOrThrow(id);
        checkActiveProtection(table, "xóa");

        table.setDeletedAt(LocalDateTime.now());
        tableRepository.save(table);
        floorPlanLayoutRepository.deleteByTableId(id);
        logger.info("Soft-deleted table: id={}, tableNumber={}", id, table.getTableNumber());
    }

    /**
     * Updates table status and broadcasts live event via WebSocket.
     * BR-59: Maintenance Status Override is applied when set to MAINTENANCE.
     */
    @Transactional
    public TableResponse updateStatus(String id, UpdateTableStatusRequest request) {
        RestaurantTable table = findActiveWithZoneOrThrow(id);
        TableStatus oldStatus = table.getStatus();
        table.setStatus(request.status());
        RestaurantTable saved = tableRepository.save(table);
        if (saved == null) {
            saved = table;
        }

        logger.info("Table status updated: id={}, tableNumber={}, old={}, new={}",
            saved.getId(), saved.getTableNumber(), oldStatus, request.status());

        TableResponse response = TableResponse.from(saved, request.status());
        broadcastStatusChange(saved, oldStatus, request.status());
        return response;
    }

    private RestaurantTable findActiveWithZoneOrThrow(String id) {
        return tableRepository.findByIdActiveWithZone(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bàn."));
    }

    private TableZone validateZoneExists(String zoneId) {
        return zoneRepository.findByIdAndDeletedAtIsNull(zoneId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Khu vực không tồn tại hoặc đã bị xóa."));
    }

    private void checkActiveProtection(RestaurantTable table, String action) {
        LocalDate today = LocalDate.now();
        LocalTime nowTime = LocalTime.now();
        List<Reservation> todayReservations = (reservationRepository != null)
            ? reservationRepository.findActiveReservationsForDate(today)
            : List.of();
        TableStatus currentStatus = calculateDynamicStatus(table, todayReservations, nowTime);
        TableStatus baseStatus = table.getStatus();

        if (baseStatus == TableStatus.RESERVED || baseStatus == TableStatus.OCCUPIED
            || currentStatus == TableStatus.RESERVED || currentStatus == TableStatus.OCCUPIED) {
            TableStatus conflictStatus = (currentStatus == TableStatus.RESERVED || currentStatus == TableStatus.OCCUPIED)
                ? currentStatus : baseStatus;
            logger.warn("BR-56 violation blocked: attempt to {} table {} in status {}",
                action, table.getTableNumber(), conflictStatus);
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Không thể " + action + " bàn đang được sử dụng hoặc đã đặt trước (" + conflictStatus + ")."
            );
        }
    }

    private void broadcastStatusChange(RestaurantTable table, TableStatus oldStatus, TableStatus newStatus) {
        try {
            TableZone zone = table.getZone();
            TableStatusMessage message = new TableStatusMessage(
                table.getId(),
                table.getTableNumber(),
                oldStatus,
                newStatus,
                zone != null ? zone.getZoneName() : null
            );
            messagingTemplate.convertAndSend("/topic/table-status", message);
            logger.debug("Broadcasted table status change: {}", message);
        } catch (Exception ex) {
            logger.error("Failed to broadcast table status change via WebSocket: ", ex);
        }
    }

    /**
     * Calculates optimal non-overlapping coordinates for a newly created table.
     * - If the zone already has tables: places the new table adjacent to existing tables in a tidy grid.
     * - If the zone is brand new (or has no active layouts): finds an empty spot in the visible canvas
     *   without overlapping any other zone's boundaries or tables.
     */
    public FloorPlanLayout calculateOptimalLayoutPosition(RestaurantTable savedTable) {
        double width = savedTable.getCapacity() <= 2 ? 70.0 : (savedTable.getCapacity() <= 4 ? 90.0 : 140.0);
        double height = savedTable.getCapacity() <= 2 ? 70.0 : 90.0;
        FloorPlanShape shape = FloorPlanShape.RECTANGLE;

        List<RestaurantTable> allActiveTables = tableRepository.findAllActiveWithZone();
        List<FloorPlanLayout> allLayouts = floorPlanLayoutRepository.findAll();

        Map<String, FloorPlanLayout> layoutMap = allLayouts.stream()
            .collect(Collectors.toMap(FloorPlanLayout::getTableId, l -> l, (a, b) -> a));

        Map<String, List<FloorPlanLayout>> zoneLayoutsMap = new HashMap<>();
        for (RestaurantTable t : allActiveTables) {
            if (t.getId().equals(savedTable.getId())) continue;
            FloorPlanLayout l = layoutMap.get(t.getId());
            if (l != null && l.getPositionX() != null && l.getPositionY() != null) {
                zoneLayoutsMap.computeIfAbsent(t.getZoneId(), k -> new ArrayList<>()).add(l);
            }
        }

        List<FloorPlanLayout> thisZoneLayouts = zoneLayoutsMap.getOrDefault(savedTable.getZoneId(), Collections.emptyList());

        double candX;
        double candY;

        if (!thisZoneLayouts.isEmpty()) {
            // Case 1: Zone already has tables - place adjacent in grid
            double gapX = 40.0;
            double gapY = 40.0;

            List<double[]> candidates = new ArrayList<>();
            for (FloorPlanLayout ex : thisZoneLayouts) {
                candidates.add(new double[]{ex.getPositionX() + ex.getWidth() + gapX, ex.getPositionY()});
                candidates.add(new double[]{ex.getPositionX(), ex.getPositionY() + ex.getHeight() + gapY});
            }

            double minX = thisZoneLayouts.stream().mapToDouble(FloorPlanLayout::getPositionX).min().orElse(50.0);
            double maxX = thisZoneLayouts.stream().mapToDouble(l -> l.getPositionX() + l.getWidth()).max().orElse(150.0);
            double maxY = thisZoneLayouts.stream().mapToDouble(l -> l.getPositionY() + l.getHeight()).max().orElse(150.0);

            candidates.add(new double[]{maxX + gapX, thisZoneLayouts.get(0).getPositionY()});
            candidates.add(new double[]{minX, maxY + gapY});

            double[] best = null;
            for (double[] cand : candidates) {
                double cx = cand[0];
                double cy = cand[1];
                if (cx < 20 || cy < 20) continue;

                // Check collision with any existing table
                boolean collidesWithTable = false;
                for (FloorPlanLayout other : layoutMap.values()) {
                    if (other.getTableId().equals(savedTable.getId())) continue;
                    if (isAabbOverlap(cx, cy, width, height, other.getPositionX(), other.getPositionY(), other.getWidth(), other.getHeight())) {
                        collidesWithTable = true;
                        break;
                    }
                }
                if (collidesWithTable) continue;

                // Check if new projected zone boundary would collide with other zones
                boolean collidesWithOtherZones = false;
                double newZoneMinX = Math.min(minX, cx);
                double newZoneMinY = Math.min(thisZoneLayouts.stream().mapToDouble(FloorPlanLayout::getPositionY).min().orElse(50.0), cy);
                double newZoneMaxX = Math.max(maxX, cx + width);
                double newZoneMaxY = Math.max(maxY, cy + height);
                double newBoxX = newZoneMinX - 24.0;
                double newBoxY = newZoneMinY - 54.0;
                double newBoxW = newZoneMaxX - newZoneMinX + 48.0;
                double newBoxH = newZoneMaxY - newZoneMinY + 78.0;

                for (Map.Entry<String, List<FloorPlanLayout>> entry : zoneLayoutsMap.entrySet()) {
                    if (entry.getKey().equals(savedTable.getZoneId()) || entry.getValue().isEmpty()) continue;
                    double otherMinX = entry.getValue().stream().mapToDouble(FloorPlanLayout::getPositionX).min().orElse(0);
                    double otherMinY = entry.getValue().stream().mapToDouble(FloorPlanLayout::getPositionY).min().orElse(0);
                    double otherMaxX = entry.getValue().stream().mapToDouble(l -> l.getPositionX() + l.getWidth()).max().orElse(0);
                    double otherMaxY = entry.getValue().stream().mapToDouble(l -> l.getPositionY() + l.getHeight()).max().orElse(0);
                    double otherBoxX = otherMinX - 24.0;
                    double otherBoxY = otherMinY - 54.0;
                    double otherBoxW = otherMaxX - otherMinX + 48.0;
                    double otherBoxH = otherMaxY - otherMinY + 78.0;

                    if (isAabbOverlap(newBoxX, newBoxY, newBoxW, newBoxH, otherBoxX, otherBoxY, otherBoxW, otherBoxH)) {
                        collidesWithOtherZones = true;
                        break;
                    }
                }

                if (!collidesWithOtherZones) {
                    best = cand;
                    break;
                }
            }

            if (best != null) {
                candX = best[0];
                candY = best[1];
            } else {
                candX = minX;
                candY = maxY + gapY;
            }
        } else {
            // Case 2: Brand new zone (no existing layouts in this zone)
            candX = 50.0;
            candY = 70.0;
            boolean found = false;

            for (double y = 70.0; y <= 800.0 && !found; y += 180.0) {
                for (double x = 50.0; x <= 900.0 && !found; x += 180.0) {
                    double boxX = x - 24.0;
                    double boxY = y - 54.0;
                    double boxW = width + 48.0;
                    double boxH = height + 78.0;

                    boolean overlapsAny = false;
                    for (FloorPlanLayout other : layoutMap.values()) {
                        if (isAabbOverlap(x, y, width, height, other.getPositionX(), other.getPositionY(), other.getWidth(), other.getHeight())) {
                            overlapsAny = true;
                            break;
                        }
                    }
                    if (overlapsAny) continue;

                    for (Map.Entry<String, List<FloorPlanLayout>> entry : zoneLayoutsMap.entrySet()) {
                        if (entry.getValue().isEmpty()) continue;
                        double otherMinX = entry.getValue().stream().mapToDouble(FloorPlanLayout::getPositionX).min().orElse(0);
                        double otherMinY = entry.getValue().stream().mapToDouble(FloorPlanLayout::getPositionY).min().orElse(0);
                        double otherMaxX = entry.getValue().stream().mapToDouble(l -> l.getPositionX() + l.getWidth()).max().orElse(0);
                        double otherMaxY = entry.getValue().stream().mapToDouble(l -> l.getPositionY() + l.getHeight()).max().orElse(0);
                        double otherBoxX = otherMinX - 24.0;
                        double otherBoxY = otherMinY - 54.0;
                        double otherBoxW = otherMaxX - otherMinX + 48.0;
                        double otherBoxH = otherMaxY - otherMinY + 78.0;

                        if (isAabbOverlap(boxX, boxY, boxW, boxH, otherBoxX, otherBoxY, otherBoxW, otherBoxH)) {
                            overlapsAny = true;
                            break;
                        }
                    }

                    if (!overlapsAny) {
                        candX = x;
                        candY = y;
                        found = true;
                        break;
                    }
                }
            }
        }

        return new FloorPlanLayout(
            UUID.randomUUID().toString(),
            savedTable.getId(),
            (double) Math.round(candX),
            (double) Math.round(candY),
            width,
            height,
            0.0,
            shape
        );
    }

    private static boolean isAabbOverlap(double x1, double y1, double w1, double h1,
                                         double x2, double y2, double w2, double h2) {
        return x1 < x2 + w2 && x1 + w1 > x2 && y1 < y2 + h2 && y1 + h1 > y2;
    }
}
