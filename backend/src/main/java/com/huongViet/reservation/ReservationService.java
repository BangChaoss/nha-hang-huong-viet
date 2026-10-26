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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ReservationService {

    private static final Logger logger = LoggerFactory.getLogger(ReservationService.class);

    public static final List<LocalTime> TIME_SLOTS = List.of(
        LocalTime.of(11, 0), LocalTime.of(11, 30),
        LocalTime.of(12, 0), LocalTime.of(12, 30),
        LocalTime.of(13, 0),
        LocalTime.of(17, 30), LocalTime.of(18, 0),
        LocalTime.of(18, 30), LocalTime.of(19, 0),
        LocalTime.of(19, 30), LocalTime.of(20, 0),
        LocalTime.of(20, 30)
    );

    private static final Duration DINING_WINDOW = Duration.ofHours(2);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private static final Map<ReservationStatus, Set<ReservationStatus>> ALLOWED_TRANSITIONS = Map.of(
        ReservationStatus.PENDING_HOLD, Set.of(ReservationStatus.CONFIRMED, ReservationStatus.CANCELLED),
        ReservationStatus.PENDING_DEPOSIT, Set.of(ReservationStatus.CONFIRMED, ReservationStatus.CANCELLED),
        ReservationStatus.CONFIRMED, Set.of(ReservationStatus.ARRIVED, ReservationStatus.CANCELLED, ReservationStatus.NO_SHOW),
        ReservationStatus.ARRIVED, Set.of(),
        ReservationStatus.CANCELLED, Set.of(),
        ReservationStatus.NO_SHOW, Set.of()
    );

    private final ReservationRepository reservationRepository;
    private final ZoneRepository zoneRepository;
    private final TableRepository tableRepository;
    private final ReservationAuditRepository reservationAuditRepository;
    private final ReservationEmailService emailService;

    public ReservationService(ReservationRepository reservationRepository,
                              ZoneRepository zoneRepository,
                              TableRepository tableRepository,
                              ReservationAuditRepository reservationAuditRepository,
                              ReservationEmailService emailService) {
        this.reservationRepository = reservationRepository;
        this.zoneRepository = zoneRepository;
        this.tableRepository = tableRepository;
        this.reservationAuditRepository = reservationAuditRepository;
        this.emailService = emailService;
    }

    /**
     * State Machine Transition Validation (Source of Truth).
     */
    public void validateTransition(ReservationStatus from, ReservationStatus to) {
        if (from == to) {
            return;
        }
        Set<ReservationStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                String.format("Không thể chuyển trạng thái từ %s sang %s", from, to));
        }
    }

    /**
     * UC08: Create a new table reservation.
     * Enforces:
     * - BR-17: Party size > 15 requires event booking protocol
     * - Time slot validation (within fixed list)
     * - Date in the future / today valid time
     * - Zone capacity check immediately deducting PENDING and CONFIRMED reservations across 2h window
     */
    @Transactional
    public ReservationResponse createBooking(CreateReservationRequest request, String customerId) {
        // BR-17: Event Booking Protocol
        if (request.partySize() != null && request.partySize() > 15) {
            logger.warn("Party size {} exceeded threshold for online booking", request.partySize());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Đoàn trên 15 khách cần đặt sự kiện. Vui lòng liên hệ quản lý nhà hàng.");
        }

        // Validate date
        LocalDate today = LocalDate.now();
        if (request.reservationDate().isBefore(today)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Ngày đặt bàn không được ở quá khứ.");
        }

        // Validate slot against predefined slots
        if (!TIME_SLOTS.contains(request.reservationTime())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Khung giờ đặt bàn không hợp lệ. Vui lòng chọn trong các khung giờ phục vụ của nhà hàng.");
        }

        // If booking today, slot must be in the future
        if (request.reservationDate().isEqual(today) && request.reservationTime().isBefore(LocalTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Giờ đặt bàn phải sau thời điểm hiện tại.");
        }

        // Validate zone existence
        TableZone zone = zoneRepository.findById(request.zoneId())
            .filter(z -> z.getDeletedAt() == null)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Khu vực không tồn tại hoặc đã bị vô hiệu hóa."));

        // Capacity check deducting both PENDING and CONFIRMED
        int zoneCapacity = getZoneTotalCapacity(zone.getId());
        if (zoneCapacity <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Khu vực này hiện chưa có bàn khả dụng.");
        }

        if (!isTimeSlotAvailable(zone.getId(), request.reservationDate(), request.reservationTime(), request.partySize(), zoneCapacity)) {
            List<LocalTime> alternatives = findAdjacentAvailableSlots(zone.getId(), request.reservationDate(), request.partySize(), zoneCapacity);
            if (!alternatives.isEmpty()) {
                String suggestions = alternatives.stream()
                    .map(t -> t.format(TIME_FORMATTER))
                    .collect(Collectors.joining(", "));
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Khung giờ đã đầy cho khu vực này. Khung giờ gợi ý: " + suggestions);
            } else {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Khu vực này đã hết chỗ trong ngày đã chọn. Quý khách vui lòng chọn ngày hoặc khu vực khác.");
            }
        }

        // Create reservation with PENDING_HOLD and zoneId persisted
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
        logger.info("Created reservation {} for guest {} on {} at {} (partySize: {})",
            saved.getId(), saved.getGuestName(), saved.getReservationDate(), saved.getReservationTime(), saved.getPartySize());

        // Send email notification (non-blocking, won't break transaction if mail server fails)
        emailService.sendBookingConfirmation(saved, zone.getZoneName());

        return ReservationResponse.from(saved, zone.getId(), zone.getZoneName(), zone.getIsVip());
    }

    /**
     * Check availability across all active zones for a given date and party size.
     * Deducts capacity in real-time for both PENDING and CONFIRMED reservations across 2h window.
     */
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
                    slotAvailabilities.add(new TimeSlotAvailability(
                        slot,
                        slot.format(TIME_FORMATTER),
                        0,
                        0,
                        false
                    ));
                    continue;
                }

                LocalTime windowStart = slot.minus(DINING_WINDOW).plusMinutes(1);
                LocalTime windowEnd = slot.plus(DINING_WINDOW).minusMinutes(1);

                int confirmedSeats = reservationRepository.sumConfirmedPartySizeByZoneAndSlot(
                    zone.getId(), date, windowStart, windowEnd
                );

                int remaining = Math.max(0, zoneCapacity - confirmedSeats);
                boolean isAvail = (partySize != null && partySize > 0)
                    ? remaining >= partySize
                    : remaining > 0;

                slotAvailabilities.add(new TimeSlotAvailability(
                    slot,
                    slot.format(TIME_FORMATTER),
                    confirmedSeats,
                    remaining,
                    isAvail
                ));
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

    /**
     * Predefined available time slots.
     */
    public List<String> getTimeSlots() {
        return TIME_SLOTS.stream()
            .map(LocalTime::toString)
            .toList();
    }

    /**
     * Customer booking history (UC08 / UC11).
     */
    public List<ReservationResponse> getCustomerBookings(String customerId) {
        return reservationRepository.findByCustomerIdWithTableAndZone(customerId)
            .stream()
            .map(ReservationResponse::from)
            .toList();
    }

    /**
     * Guest lookup requiring BOTH phone and booking code (AND condition).
     */
    public List<ReservationResponse> getGuestBookings(String phone, String code) {
        if (phone == null || phone.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số điện thoại không được để trống");
        }
        if (code == null || code.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã đặt bàn không được để trống");
        }
        String cleanCode = code.trim().toUpperCase().replaceFirst("^HV-", "").toLowerCase();
        return reservationRepository.findByPhoneAndBookingCode(phone.trim(), cleanCode)
            .stream()
            .map(ReservationResponse::from)
            .toList();
    }

    /**
     * Backward-compatible guest lookup by phone number only.
     */
    public List<ReservationResponse> getGuestBookings(String phone) {
        if (phone == null || phone.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số điện thoại không được để trống");
        }
        return reservationRepository.findByGuestPhoneWithTableAndZone(phone.trim())
            .stream()
            .map(ReservationResponse::from)
            .toList();
    }

    /**
     * Get reservation detail by ID.
     */
    public ReservationResponse getById(String id) {
        Reservation reservation = reservationRepository.findActiveByIdWithTableAndZone(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu đặt bàn"));
        return ReservationResponse.from(reservation);
    }

    /**
     * UC09: List reservations by date with optional status filter.
     */
    public List<ReservationResponse> listByDate(LocalDate date, ReservationStatus status) {
        return reservationRepository.findByDateAndStatusWithTableAndZone(date, status)
            .stream()
            .map(ReservationResponse::from)
            .toList();
    }

    /**
     * UC09 backward compatible overload: List reservations by date.
     */
    public List<ReservationResponse> listByDate(LocalDate date) {
        return listByDate(date, null);
    }

    /**
     * UC10: Get available tables suited for assigning to a given reservation.
     */
    public List<TableResponse> getAvailableTablesForReservation(String id) {
        Reservation reservation = reservationRepository.findActiveByIdWithTableAndZone(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu đặt bàn"));

        int partySize = reservation.getPartySize();
        List<RestaurantTable> tables;
        String targetZoneId = reservation.getZoneId();
        if (targetZoneId == null && reservation.getTable() != null) {
            targetZoneId = reservation.getTable().getZoneId();
        }

        if (targetZoneId != null) {
            tables = tableRepository.findAvailableByZoneAndCapacity(targetZoneId, partySize);
        } else {
            tables = tableRepository.findAvailableByCapacity(partySize);
        }

        return tables.stream()
            .map(TableResponse::from)
            .toList();
    }

    /**
     * UC10, UC13, UC10-A1: Update reservation status, assign table, or check in.
     */
    @Transactional
    public ReservationResponse updateStatus(String id, UpdateReservationStatusRequest request, String changedBy) {
        Reservation reservation = reservationRepository.findActiveByIdWithTableAndZone(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu đặt bàn"));

        ReservationStatus oldStatus = reservation.getStatus();
        ReservationStatus newStatus = request.status();

        validateTransition(oldStatus, newStatus);

        String oldTableId = reservation.getTableId();

        // Phase 3.3 / UC10: Transition to CONFIRMED
        if (newStatus == ReservationStatus.CONFIRMED) {
            String targetTableId = (request.tableId() != null && !request.tableId().isBlank())
                ? request.tableId()
                : oldTableId;

            if (targetTableId == null || targetTableId.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Vui lòng chọn bàn trước khi xác nhận đặt bàn.");
            }

            RestaurantTable table = tableRepository.findByIdAndDeletedAtIsNull(targetTableId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bàn không tồn tại hoặc đã bị vô hiệu hóa"));

            if (table.getCapacity() < reservation.getPartySize()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    String.format("Bàn %s chỉ chứa %d khách, không đủ cho %d khách.",
                        table.getTableNumber(), table.getCapacity(), reservation.getPartySize()));
            }

            // Verify table availability if newly assigned or newly confirmed
            if (!targetTableId.equals(oldTableId) || oldStatus != ReservationStatus.CONFIRMED) {
                if (table.getStatus() != TableStatus.AVAILABLE) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                        String.format("Bàn %s đang ở trạng thái %s, không thể gán.",
                            table.getTableNumber(), table.getStatus()));
                }
            }

            // BR-19: Mark table as RESERVED
            table.setStatus(TableStatus.RESERVED);
            tableRepository.save(table);

            // If old table was different and RESERVED, release it back to AVAILABLE
            if (oldTableId != null && !oldTableId.equals(targetTableId)) {
                tableRepository.findByIdAndDeletedAtIsNull(oldTableId)
                    .ifPresent(prevTable -> {
                        if (prevTable.getStatus() == TableStatus.RESERVED) {
                            prevTable.setStatus(TableStatus.AVAILABLE);
                            tableRepository.save(prevTable);
                        }
                    });
            }

            reservation.setTableId(table.getId());
            reservation.setTable(table);
        }

        // Phase 3.6 / UC13: Transition to ARRIVED (Staff Check-in)
        if (newStatus == ReservationStatus.ARRIVED) {
            if (reservation.getTableId() != null) {
                RestaurantTable table = tableRepository.findByIdAndDeletedAtIsNull(reservation.getTableId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bàn gán không tồn tại"));

                // BR-24 / D6: RESERVED -> OCCUPIED
                if (table.getStatus() != TableStatus.RESERVED && table.getStatus() != TableStatus.AVAILABLE) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                        String.format("Bàn %s đang ở trạng thái %s, không thể check-in.",
                            table.getTableNumber(), table.getStatus()));
                }
                table.setStatus(TableStatus.OCCUPIED);
                tableRepository.save(table);
            }
        }

        // Phase 3.8: Transition to CANCELLED (Staff Cancel)
        if (newStatus == ReservationStatus.CANCELLED) {
            if (reservation.getTableId() != null) {
                tableRepository.findByIdAndDeletedAtIsNull(reservation.getTableId())
                    .ifPresent(table -> {
                        if (table.getStatus() == TableStatus.RESERVED) {
                            table.setStatus(TableStatus.AVAILABLE);
                            tableRepository.save(table);
                        }
                    });
            }
        }

        if (request.reason() != null && !request.reason().isBlank()) {
            reservation.setCancellationReason(request.reason());
        }

        reservation.setStatus(newStatus);
        Reservation saved = reservationRepository.save(reservation);

        // Record audit trail
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

        logger.info("Updated reservation {} status from {} to {} by {}", saved.getId(), oldStatus, newStatus, changedBy);

        return ReservationResponse.from(saved);
    }

    /**
     * Phase 3.5 / UC12: Self-Cancel Booking with 60-min rule.
     * Accessible to authenticated members or guests who provide matching phone number.
     */
    @Transactional
    public ReservationResponse cancelByCustomer(String reservationId, String customerId, CancelReservationRequest request) {
        Reservation reservation = reservationRepository.findActiveByIdWithTableAndZone(reservationId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu đặt bàn"));

        // Verify ownership: customerId matches OR guestPhone matches
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

        // Only PENDING_HOLD, PENDING_DEPOSIT and CONFIRMED can be cancelled
        validateTransition(reservation.getStatus(), ReservationStatus.CANCELLED);

        // Requirement 6: 60-min rule for ALL self-cancellations
        LocalDateTime reservationDateTime = LocalDateTime.of(
            reservation.getReservationDate(), reservation.getReservationTime());
        if (Duration.between(LocalDateTime.now(), reservationDateTime).toMinutes() < 60) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Chỉ được hủy đặt bàn trước giờ đến tối thiểu 1 tiếng. Vui lòng liên hệ hotline nhà hàng để được hỗ trợ.");
        }

        ReservationStatus oldStatus = reservation.getStatus();
        reservation.setStatus(ReservationStatus.CANCELLED);
        String reason = (request != null && request.reason() != null && !request.reason().isBlank())
            ? request.reason()
            : "Khách hàng tự hủy trên hệ thống";
        reservation.setCancellationReason(reason);

        // Release table if assigned
        if (reservation.getTableId() != null) {
            tableRepository.findByIdAndDeletedAtIsNull(reservation.getTableId())
                .ifPresent(table -> {
                    if (table.getStatus() == TableStatus.RESERVED) {
                        table.setStatus(TableStatus.AVAILABLE);
                        tableRepository.save(table);
                    }
                });
        }

        Reservation saved = reservationRepository.save(reservation);

        // Audit trail
        ReservationAudit audit = new ReservationAudit(
            UUID.randomUUID().toString(),
            saved.getId(),
            actor,
            oldStatus,
            ReservationStatus.CANCELLED,
            reservation.getTableId(),
            null,
            reason
        );
        reservationAuditRepository.save(audit);

        logger.info("Reservation {} cancelled by {}", saved.getId(), actor);

        return ReservationResponse.from(saved);
    }

    /**
     * Calculate zone capacity dynamically from active tables.
     */
    public int getZoneTotalCapacity(String zoneId) {
        return tableRepository.findAllByZoneIdAndDeletedAtIsNull(zoneId)
            .stream()
            .mapToInt(RestaurantTable::getCapacity)
            .sum();
    }

    private boolean isTimeSlotAvailable(String zoneId, LocalDate date, LocalTime slot, int partySize, int totalCapacity) {
        LocalTime windowStart = slot.minus(DINING_WINDOW).plusMinutes(1);
        LocalTime windowEnd = slot.plus(DINING_WINDOW).minusMinutes(1);
        int confirmed = reservationRepository.sumConfirmedPartySizeByZoneAndSlot(zoneId, date, windowStart, windowEnd);
        return (totalCapacity - confirmed) >= partySize;
    }

    private List<LocalTime> findAdjacentAvailableSlots(String zoneId, LocalDate date, int partySize, int totalCapacity) {
        List<LocalTime> alternatives = new ArrayList<>();
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();

        for (LocalTime slot : TIME_SLOTS) {
            if (date.isEqual(today) && slot.isBefore(now)) {
                continue;
            }
            if (isTimeSlotAvailable(zoneId, date, slot, partySize, totalCapacity)) {
                alternatives.add(slot);
            }
        }
        return alternatives;
    }
}
