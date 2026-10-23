package com.huongViet.reservation;

import com.huongViet.reservation.dto.CreateReservationRequest;
import com.huongViet.reservation.dto.ReservationResponse;
import com.huongViet.reservation.dto.TimeSlotAvailability;
import com.huongViet.reservation.dto.UpdateReservationStatusRequest;
import com.huongViet.reservation.dto.ZoneAvailabilityResponse;
import com.huongViet.table.RestaurantTable;
import com.huongViet.table.TableRepository;
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
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
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
     * UC08: Create a new table reservation.
     * Enforces:
     * - BR-17: Party size > 15 requires event booking protocol
     * - Time slot validation (within fixed list)
     * - Date in the future / today valid time
     * - Zone capacity check (E1) considering CONFIRMED reservations only (BR-16)
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

        // Capacity check (BR-16: only CONFIRMED consume capacity)
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

        // Create reservation with PENDING_HOLD
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
            request.specialRequests()
        );

        Reservation saved = reservationRepository.save(reservation);
        logger.info("Created reservation {} for guest {} on {} at {} (partySize: {})",
            saved.getId(), saved.getGuestName(), saved.getReservationDate(), saved.getReservationTime(), saved.getPartySize());

        // Send email notification (non-blocking, won't break transaction if mail server fails)
        emailService.sendBookingConfirmation(saved, zone.getZoneName());

        return ReservationResponse.from(saved, zone.getId(), zone.getZoneName(), zone.getIsVip());
    }

    /**
     * Check availability across all active zones for a given date and party size.
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
                LocalTime windowStart = slot.minus(DINING_WINDOW).plusMinutes(1);
                LocalTime windowEnd = slot.plus(DINING_WINDOW).minusMinutes(1);

                int confirmedSeats = reservationRepository.sumConfirmedPartySizeByZoneAndSlot(
                    zone.getId(), date, windowStart, windowEnd);
                int remainingCapacity = Math.max(0, zoneCapacity - confirmedSeats);

                boolean isPast = date.isEqual(today) && slot.isBefore(now);
                boolean hasCapacity = (partySize == null) ? (remainingCapacity > 0) : (remainingCapacity >= partySize);
                boolean available = !isPast && hasCapacity && zoneCapacity > 0;

                slotAvailabilities.add(new TimeSlotAvailability(
                    slot,
                    slot.format(TIME_FORMATTER),
                    confirmedSeats,
                    remainingCapacity,
                    available
                ));
            }

            result.add(new ZoneAvailabilityResponse(
                zone.getId(),
                zone.getZoneName(),
                zone.getIsVip(),
                zoneCapacity,
                slotAvailabilities
            ));
        }

        return result;
    }

    /**
     * Return fixed list of time slots.
     */
    public List<String> getTimeSlots() {
        return TIME_SLOTS.stream()
            .map(t -> t.format(TIME_FORMATTER))
            .toList();
    }

    /**
     * Customer booking history (authenticated user).
     */
    public List<ReservationResponse> getCustomerBookings(String customerId) {
        return reservationRepository.findByCustomerIdWithTableAndZone(customerId)
            .stream()
            .map(ReservationResponse::from)
            .toList();
    }

    /**
     * Guest lookup by phone number.
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
     * UC09 foundation: List reservations by date.
     */
    public List<ReservationResponse> listByDate(LocalDate date) {
        return reservationRepository.findByReservationDateWithTableAndZone(date)
            .stream()
            .map(ReservationResponse::from)
            .toList();
    }

    /**
     * UC09 foundation: Update reservation status and assign table.
     */
    @Transactional
    public ReservationResponse updateStatus(String id, UpdateReservationStatusRequest request, String changedBy) {
        Reservation reservation = reservationRepository.findActiveByIdWithTableAndZone(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu đặt bàn"));

        ReservationStatus oldStatus = reservation.getStatus();
        String oldTableId = reservation.getTableId();

        if (request.tableId() != null && !request.tableId().isBlank()) {
            RestaurantTable table = tableRepository.findByIdAndDeletedAtIsNull(request.tableId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bàn không tồn tại hoặc đã bị vô hiệu hóa"));
            reservation.setTableId(table.getId());
            reservation.setTable(table);
        }

        if (request.reason() != null && !request.reason().isBlank()) {
            reservation.setCancellationReason(request.reason());
        }

        reservation.setStatus(request.status());
        Reservation saved = reservationRepository.save(reservation);

        // Record audit trail
        ReservationAudit audit = new ReservationAudit(
            UUID.randomUUID().toString(),
            saved.getId(),
            changedBy,
            oldStatus,
            request.status(),
            oldTableId,
            saved.getTableId(),
            request.reason()
        );
        reservationAuditRepository.save(audit);

        logger.info("Updated reservation {} status from {} to {} by {}", saved.getId(), oldStatus, request.status(), changedBy);

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
