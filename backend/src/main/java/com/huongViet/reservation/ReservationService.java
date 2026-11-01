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

    /**
     * Continuous time slots from 11:00 to 21:00 without broken intervals between shifts.
     */
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

    /**
     * Strict 1 hour 59 minute reservation lock duration.
     */
    public static final Duration DINING_WINDOW = Duration.ofMinutes(119);
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
     * - Continuous slot validation (from 11:00 to 21:00)
     * - Date in the future / today valid time
     * - Zone capacity check deducting active reservations across 1h59m window
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

        // Validate slot against continuous predefined slots
        if (!TIME_SLOTS.contains(request.reservationTime())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Khung giờ đặt bàn không hợp lệ. Vui lòng chọn trong các khung giờ phục vụ của nhà hàng (11:00 - 21:00).");
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

        // Send email notification (non-blocking)
        emailService.sendBookingConfirmation(saved, zone.getZoneName());

        return toResponse(saved, zone.getId(), zone.getZoneName(), zone.getIsVip());
    }

    /**
     * Check availability across all active zones for a given date and party size.
     * Evaluates continuous time slots and deductucts capacity with 1h59m window.
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

                // 1h59m turn-time: slot - 118m to slot + 118m
                LocalTime windowStart = slot.minusMinutes(118);
                LocalTime windowEnd = slot.plusMinutes(118);

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
     * Predefined continuous available time slots.
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
            .map(this::toResponse)
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
            .map(this::toResponse)
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
            .map(this::toResponse)
            .toList();
    }

    /**
     * Get reservation detail by ID.
     */
    public ReservationResponse getById(String id) {
        Reservation reservation = reservationRepository.findActiveByIdWithTableAndZone(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu đặt bàn"));
        return toResponse(reservation);
    }

    /**
     * UC09: List reservations by date with optional status filter.
     */
    public List<ReservationResponse> listByDate(LocalDate date, ReservationStatus status) {
        return reservationRepository.findByDateAndStatusWithTableAndZone(date, status)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    public List<ReservationResponse> listByDate(LocalDate date) {
        return listByDate(date, null);
    }

    /**
     * UC10: Get available tables suited for assigning to a given reservation.
     * Evaluates time-scoped table availability without permanently locking the table.
     */
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

    /**
     * UC10, UC13, UC10-A1: Update reservation status, assign / merge tables, or check in.
     * Supports multi-table merging (Gộp bàn) within the same zone.
     * Restricts staff check-in to [-30m, +30m] window near reservation time.
     */
    @Transactional
    public ReservationResponse updateStatus(String id, UpdateReservationStatusRequest request, String changedBy) {
        Reservation reservation = reservationRepository.findActiveByIdWithTableAndZone(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu đặt bàn"));

        ReservationStatus oldStatus = reservation.getStatus();
        ReservationStatus newStatus = request.status();

        validateTransition(oldStatus, newStatus);

        String oldTableId = reservation.getTableId();

        // Phase 3.3 / UC10: Transition to CONFIRMED (Table Assignment & Merging)
        if (newStatus == ReservationStatus.CONFIRMED) {
            List<String> targetTableIds = new ArrayList<>();
            if (request.tableIds() != null && !request.tableIds().isEmpty()) {
                targetTableIds.addAll(request.tableIds().stream()
                    .filter(tId -> tId != null && !tId.isBlank())
                    .distinct()
                    .toList());
            } else if (request.tableId() != null && !request.tableId().isBlank()) {
                targetTableIds.add(request.tableId().trim());
            } else if (oldTableId != null && !oldTableId.isBlank()) {
                targetTableIds.add(oldTableId);
            }

            if (targetTableIds.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Vui lòng chọn ít nhất một bàn khả dụng trước khi xác nhận đặt bàn.");
            }

            List<RestaurantTable> assignedTables = tableRepository.findAllById(targetTableIds);
            if (assignedTables.size() != targetTableIds.size()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Một hoặc nhiều bàn được chọn không tồn tại hoặc đã bị xóa.");
            }

            // Verify all tables belong to the SAME zone
            String primaryZoneId = assignedTables.get(0).getZoneId();
            boolean sameZone = assignedTables.stream().allMatch(t -> primaryZoneId.equals(t.getZoneId()));
            if (!sameZone) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Chỉ có thể gộp các bàn trong cùng một khu vực (zone).");
            }

            // Verify total capacity meets party size
            int totalCapacity = assignedTables.stream().mapToInt(RestaurantTable::getCapacity).sum();
            if (totalCapacity < reservation.getPartySize()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    String.format("Tổng sức chứa các bàn đã chọn là %d chỗ, không đủ cho đoàn %d khách.",
                        totalCapacity, reservation.getPartySize()));
            }

            // Strict Non-redundancy: When merging multiple tables, no single table should already be sufficient,
            // small parties (<= 4) cannot merge multiple tables, and 3+ tables cannot contain redundant tables.
            if (assignedTables.size() > 1) {
                if (reservation.getPartySize() <= 4) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Đoàn nhỏ (<= 4 khách) vui lòng chỉ gán 1 bàn, không gộp nhiều bàn.");
                }
                for (RestaurantTable t : assignedTables) {
                    if (t.getCapacity() >= reservation.getPartySize()) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            String.format("Bàn %s (sức chứa %d chỗ) đã đủ cho đoàn %d khách, không được gộp thêm bàn khác.",
                                t.getTableNumber(), t.getCapacity(), reservation.getPartySize()));
                    }
                }
                if (assignedTables.size() > 2) {
                    for (RestaurantTable t : assignedTables) {
                        if (totalCapacity - t.getCapacity() >= reservation.getPartySize()) {
                            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                String.format("Gộp bàn dư thừa: danh sách bàn đã chọn có Bàn %s không cần thiết vì các bàn còn lại đã đủ cho đoàn %d khách.",
                                    t.getTableNumber(), reservation.getPartySize()));
                        }
                    }
                }
            }

            // Verify time-scoped availability for each table across 1h59m window
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

                boolean hasOtherBooking = overlapping.stream().anyMatch(r -> !r.getId().equals(reservation.getId()));
                if (hasOtherBooking) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                        String.format("Bàn %s đã có đơn đặt trong khung giờ này (%s - %s).",
                            table.getTableNumber(),
                            reservation.getReservationTime().format(TIME_FORMATTER),
                            reservation.getReservationTime().plus(DINING_WINDOW).format(TIME_FORMATTER)));
                }
            }

            // Assign primary table and merged tables
            RestaurantTable primaryTable = assignedTables.get(0);
            reservation.setTableId(primaryTable.getId());
            reservation.setTable(primaryTable);
            reservation.setZoneId(primaryZoneId);
            reservation.setMergedTableIds(String.join(",", targetTableIds));
        }

        // Phase 3.6 / UC13: Transition to ARRIVED (Staff Check-in Time Guard)
        if (newStatus == ReservationStatus.ARRIVED) {
            LocalDateTime bookingDateTime = LocalDateTime.of(
                reservation.getReservationDate(), reservation.getReservationTime());
            LocalDateTime now = LocalDateTime.now();

            // Staff Check-in Time Guard: [-30m, +30m]
            if (now.isBefore(bookingDateTime.minusMinutes(30))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Chưa đến thời gian nhận bàn. Check-in chỉ được phép thực hiện trước tối đa 30 phút so với giờ hẹn.");
            }
            if (now.isAfter(bookingDateTime.plusMinutes(30))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Đã quá thời gian nhận bàn cho phép check-in (tối đa 30 phút sau giờ hẹn).");
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

        return toResponse(saved);
    }

    /**
     * Phase 3.5 / UC12: Self-Cancel Booking with 60-min rule.
     */
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

        return toResponse(saved);
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
            if (date.isEqual(today) && slot.isBefore(now)) {
                continue;
            }
            if (isTimeSlotAvailable(zoneId, date, slot, partySize, totalCapacity)) {
                alternatives.add(slot);
            }
        }
        return alternatives;
    }

    /**
     * Helper to construct ReservationResponse, formatting merged table numbers (e.g. "Bàn 01 + Bàn 02").
     */
    public ReservationResponse toResponse(Reservation reservation) {
        return toResponse(reservation, null, null, null);
    }

    public ReservationResponse toResponse(Reservation reservation, String fallbackZoneId, String fallbackZoneName, Boolean fallbackZoneIsVip) {
        String customTableNumber = null;
        if (reservation.getMergedTableIds() != null && !reservation.getMergedTableIds().isBlank()) {
            List<String> ids = Arrays.stream(reservation.getMergedTableIds().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

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
