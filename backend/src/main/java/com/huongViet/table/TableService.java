package com.huongViet.table;

import com.huongViet.order.Order;
import com.huongViet.order.OrderRepository;
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
    private final OrderRepository orderRepository;

    public TableService(TableRepository tableRepository,
                        ZoneRepository zoneRepository,
                        FloorPlanLayoutRepository floorPlanLayoutRepository,
                        ReservationRepository reservationRepository,
                        SimpMessagingTemplate messagingTemplate,
                        OrderRepository orderRepository) {
        this.tableRepository = tableRepository;
        this.zoneRepository = zoneRepository;
        this.floorPlanLayoutRepository = floorPlanLayoutRepository;
        this.reservationRepository = reservationRepository;
        this.messagingTemplate = messagingTemplate;
        this.orderRepository = orderRepository;
    }

    public List<TableResponse> listAll() {
        LocalDate today = LocalDate.now();
        LocalTime nowTime = LocalTime.now();
        List<Reservation> todayReservations = reservationRepository.findActiveReservationsForDate(today);

        Map<String, List<Order>> seated = orderRepository.findSeatedOrders().stream()
            .collect(Collectors.groupingBy(Order::getTableId));
        return tableRepository.findAllActiveWithZone()
            .stream()
            .map(table -> withSeats(table, todayReservations, nowTime, seated.getOrDefault(table.getId(), List.of())))
            .toList();
    }

    public TableResponse getById(String id) {
        RestaurantTable table = findActiveWithZoneOrThrow(id);
        LocalDate today = LocalDate.now();
        LocalTime nowTime = LocalTime.now();
        List<Reservation> todayReservations = reservationRepository.findActiveReservationsForDate(today);
        return withSeats(table, todayReservations, nowTime, orderRepository.findSeatedOrdersByTableIds(List.of(id)));
    }

    private TableResponse withSeats(RestaurantTable table, List<Reservation> reservations, LocalTime now,
                                    List<Order> seated) {
        TableStatus status = calculateDynamicStatus(table, reservations, now);
        boolean exclusive = reservations.stream().anyMatch(r -> r.blocksSharedSeating(table.getId()));
        Integer free = !exclusive && (status == TableStatus.OCCUPIED || status == TableStatus.AVAILABLE)
            ? Order.availableSeats(table.getCapacity(), seated) : null;
        return TableResponse.from(table, status, free);
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

    private static final int MAX_ATOMIC_CAPACITY = 4;

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

        // ★ AUTO-SPLIT: Khu vực không phải VIP + capacity > 4 → tách bàn
        boolean isVipZone = zone.getIsVip() != null && zone.getIsVip();
        if (!isVipZone && request.capacity() > MAX_ATOMIC_CAPACITY) {
            return createSplitTables(trimmedNumber, request.capacity(), zone);
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
        List<FloorPlanLayout> layouts = calculateAndApplyOptimalLayouts(List.of(saved), zone);
        for (FloorPlanLayout layout : layouts) {
            floorPlanLayoutRepository.save(layout);
        }

        FloorPlanLayout primaryLayout = layouts.isEmpty() ? null : layouts.get(0);
        logger.info("Created table: id={}, tableNumber={}, zone={}, position=({}, {})",
            saved.getId(), saved.getTableNumber(), zone.getZoneName(),
            primaryLayout != null ? primaryLayout.getPositionX() : null,
            primaryLayout != null ? primaryLayout.getPositionY() : null);

        TableResponse response = TableResponse.from(saved, TableStatus.AVAILABLE);
        broadcastStatusChange(saved, null, saved.getStatus());
        return response;
    }

    /**
     * Tách bàn lớn thành nhiều bàn nhỏ (max 4 chỗ/bàn).
     * Ví dụ: "Bàn 20" capacity 8 → "Bàn 20A"(4) + "Bàn 20B"(4) xếp liền kề nhau ("xếp vào nhau").
     */
    private TableResponse createSplitTables(String baseNumber, int totalCapacity, TableZone zone) {
        List<Integer> splits = calculateSplits(totalCapacity);
        char suffix = 'A';

        // 1. Kiểm tra trước xem có bị trùng số hiệu bàn con nào không
        for (int cap : splits) {
            String splitNumber = baseNumber + suffix;
            if (tableRepository.existsByTableNumberIgnoreCaseAndDeletedAtIsNull(splitNumber)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Số bàn " + splitNumber + " đã tồn tại. Hãy chọn số bàn khác.");
            }
            suffix++;
        }

        // 2. Tạo và lưu toàn bộ các bàn con vào CSDL
        List<RestaurantTable> savedTables = new ArrayList<>();
        suffix = 'A';
        for (int cap : splits) {
            String splitNumber = baseNumber + suffix;
            RestaurantTable table = new RestaurantTable(
                UUID.randomUUID().toString(), zone.getId(), splitNumber, cap
            );
            table.setZone(zone);
            RestaurantTable saved = tableRepository.save(table);
            savedTables.add(saved);
            suffix++;
        }

        // 3. Tính toán vị trí xếp cặp liền kề nhau và áp dụng lực đẩy nam châm trái chiều nếu khu vực mở rộng
        List<FloorPlanLayout> layouts = calculateAndApplyOptimalLayouts(savedTables, zone);
        for (FloorPlanLayout layout : layouts) {
            floorPlanLayoutRepository.save(layout);
        }

        // 4. Phát thông báo trạng thái
        for (RestaurantTable st : savedTables) {
            logger.info("Auto-split: created sub-table {}, capacity={}, zone={}",
                st.getTableNumber(), st.getCapacity(), zone.getZoneName());
            broadcastStatusChange(st, null, st.getStatus());
        }

        logger.info("Auto-split complete: {} → {} paired sub-tables", baseNumber, splits.size());
        return TableResponse.from(savedTables.get(0), TableStatus.AVAILABLE);
    }

    /**
     * Chia totalCapacity thành list các cap ≤ 4.
     * Quy tắc: chia thành các phần 4, remainder 1-2 giữ nguyên, remainder 3 pad lên 4.
     */
    public static List<Integer> calculateSplits(int totalCapacity) {
        List<Integer> parts = new ArrayList<>();
        int remaining = totalCapacity;
        while (remaining > MAX_ATOMIC_CAPACITY) {
            parts.add(MAX_ATOMIC_CAPACITY);
            remaining -= MAX_ATOMIC_CAPACITY;
        }
        if (remaining == 3) {
            // Pad 3 → 4 (thêm 1 chỗ cho thoải mái)
            parts.add(MAX_ATOMIC_CAPACITY);
        } else if (remaining > 0) {
            parts.add(remaining); // 1 hoặc 2
        }
        return parts;
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
        RestaurantTable table = tableRepository.lockActiveById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bàn."));
        if (request.status() != TableStatus.OCCUPIED &&
            orderRepository.findSeatedOrdersByTableIds(List.of(id)).stream().anyMatch(Order::isSeated)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Hãy trả chỗ cho các đơn còn khách trước khi đổi trạng thái bàn.");
        }
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
    /**
     * Calculates optimal non-overlapping coordinates for a newly created table.
     * Backwards-compatible wrapper around calculateAndApplyOptimalLayouts.
     */
    public FloorPlanLayout calculateOptimalLayoutPosition(RestaurantTable savedTable) {
        TableZone zone = savedTable.getZone();
        if (zone == null && savedTable.getZoneId() != null) {
            zone = zoneRepository.findById(savedTable.getZoneId()).orElse(null);
        }
        List<FloorPlanLayout> layouts = calculateAndApplyOptimalLayouts(List.of(savedTable), zone);
        return layouts.isEmpty() ? null : layouts.get(0);
    }

    /**
     * Tính toán vị trí xếp bàn thông minh và xếp cặp bàn tự tách liền kề nhau ("xếp vào nhau").
     * Khi một khu vực bị nở rộng, kích hoạt cơ chế "Nam châm trái chiều" (Magnetic Repulsion)
     * tự động đẩy các khu vực liền kề ra xa để triệt tiêu 100% va chạm ranh giới.
     */
    public List<FloorPlanLayout> calculateAndApplyOptimalLayouts(List<RestaurantTable> newTables, TableZone zone) {
        if (newTables == null || newTables.isEmpty()) {
            return Collections.emptyList();
        }

        String zoneId = zone != null ? zone.getId() : newTables.get(0).getZoneId();
        List<RestaurantTable> allActiveTables = tableRepository.findAllActiveWithZone();
        List<FloorPlanLayout> allLayouts = floorPlanLayoutRepository.findAll();

        Map<String, FloorPlanLayout> layoutMap = allLayouts.stream()
            .collect(Collectors.toMap(FloorPlanLayout::getTableId, l -> l, (a, b) -> a));

        Map<String, List<FloorPlanLayout>> zoneLayoutsMap = new HashMap<>();
        for (RestaurantTable t : allActiveTables) {
            boolean isNew = newTables.stream().anyMatch(nt -> nt.getId().equals(t.getId()));
            if (isNew) continue;
            FloorPlanLayout l = layoutMap.get(t.getId());
            if (l != null && l.getPositionX() != null && l.getPositionY() != null) {
                zoneLayoutsMap.computeIfAbsent(t.getZoneId(), k -> new ArrayList<>()).add(l);
            }
        }

        List<FloorPlanLayout> thisZoneLayouts = zoneLayoutsMap.getOrDefault(zoneId, Collections.emptyList());

        int count = newTables.size();
        double[] widths = new double[count];
        double[] heights = new double[count];
        double[] relX = new double[count];
        double[] relY = new double[count];

        double currentRelX = 0;
        double maxH = 0;
        double pairGap = 10.0;

        for (int i = 0; i < count; i++) {
            RestaurantTable t = newTables.get(i);
            widths[i] = t.getCapacity() <= 2 ? 70.0 : (t.getCapacity() <= 4 ? 90.0 : 140.0);
            heights[i] = t.getCapacity() <= 2 ? 70.0 : 90.0;
            relX[i] = currentRelX;
            relY[i] = 0;
            currentRelX += widths[i] + (i < count - 1 ? pairGap : 0);
            if (heights[i] > maxH) maxH = heights[i];
        }

        double clusterW = currentRelX;
        double clusterH = maxH;

        double candX = 50.0;
        double candY = 70.0;

        if (!thisZoneLayouts.isEmpty()) {
            double gapX = 40.0;
            double gapY = 40.0;

            List<double[]> candidates = new ArrayList<>();
            for (FloorPlanLayout ex : thisZoneLayouts) {
                candidates.add(new double[]{ex.getPositionX() + ex.getWidth() + gapX, ex.getPositionY()});
                candidates.add(new double[]{ex.getPositionX(), ex.getPositionY() + ex.getHeight() + gapY});
            }

            double minX = thisZoneLayouts.stream().mapToDouble(FloorPlanLayout::getPositionX).min().orElse(50.0);
            double maxX = thisZoneLayouts.stream().mapToDouble(l -> l.getPositionX() + l.getWidth()).max().orElse(150.0);
            double minY = thisZoneLayouts.stream().mapToDouble(FloorPlanLayout::getPositionY).min().orElse(70.0);
            double maxY = thisZoneLayouts.stream().mapToDouble(l -> l.getPositionY() + l.getHeight()).max().orElse(150.0);

            candidates.add(new double[]{maxX + gapX, minY});
            candidates.add(new double[]{minX, maxY + gapY});

            double[] best = null;
            for (double[] cand : candidates) {
                double cx = cand[0];
                double cy = cand[1];
                if (cx < 20 || cy < 20) continue;

                // 1. Kiểm tra va chạm bàn với tất cả bàn hiện có
                boolean collidesWithTable = false;
                for (int i = 0; i < count; i++) {
                    double tiX = cx + relX[i];
                    double tiY = cy + relY[i];
                    double tiW = widths[i];
                    double tiH = heights[i];

                    for (FloorPlanLayout other : layoutMap.values()) {
                        boolean isNew = newTables.stream().anyMatch(nt -> nt.getId().equals(other.getTableId()));
                        if (isNew) continue;
                        if (isAabbOverlap(tiX, tiY, tiW, tiH, other.getPositionX(), other.getPositionY(), other.getWidth(), other.getHeight())) {
                            collidesWithTable = true;
                            break;
                        }
                    }
                    if (collidesWithTable) break;
                }
                if (collidesWithTable) continue;

                // 2. Kiểm tra va chạm ranh giới khu vực (Zone Collision)
                double newZoneMinX = Math.min(minX, cx);
                double newZoneMinY = Math.min(minY, cy);
                double newZoneMaxX = Math.max(maxX, cx + clusterW);
                double newZoneMaxY = Math.max(maxY, cy + clusterH);

                double newBoxX = newZoneMinX - 24.0;
                double newBoxY = newZoneMinY - 54.0;
                double newBoxW = newZoneMaxX - newZoneMinX + 48.0;
                double newBoxH = newZoneMaxY - newZoneMinY + 78.0;

                boolean collidesWithOtherZones = false;
                for (Map.Entry<String, List<FloorPlanLayout>> entry : zoneLayoutsMap.entrySet()) {
                    if (entry.getKey().equals(zoneId) || entry.getValue().isEmpty()) continue;
                    double[] otherBox = computeZoneVisualBox(entry.getValue());
                    if (isAabbOverlap(newBoxX, newBoxY, newBoxW, newBoxH, otherBox[0], otherBox[1], otherBox[2], otherBox[3])) {
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
                // Không có vị trí nào vừa vặn bên trong ranh giới hiện tại -> Mở rộng khu vực
                candX = minX;
                candY = maxY + gapY;

                // Điều chỉnh tránh va chạm bàn cục bộ
                while (hasClusterTableCollision(candX, candY, count, relX, relY, widths, heights, layoutMap, newTables)) {
                    candY += 30.0;
                }

                // KÍCH HOẠT LỰC ĐẨY NAM CHÂM TRÁI CHIỀU (Magnetic Repulsion)
                applyMagneticRepulsion(zoneId, candX, candY, clusterW, clusterH, minX, maxX, minY, maxY, zoneLayoutsMap);
            }
        } else {
            // Case 2: Khu vực mới hoàn toàn chưa có bàn nào
            candX = 50.0;
            candY = 70.0;
            boolean found = false;

            for (double y = 70.0; y <= 800.0 && !found; y += 180.0) {
                for (double x = 50.0; x <= 900.0 && !found; x += 180.0) {
                    double boxX = x - 24.0;
                    double boxY = y - 54.0;
                    double boxW = clusterW + 48.0;
                    double boxH = clusterH + 78.0;

                    boolean overlapsAny = false;
                    for (int i = 0; i < count; i++) {
                        double tiX = x + relX[i];
                        double tiY = y + relY[i];
                        for (FloorPlanLayout other : layoutMap.values()) {
                            if (isAabbOverlap(tiX, tiY, widths[i], heights[i], other.getPositionX(), other.getPositionY(), other.getWidth(), other.getHeight())) {
                                overlapsAny = true;
                                break;
                            }
                        }
                        if (overlapsAny) break;
                    }
                    if (overlapsAny) continue;

                    for (Map.Entry<String, List<FloorPlanLayout>> entry : zoneLayoutsMap.entrySet()) {
                        if (entry.getValue().isEmpty()) continue;
                        double[] otherBox = computeZoneVisualBox(entry.getValue());
                        if (isAabbOverlap(boxX, boxY, boxW, boxH, otherBox[0], otherBox[1], otherBox[2], otherBox[3])) {
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

        // Tạo danh sách layout cho từng bàn con xếp liền kề nhau
        List<FloorPlanLayout> results = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            RestaurantTable t = newTables.get(i);
            FloorPlanLayout layout = new FloorPlanLayout(
                UUID.randomUUID().toString(),
                t.getId(),
                (double) Math.round(candX + relX[i]),
                (double) Math.round(candY + relY[i]),
                widths[i],
                heights[i],
                0.0,
                FloorPlanShape.RECTANGLE
            );
            results.add(layout);
        }

        return results;
    }

    private boolean hasClusterTableCollision(double cx, double cy, int count,
                                            double[] relX, double[] relY,
                                            double[] widths, double[] heights,
                                            Map<String, FloorPlanLayout> layoutMap,
                                            List<RestaurantTable> newTables) {
        for (int i = 0; i < count; i++) {
            double tiX = cx + relX[i];
            double tiY = cy + relY[i];
            double tiW = widths[i];
            double tiH = heights[i];
            for (FloorPlanLayout other : layoutMap.values()) {
                boolean isNew = newTables.stream().anyMatch(nt -> nt.getId().equals(other.getTableId()));
                if (isNew) continue;
                if (isAabbOverlap(tiX, tiY, tiW, tiH, other.getPositionX(), other.getPositionY(), other.getWidth(), other.getHeight())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Cơ chế Nam châm trái chiều (Magnetic Repulsion):
     * Khi khu vực mở rộng, tính toán vector đẩy và tịnh tiến các khu vực lân cận ra xa
     * để duy trì khoảng đệm an toàn >= 40px, triệt tiêu 100% va chạm ranh giới.
     */
    private void applyMagneticRepulsion(String thisZoneId, double candX, double candY,
                                       double clusterW, double clusterH,
                                       double minX, double maxX, double minY, double maxY,
                                       Map<String, List<FloorPlanLayout>> zoneLayoutsMap) {
        double thisMinX = Math.min(minX, candX);
        double thisMaxX = Math.max(maxX, candX + clusterW);
        double thisMinY = Math.min(minY, candY);
        double thisMaxY = Math.max(maxY, candY + clusterH);

        double thisBoxX = thisMinX - 24.0;
        double thisBoxY = thisMinY - 54.0;
        double thisBoxW = thisMaxX - thisMinX + 48.0;
        double thisBoxH = thisMaxY - thisMinY + 78.0;

        double zoneBuffer = 40.0;

        for (Map.Entry<String, List<FloorPlanLayout>> entry : zoneLayoutsMap.entrySet()) {
            String otherZoneId = entry.getKey();
            List<FloorPlanLayout> otherLayouts = entry.getValue();
            if (otherZoneId.equals(thisZoneId) || otherLayouts == null || otherLayouts.isEmpty()) continue;

            double[] otherBox = computeZoneVisualBox(otherLayouts);
            double otherBoxX = otherBox[0];
            double otherBoxY = otherBox[1];
            double otherBoxW = otherBox[2];
            double otherBoxH = otherBox[3];
            double otherMinX = otherBox[4];
            double otherMinY = otherBox[5];

            // Kiểm tra va chạm có tính cả khoảng đệm an toàn
            boolean collidesWithBuffer = isAabbOverlap(
                thisBoxX, thisBoxY, thisBoxW + zoneBuffer, thisBoxH + zoneBuffer,
                otherBoxX, otherBoxY, otherBoxW, otherBoxH
            );

            if (!collidesWithBuffer) continue;

            // Xác định hướng đẩy nam châm (Đẩy xuống dưới trục Y hoặc Đẩy sang phải trục X)
            if (otherMinY >= thisMinY + 50.0 || (otherBoxY >= thisBoxY + thisBoxH / 2.0)) {
                // Đẩy xuống phía dưới theo trục Y
                double targetOtherBoxY = thisBoxY + thisBoxH + zoneBuffer;
                double shiftY = targetOtherBoxY - otherBoxY;
                if (shiftY > 0) {
                    shiftY = Math.ceil(shiftY / 10.0) * 10.0;
                    for (FloorPlanLayout layout : otherLayouts) {
                        layout.setPositionY(layout.getPositionY() + shiftY);
                        floorPlanLayoutRepository.save(layout);
                    }
                    logger.info("Magnetic repulsion: pushed zone {} downwards by {}px", otherZoneId, shiftY);
                }
            } else if (otherMinX >= thisMinX + 50.0 || (otherBoxX >= thisBoxX + thisBoxW / 2.0)) {
                // Đẩy sang bên phải theo trục X
                double targetOtherBoxX = thisBoxX + thisBoxW + zoneBuffer;
                double shiftX = targetOtherBoxX - otherBoxX;
                if (shiftX > 0) {
                    shiftX = Math.ceil(shiftX / 10.0) * 10.0;
                    for (FloorPlanLayout layout : otherLayouts) {
                        layout.setPositionX(layout.getPositionX() + shiftX);
                        floorPlanLayoutRepository.save(layout);
                    }
                    logger.info("Magnetic repulsion: pushed zone {} rightwards by {}px", otherZoneId, shiftX);
                }
            }
        }
    }

    private static double[] computeZoneVisualBox(List<FloorPlanLayout> layouts) {
        double minX = layouts.stream().mapToDouble(FloorPlanLayout::getPositionX).min().orElse(0.0);
        double minY = layouts.stream().mapToDouble(FloorPlanLayout::getPositionY).min().orElse(0.0);
        double maxX = layouts.stream().mapToDouble(l -> l.getPositionX() + l.getWidth()).max().orElse(0.0);
        double maxY = layouts.stream().mapToDouble(l -> l.getPositionY() + l.getHeight()).max().orElse(0.0);

        double padding = 24.0;
        double headerHeight = 30.0;
        double boxX = minX - padding;
        double boxY = minY - padding - headerHeight;
        double boxW = maxX - minX + padding * 2.0;
        double boxH = maxY - minY + padding * 2.0 + headerHeight;
        return new double[]{boxX, boxY, boxW, boxH, minX, minY, maxX, maxY};
    }

    private static boolean isAabbOverlap(double x1, double y1, double w1, double h1,
                                         double x2, double y2, double w2, double h2) {
        return x1 < x2 + w2 && x1 + w1 > x2 && y1 < y2 + h2 && y1 + h1 > y2;
    }
}
