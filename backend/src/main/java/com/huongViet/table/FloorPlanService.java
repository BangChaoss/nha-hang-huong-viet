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
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class FloorPlanService {

    private static final Logger logger = LoggerFactory.getLogger(FloorPlanService.class);

    private final FloorPlanLayoutRepository layoutRepository;
    private final TableRepository tableRepository;
    private final TableService tableService;
    private final ReservationRepository reservationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public FloorPlanService(FloorPlanLayoutRepository layoutRepository,
                            TableRepository tableRepository,
                            TableService tableService,
                            ReservationRepository reservationRepository,
                            SimpMessagingTemplate messagingTemplate) {
        this.layoutRepository = layoutRepository;
        this.tableRepository = tableRepository;
        this.tableService = tableService;
        this.reservationRepository = reservationRepository;
        this.messagingTemplate = messagingTemplate;
    }

    public List<FloorPlanLayoutResponse> getAll() {
        List<FloorPlanLayout> layouts = layoutRepository.findAll();
        List<FloorPlanLayoutResponse> responses = new ArrayList<>();

        LocalDate today = LocalDate.now();
        LocalTime nowTime = LocalTime.now();
        List<Reservation> todayReservations = reservationRepository.findActiveReservationsForDate(today);

        for (FloorPlanLayout layout : layouts) {
            tableRepository.findByIdActiveWithZone(layout.getTableId())
                .ifPresent(table -> {
                    TableStatus dynamicStatus = tableService.calculateDynamicStatus(table, todayReservations, nowTime);
                    int occupiedSeats = calculateOccupiedSeats(table, todayReservations, nowTime, dynamicStatus);
                    responses.add(FloorPlanLayoutResponse.from(layout, table, dynamicStatus, occupiedSeats));
                });
        }

        return responses;
    }

    @Transactional
    public List<FloorPlanLayoutResponse> saveAll(List<FloorPlanLayoutRequest> requests) {
        List<FloorPlanLayoutResponse> responses = new ArrayList<>();

        LocalDate today = LocalDate.now();
        LocalTime nowTime = LocalTime.now();
        List<Reservation> todayReservations = reservationRepository.findActiveReservationsForDate(today);

        for (FloorPlanLayoutRequest request : requests) {
            RestaurantTable table = tableRepository.findByIdActiveWithZone(request.tableId())
                .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Bàn với ID " + request.tableId() + " không tồn tại hoặc đã bị xóa."
                ));

            FloorPlanLayout layout = layoutRepository.findByTableId(request.tableId())
                .orElseGet(() -> new FloorPlanLayout(
                    UUID.randomUUID().toString(),
                    request.tableId(),
                    request.positionX(),
                    request.positionY(),
                    request.width(),
                    request.height(),
                    request.rotation(),
                    request.shape()
                ));

            layout.setPositionX(request.positionX());
            layout.setPositionY(request.positionY());
            layout.setWidth(request.width());
            layout.setHeight(request.height());
            layout.setRotation(request.rotation());
            layout.setShape(request.shape());

            FloorPlanLayout saved = layoutRepository.save(layout);
            TableStatus dynamicStatus = tableService.calculateDynamicStatus(table, todayReservations, nowTime);
            int occupiedSeats = calculateOccupiedSeats(table, todayReservations, nowTime, dynamicStatus);
            responses.add(FloorPlanLayoutResponse.from(saved, table, dynamicStatus, occupiedSeats));
        }

        logger.info("Saved batch floor plan layout positions: count={}", responses.size());
        broadcastFloorPlanChange(responses);
        return responses;
    }

    public int calculateOccupiedSeats(RestaurantTable table, List<Reservation> todayReservations, LocalTime nowTime, TableStatus dynamicStatus) {
        if (dynamicStatus == TableStatus.MAINTENANCE || dynamicStatus == TableStatus.CLEANING) {
            return table.getCapacity();
        }
        int booked = 0;
        if (todayReservations != null && !todayReservations.isEmpty()) {
            LocalDateTime nowDateTime = LocalDateTime.of(LocalDate.now(), nowTime);
            // 1. Ưu tiên các đơn trong khung giờ giữ bàn / ăn uống hiện tại
            for (Reservation r : todayReservations) {
                if (!r.includesTable(table.getId())) continue;

                LocalDate resDate = r.getReservationDate() != null ? r.getReservationDate() : LocalDate.now();
                LocalDateTime bookingDateTime = LocalDateTime.of(resDate, r.getReservationTime());
                LocalDateTime windowEnd = bookingDateTime.plus(ReservationService.DINING_WINDOW);

                if (r.getStatus() == ReservationStatus.ARRIVED) {
                    if (!nowDateTime.isBefore(bookingDateTime.minusMinutes(30)) && !nowDateTime.isAfter(windowEnd)) {
                        booked += calculatePartyForTable(table, r);
                    }
                } else if (r.getStatus() == ReservationStatus.CONFIRMED) {
                    if (!nowDateTime.isBefore(bookingDateTime.minusMinutes(ReservationService.RESERVE_BEFORE_MINUTES))
                        && !nowDateTime.isAfter(windowEnd)) {
                        booked += calculatePartyForTable(table, r);
                    }
                }
            }

            // 2. Nếu ngoài khung giờ trực tiếp nhưng bàn đang ở trạng thái RESERVED hoặc CONFIRMED trong ngày:
            // Hiển thị số ghế đặt tương ứng theo đơn đặt bàn trong ngày của bàn đó
            if (booked == 0 && (table.getStatus() == TableStatus.RESERVED || dynamicStatus == TableStatus.RESERVED)) {
                for (Reservation r : todayReservations) {
                    boolean matches = (table.getId().equals(r.getTableId()))
                        || (r.getMergedTableIds() != null && r.getMergedTableIds().contains(table.getId()));
                    if (!matches) continue;

                    if (r.getStatus() == ReservationStatus.CONFIRMED || r.getStatus() == ReservationStatus.ARRIVED) {
                        booked += calculatePartyForTable(table, r);
                        break;
                    }
                }
            }
        }
        if (booked > 0) {
            return Math.min(booked, table.getCapacity());
        }
        if (dynamicStatus == TableStatus.OCCUPIED) {
            return table.getCapacity();
        }
        if (dynamicStatus == TableStatus.RESERVED || table.getStatus() == TableStatus.RESERVED) {
            return table.getCapacity();
        }
        return 0;
    }

    public int calculatePartyForTable(RestaurantTable table, Reservation r) {
        if (r.getMergedTableIds() == null || r.getMergedTableIds().isBlank()) {
            return Math.min(r.getPartySize(), table.getCapacity());
        }
        String[] mergedIds = r.getMergedTableIds().split(",");
        int remainingParty = r.getPartySize();
        for (String idStr : mergedIds) {
            String cleanId = idStr.trim();
            if (cleanId.isEmpty()) continue;
            if (cleanId.equals(table.getId())) {
                return Math.min(remainingParty, table.getCapacity());
            } else {
                int otherCap = tableRepository.findById(cleanId)
                    .map(RestaurantTable::getCapacity)
                    .orElse(4);
                remainingParty = Math.max(0, remainingParty - otherCap);
            }
        }
        return Math.min(r.getPartySize(), table.getCapacity());
    }

    private void broadcastFloorPlanChange(List<FloorPlanLayoutResponse> layouts) {
        try {
            messagingTemplate.convertAndSend("/topic/floor-plan", layouts);
            logger.debug("Broadcasted floor plan layout update");
        } catch (Exception ex) {
            logger.error("Failed to broadcast floor plan update via WebSocket: ", ex);
        }
    }
}
