package com.huongViet.reservation;

import com.huongViet.reservation.dto.CancelReservationRequest;
import com.huongViet.reservation.dto.CreateReservationRequest;
import com.huongViet.reservation.dto.ReservationResponse;
import com.huongViet.reservation.dto.TimeSlotAvailability;
import com.huongViet.reservation.dto.UpdateReservationStatusRequest;
import com.huongViet.reservation.dto.ZoneAvailabilityResponse;
import com.huongViet.table.RestaurantTable;
import com.huongViet.table.TableRepository;
import com.huongViet.table.TableResponse;
import com.huongViet.table.TableStatus;
import com.huongViet.table.TableZone;
import com.huongViet.table.ZoneRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import com.huongViet.table.TableStatusMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ReservationService {

    private static final Logger logger = LoggerFactory.getLogger(ReservationService.class);

    public static final LocalTime OPENING_TIME = LocalTime.of(11, 0);
    public static final LocalTime CLOSING_TIME = LocalTime.of(21, 0);
    public static final int SLOT_INTERVAL_MINUTES = 30;

    public static List<LocalTime> generateContinuousTimeSlots() {
        List<LocalTime> slots = new ArrayList<>();
        LocalTime current = OPENING_TIME;
        while (!current.isAfter(CLOSING_TIME)) {
            slots.add(current);
            current = current.plusMinutes(SLOT_INTERVAL_MINUTES);
        }
        return Collections.unmodifiableList(slots);
    }

    public static final List<LocalTime> TIME_SLOTS = generateContinuousTimeSlots();
    public static final Duration DINING_WINDOW = Duration.ofMinutes(119);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private static final Map<ReservationStatus, Set<ReservationStatus>> ALLOWED_TRANSITIONS = Map.of(
        ReservationStatus.PENDING_HOLD, Set.of(ReservationStatus.CONFIRMED, ReservationStatus.CANCELLED),
        ReservationStatus.PENDING_DEPOSIT, Set.of(ReservationStatus.CONFIRMED, ReservationStatus.CANCELLED),
        ReservationStatus.CONFIRMED, Set.of(ReservationStatus.ARRIVED, ReservationStatus.CANCELLED, ReservationStatus.NO_SHOW),
        ReservationStatus.ARRIVED, Set.of(ReservationStatus.PAID, ReservationStatus.REVIEWED),
        ReservationStatus.PAID, Set.of(ReservationStatus.REVIEWED),
        ReservationStatus.REVIEWED, Set.of(),
        ReservationStatus.CANCELLED, Set.of(),
        ReservationStatus.NO_SHOW, Set.of()
    );

    private final ReservationRepository reservationRepository;
    private final ZoneRepository zoneRepository;
    private final TableRepository tableRepository;
    private final ReservationAuditRepository reservationAuditRepository;
    private final ReservationEmailService emailService;
    private final SimpMessagingTemplate messagingTemplate;

    @Autowired
    public ReservationService(ReservationRepository reservationRepository,
                              ZoneRepository zoneRepository,
                              TableRepository tableRepository,
                              ReservationAuditRepository reservationAuditRepository,
                              ReservationEmailService emailService,
                              SimpMessagingTemplate messagingTemplate) {
        this.reservationRepository = reservationRepository;
        this.zoneRepository = zoneRepository;
        this.tableRepository = tableRepository;
        this.reservationAuditRepository = reservationAuditRepository;
        this.emailService = emailService;
        this.messagingTemplate = messagingTemplate;
    }

    public void validateTransition(ReservationStatus from, ReservationStatus to) {
        if (from == to) return;
        Set<ReservationStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                String.format("Không thể chuyển trạng thái từ %s sang %s", from, to));
        }
    }

    @Transactional
    public ReservationResponse createBooking(CreateReservationRequest request, String customerId) {
        if (request.partySize() != null && request.partySize() > 15) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Đoàn trên 15 khách cần đặt sự kiện. Vui lòng liên hệ quản lý nhà hàng.");
        }

        LocalDate today = LocalDate.now();
        if (request.reservationDate().isBefore(today)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ngày đặt bàn không được ở quá khứ.");
        }

        if (!TIME_SLOTS.contains(request.reservationTime())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Khung giờ đặt bàn không hợp lệ. Vui lòng chọn trong các khung giờ phục vụ của nhà hàng (11:00 - 21:00).");
        }

        if (request.reservationDate().isEqual(today) && request.reservationTime().isBefore(LocalTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Giờ đặt bàn phải sau thời điểm hiện tại.");
        }

        TableZone zone = zoneRepository.findById(request.zoneId())
            .filter(z -> z.getDeletedAt() == null)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Khu vực không tồn tại hoặc đã bị vô hiệu hóa."));

        int zoneCapacity = getZoneTotalCapacity(zone.getId());
        if (zoneCapacity <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Khu vực này hiện chưa có bàn khả dụng.");
        }

        if (!isTimeSlotAvailable(zone.getId(), request.reservationDate(), request.reservationTime(), request.partySize(), zoneCapacity)) {
            List<LocalTime> alternatives = findAdjacentAvailableSlots(zone.getId(), request.reservationDate(), request.partySize(), zoneCapacity);
            if (!alternatives.isEmpty()) {
                String suggestions = alternatives.stream().map(t -> t.format(TIME_FORMATTER)).collect(Collectors.joining(", "));
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Khung giờ đã đầy cho khu vực này. Khung giờ gợi ý: " + suggestions);
            } else {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Khu vực này đã hết chỗ trong ngày đã chọn. Quý khách vui lòng chọn ngày hoặc khu vực khác.");
            }
        }

        String reservationId = UUID.randomUUID().toString();
        Reservation reservation = new Reservation(
            reservationId,
            customerId,
            request.guestName().trim(),
            request.guestPhone().trim(),
            (request.guestEmail() != null && !request.guestEmail().isBlank()) ? request.guestEmail().trim() : null,
            request.partySize(),
            request.reservationDate(),
            request.reservationTime(),
            request.specialRequests(),
            zone.getId()
        );
        reservation.setZone(zone);

        Reservation saved = reservationRepository.save(reservation);
        emailService.sendBookingConfirmation(saved, zone.getZoneName());
        return toResponse(saved, zone.getId(), zone.getZoneName(), zone.getIsVip());
    }

    public List<ZoneAvailabilityResponse> checkAvailability(LocalDate date, Integer partySize) {
        List<TableZone> activeZones = zoneRepository.findAll().stream()
            .filter(z -> z.getDeletedAt() == null)
            .toList();

        List<ZoneAvailabilityResponse> result = new ArrayList<>();
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();

        for (TableZone zone : activeZones) {
            int zoneCapacity = getZoneTotalCapacity(zone.getId());
            List<TimeSlotAvailability> slotAvailabilities = new ArrayList<>();

            for (LocalTime slot : TIME_SLOTS) {
                boolean pastTime = date.isEqual(today) && slot.isBefore(now);
                if (pastTime) {
                    slotAvailabilities.add(new TimeSlotAvailability(slot, slot.format(TIME_FORMATTER), 0, 0, false));
                    continue;
                }

                LocalTime windowStart = slot.minusMinutes(118);
                LocalTime windowEnd = slot.plusMinutes(118);

                int confirmedSeats = reservationRepository.sumConfirmedPartySizeByZoneAndSlot(
                    zone.getId(), date, windowStart, windowEnd
                );

                int remaining = Math.max(0, zoneCapacity - confirmedSeats);
                boolean isAvail = (partySize != null && partySize > 0) ? remaining >= partySize : remaining > 0;

                slotAvailabilities.add(new TimeSlotAvailability(slot, slot.format(TIME_FORMATTER), confirmedSeats, remaining, isAvail));
            }

            result.add(new ZoneAvailabilityResponse(
                zone.getId(),
                zone.getZoneName(),
                zone.getIsVip() != null && zone.getIsVip(),
                zoneCapacity,
                slotAvailabilities
            ));
        }

        return result;
    }

    public List<String> getTimeSlots() {
        return TIME_SLOTS.stream().map(LocalTime::toString).toList();
    }

    public List<ReservationResponse> getCustomerBookings(String customerId) {
        return reservationRepository.findByCustomerIdWithTableAndZone(customerId)
            .stream().map(this::toResponse).toList();
    }

    public List<ReservationResponse> getGuestBookings(String phone, String code) {
        if (phone == null || phone.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số điện thoại không được để trống");
        if (code == null || code.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã đặt bàn không được để trống");
        String cleanCode = code.trim().toUpperCase().replaceFirst("^HV-", "").toLowerCase();
        return reservationRepository.findByPhoneAndBookingCode(phone.trim(), cleanCode)
            .stream().map(this::toResponse).toList();
    }

    public ReservationResponse getById(String id) {
        Reservation reservation = reservationRepository.findActiveByIdWithTableAndZone(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu đặt bàn"));
        return toResponse(reservation);
    }

    public List<ReservationResponse> listByDate(LocalDate date, ReservationStatus status) {
        return reservationRepository.findByDateAndStatusWithTableAndZone(date, status)
            .stream().map(this::toResponse).toList();
    }

    public List<ReservationResponse> listUpcoming(LocalDate startDate, ReservationStatus status) {
        LocalDate queryDate = (startDate != null) ? startDate : LocalDate.now();
        return reservationRepository.findUpcomingWithTableAndZone(queryDate, status)
            .stream().map(this::toResponse).toList();
    }

    public List<ReservationResponse> listAllPending() {
        return reservationRepository.findAllPendingHoldWithTableAndZone()
            .stream().map(this::toResponse).toList();
    }

    public List<TableResponse> getAvailableTablesForReservation(String id) {
        Reservation reservation = reservationRepository.findActiveByIdWithTableAndZone(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu đặt bàn"));

        List<RestaurantTable> allTables = tableRepository.findAllActiveWithZone();
        LocalTime slotStart = reservation.getReservationTime().minusMinutes(118);
        LocalTime slotEnd = reservation.getReservationTime().plusMinutes(118);

        return allTables.stream()
            .filter(t -> t.getStatus() != TableStatus.MAINTENANCE)
            .filter(t -> {
                List<Reservation> overlaps = reservationRepository.findOverlappingReservationsForTable(
                    t.getId(), reservation.getReservationDate(), slotStart, slotEnd
                );
                return overlaps.stream().allMatch(r -> r.getId().equals(reservation.getId()));
            })
            .map(TableResponse::from)
            .toList();
    }

    @Transactional
    public ReservationResponse updateStatus(String id, UpdateReservationStatusRequest request, String changedBy) {
        Reservation reservation = reservationRepository.findActiveByIdWithTableAndZone(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu đặt bàn"));

        ReservationStatus oldStatus = reservation.getStatus();
        ReservationStatus newStatus = request.status();

        validateTransition(oldStatus, newStatus);
        String oldTableId = reservation.getTableId();

        // XÁC NHẬN ĐẶT BÀN & GỘP BÀN
        if (newStatus == ReservationStatus.CONFIRMED) {
            List<String> targetTableIds = new ArrayList<>();
            if (request.tableIds() != null && !request.tableIds().isEmpty()) {
                targetTableIds.addAll(request.tableIds().stream()
                    .filter(tId -> tId != null && !tId.isBlank())
                    .distinct().toList());
            } else if (request.tableId() != null && !request.tableId().isBlank()) {
                targetTableIds.add(request.tableId().trim());
            } else if (oldTableId != null && !oldTableId.isBlank()) {
                targetTableIds.add(oldTableId);
            }

            if (targetTableIds.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng chọn ít nhất một bàn khả dụng trước khi xác nhận đặt bàn.");
            }

            List<RestaurantTable> assignedTables = tableRepository.findAllById(targetTableIds);
            if (assignedTables.size() != targetTableIds.size()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Một hoặc nhiều bàn được chọn không tồn tại hoặc đã bị xóa.");
            }

            String primaryZoneId = assignedTables.get(0).getZoneId();
            boolean sameZone = assignedTables.stream().allMatch(t -> primaryZoneId.equals(t.getZoneId()));
            if (!sameZone) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chỉ có thể gộp các bàn trong cùng một khu vực (zone).");
            }

            int totalCapacity = assignedTables.stream().mapToInt(RestaurantTable::getCapacity).sum();
            if (totalCapacity < reservation.getPartySize()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    String.format("Tổng sức chứa các bàn đã chọn là %d chỗ, không đủ cho đoàn %d khách.", totalCapacity, reservation.getPartySize()));
            }

            LocalTime slotStart = reservation.getReservationTime().minusMinutes(118);
            LocalTime slotEnd = reservation.getReservationTime().plusMinutes(118);

            for (RestaurantTable table : assignedTables) {
                if (table.getStatus() == TableStatus.MAINTENANCE) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                        String.format("Bàn %s đang ở trạng thái bảo trì/tạm ẩn, không thể gán.", table.getTableNumber()));
                }

                List<Reservation> overlapping = reservationRepository.findOverlappingReservationsForTable(
                    table.getId(), reservation.getReservationDate(), slotStart, slotEnd
                );

                if (overlapping.stream().anyMatch(r -> !r.getId().equals(reservation.getId()))) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                        String.format("Bàn %s đã có đơn đặt trong khung giờ này.", table.getTableNumber()));
                }
            }

            RestaurantTable primaryTable = assignedTables.get(0);
            reservation.setTableId(primaryTable.getId());
            reservation.setTable(primaryTable);
            reservation.setZoneId(primaryZoneId);
            reservation.setMergedTableIds(String.join(",", targetTableIds));
        }

        // CHECK-IN KHÁCH NHẬN BÀN
        if (newStatus == ReservationStatus.ARRIVED) {
            LocalDate today = LocalDate.now();
            if (!reservation.getReservationDate().isEqual(today)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chỉ có thể check-in các đơn đặt bàn trong ngày hôm nay.");
            }

            List<String> targetTableIds = new ArrayList<>();
            if (reservation.getMergedTableIds() != null && !reservation.getMergedTableIds().isBlank()) {
                targetTableIds.addAll(Arrays.stream(reservation.getMergedTableIds().split(","))
                    .map(String::trim).filter(s -> !s.isEmpty()).distinct().toList());
            } else if (reservation.getTableId() != null) {
                targetTableIds.add(reservation.getTableId());
            }

            for (String tId : targetTableIds) {
                tableRepository.findByIdAndDeletedAtIsNull(tId).ifPresent(t -> {
                    TableStatus oldTStatus = t.getStatus();
                    t.setStatus(TableStatus.OCCUPIED);
                    tableRepository.save(t);
                    broadcastTableStatus(t, oldTStatus, TableStatus.OCCUPIED);
                });
            }
        }

        // KHI HỦY HOẶC ĐÁNH DẤU NO_SHOW: TỰ ĐỘNG GIẢI PHÓNG BÀN
        if (newStatus == ReservationStatus.CANCELLED || newStatus == ReservationStatus.NO_SHOW) {
            releaseAssignedTables(reservation);
        }

        if (request.reason() != null && !request.reason().isBlank()) {
            reservation.setCancellationReason(request.reason());
        }

        reservation.setStatus(newStatus);
        Reservation saved = reservationRepository.save(reservation);

        ReservationAudit audit = new ReservationAudit(
            UUID.randomUUID().toString(),
            saved.getId(),
            changedBy,
            oldStatus,
            newStatus,
            oldTableId,
            saved.getTableId(),
            request.reason()
        );
        reservationAuditRepository.save(audit);

        ReservationResponse response = toResponse(saved);
        if (oldStatus != ReservationStatus.CONFIRMED && newStatus == ReservationStatus.CONFIRMED) {
            emailService.sendBookingApprovedEmail(saved, response.tableNumber(), response.zoneName());
        } else if (oldStatus != ReservationStatus.CANCELLED && newStatus == ReservationStatus.CANCELLED) {
            String cancelReason = (saved.getCancellationReason() != null && !saved.getCancellationReason().isBlank())
                ? saved.getCancellationReason()
                : "Nhà hàng không thể tiếp nhận hoặc sắp xếp bàn vào thời điểm này";
            emailService.sendBookingCancelledEmail(saved, cancelReason, response.zoneName());
        }

        return response;
    }

    @Transactional
    public ReservationResponse cancelByCustomer(String reservationId, String customerId, CancelReservationRequest request) {
        Reservation reservation = reservationRepository.findActiveByIdWithTableAndZone(reservationId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu đặt bàn"));

        boolean isAuthorized = false;
        String actor = "CUSTOMER";
        if (customerId != null && reservation.getCustomerId() != null && reservation.getCustomerId().equals(customerId)) {
            isAuthorized = true;
            actor = customerId;
        } else if (request != null && request.phone() != null && !request.phone().isBlank()) {
            if (request.phone().trim().equals(reservation.getGuestPhone())) {
                isAuthorized = true;
                actor = "GUEST:" + reservation.getGuestPhone();
            }
        }

        if (!isAuthorized) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền hủy đặt bàn này");
        }

        validateTransition(reservation.getStatus(), ReservationStatus.CANCELLED);

        // Khách hàng chỉ được phép hủy khi chưa đến nhận bàn (PENDING_HOLD, PENDING_DEPOSIT, CONFIRMED)
        if (reservation.getStatus() != ReservationStatus.PENDING_HOLD
            && reservation.getStatus() != ReservationStatus.PENDING_DEPOSIT
            && reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đơn đặt bàn đã nhận bàn hoặc đã kết thúc, không thể hủy.");
        }
        
        // Kiểm tra thời gian hẹn chưa diễn ra / chưa quá giờ hẹn
        LocalDateTime reservationDateTime = LocalDateTime.of(reservation.getReservationDate(), reservation.getReservationTime());
        if (LocalDateTime.now().isAfter(reservationDateTime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thời gian hẹn đặt bàn đã qua, không thể hủy.");
        }

        // Tự động giải phóng bàn đã gán về trạng thái AVAILABLE
        releaseAssignedTables(reservation);

        ReservationStatus oldStatus = reservation.getStatus();
        reservation.setStatus(ReservationStatus.CANCELLED);
        String reason = (request != null && request.reason() != null && !request.reason().isBlank())
            ? request.reason() : "Khách hàng tự hủy trên hệ thống";
        reservation.setCancellationReason(reason);

        Reservation saved = reservationRepository.save(reservation);

        ReservationAudit audit = new ReservationAudit(
            UUID.randomUUID().toString(), saved.getId(), actor, oldStatus, ReservationStatus.CANCELLED,
            reservation.getTableId(), null, reason
        );
        reservationAuditRepository.save(audit);

        ReservationResponse response = toResponse(saved);
        emailService.sendBookingCancelledEmail(saved, reason, response.zoneName());
        return response;
    }

    /**
     * Tự động giải phóng các bàn đã gán trong đơn đặt bàn về trạng thái AVAILABLE.
     */
    private void releaseAssignedTables(Reservation reservation) {
        List<String> targetTableIds = new ArrayList<>();
        if (reservation.getMergedTableIds() != null && !reservation.getMergedTableIds().isBlank()) {
            targetTableIds.addAll(Arrays.stream(reservation.getMergedTableIds().split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).distinct().toList());
        } else if (reservation.getTableId() != null && !reservation.getTableId().isBlank()) {
            targetTableIds.add(reservation.getTableId().trim());
        }

        for (String tableId : targetTableIds) {
            tableRepository.findByIdAndDeletedAtIsNull(tableId).ifPresent(t -> {
                if (t.getStatus() == TableStatus.RESERVED) {
                    TableStatus oldStatus = t.getStatus();
                    t.setStatus(TableStatus.AVAILABLE);
                    tableRepository.save(t);
                    broadcastTableStatus(t, oldStatus, TableStatus.AVAILABLE);
                    logger.info("Table {} automatically released RESERVED -> AVAILABLE due to cancellation of reservation {}",
                        t.getTableNumber(), reservation.getId());
                }
            });
        }
    }

    public int getZoneTotalCapacity(String zoneId) {
        return tableRepository.findAllByZoneIdAndDeletedAtIsNull(zoneId)
            .stream().mapToInt(RestaurantTable::getCapacity).sum();
    }

    private boolean isTimeSlotAvailable(String zoneId, LocalDate date, LocalTime slot, int partySize, int totalCapacity) {
        LocalTime windowStart = slot.minusMinutes(118);
        LocalTime windowEnd = slot.plusMinutes(118);
        int confirmed = reservationRepository.sumConfirmedPartySizeByZoneAndSlot(zoneId, date, windowStart, windowEnd);
        return (totalCapacity - confirmed) >= partySize;
    }

    private List<LocalTime> findAdjacentAvailableSlots(String zoneId, LocalDate date, int partySize, int totalCapacity) {
        List<LocalTime> alternatives = new ArrayList<>();
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();

        for (LocalTime slot : TIME_SLOTS) {
            if (date.isEqual(today) && slot.isBefore(now)) continue;
            if (isTimeSlotAvailable(zoneId, date, slot, partySize, totalCapacity)) {
                alternatives.add(slot);
            }
        }
        return alternatives;
    }

    private void broadcastTableStatus(RestaurantTable table, TableStatus oldStatus, TableStatus newStatus) {
        if (messagingTemplate == null) return;
        try {
            String zoneName = table.getZone() != null ? table.getZone().getZoneName() : null;
            messagingTemplate.convertAndSend("/topic/table-status",
                new TableStatusMessage(table.getId(), table.getTableNumber(), oldStatus, newStatus, zoneName));
        } catch (Exception ex) {
            logger.warn("Không thể phát sự kiện trạng thái bàn {}: {}", table.getTableNumber(), ex.getMessage());
        }
    }

    public ReservationResponse toResponse(Reservation reservation) {
        return toResponse(reservation, null, null, null);
    }

    public ReservationResponse toResponse(Reservation reservation, String fallbackZoneId, String fallbackZoneName, Boolean fallbackZoneIsVip) {
        String customTableNumber = null;
        if (reservation.getMergedTableIds() != null && !reservation.getMergedTableIds().isBlank()) {
            List<String> ids = Arrays.stream(reservation.getMergedTableIds().split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();

            if (ids.size() > 1) {
                List<RestaurantTable> tables = tableRepository.findAllById(ids);
                customTableNumber = tables.stream()
                    .map(RestaurantTable::getTableNumber)
                    .collect(Collectors.joining(" + "));
            }
        }

        return ReservationResponse.from(reservation, fallbackZoneId, fallbackZoneName, fallbackZoneIsVip, customTableNumber);
    }
}