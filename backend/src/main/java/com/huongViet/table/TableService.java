package com.huongViet.table;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class TableService {

    private static final Logger logger = LoggerFactory.getLogger(TableService.class);

    private final TableRepository tableRepository;
    private final ZoneRepository zoneRepository;
    private final FloorPlanLayoutRepository floorPlanLayoutRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public TableService(TableRepository tableRepository,
                        ZoneRepository zoneRepository,
                        FloorPlanLayoutRepository floorPlanLayoutRepository,
                        SimpMessagingTemplate messagingTemplate) {
        this.tableRepository = tableRepository;
        this.zoneRepository = zoneRepository;
        this.floorPlanLayoutRepository = floorPlanLayoutRepository;
        this.messagingTemplate = messagingTemplate;
    }

    public List<TableResponse> listAll() {
        return tableRepository.findAllActiveWithZone()
            .stream()
            .map(TableResponse::from)
            .toList();
    }

    public TableResponse getById(String id) {
        return TableResponse.from(findActiveWithZoneOrThrow(id));
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

        // Create default floor plan layout coordinates for new table
        FloorPlanLayout defaultLayout = new FloorPlanLayout(
            UUID.randomUUID().toString(),
            saved.getId(),
            50.0,
            50.0,
            80.0,
            80.0,
            0.0,
            FloorPlanShape.RECTANGLE
        );
        floorPlanLayoutRepository.save(defaultLayout);

        logger.info("Created table: id={}, tableNumber={}, zone={}", saved.getId(), saved.getTableNumber(), zone.getZoneName());

        TableResponse response = TableResponse.from(saved);
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

        logger.info("Updated table: id={}, tableNumber={}, capacity={}", table.getId(), table.getTableNumber(), table.getCapacity());
        return TableResponse.from(table);
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

        logger.info("Table status updated: id={}, tableNumber={}, old={}, new={}",
            table.getId(), table.getTableNumber(), oldStatus, request.status());

        TableResponse response = TableResponse.from(table);
        broadcastStatusChange(table, oldStatus, request.status());
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
        if (table.getStatus() == TableStatus.RESERVED || table.getStatus() == TableStatus.OCCUPIED) {
            logger.warn("BR-56 violation blocked: attempt to {} table {} in status {}",
                action, table.getTableNumber(), table.getStatus());
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Không thể " + action + " bàn đang được sử dụng hoặc đã đặt trước (" + table.getStatus() + ")."
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
}
