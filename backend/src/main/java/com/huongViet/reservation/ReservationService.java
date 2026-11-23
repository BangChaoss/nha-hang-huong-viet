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
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
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
    public static final BigDecimal VIP_DEPOSIT_AMOUNT = new BigDecimal("1000000");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private static final Map<ReservationStatus, Set<ReservationStatus>> ALLOWED_TRANSITIONS = Map.of(
        ReservationStatus.PENDING_HOLD, Set.of(ReservationStatus.CONFIRMED, ReservationStatus.CANCELLED),
        ReservationStatus.PENDING_DEPOSIT, Set.of(ReservationStatus.PENDING_HOLD, ReservationStatus.CONFIRMED, ReservationStatus.CANCELLED),
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

        // ★ VIP DEPOSIT POLICY: Khu vực VIP → bắt buộc đặt cọc
        boolean isVipZone = zone.getIsVip() != null && zone.getIsVip();
        if (isVipZone) {
            reservation.setStatus(ReservationStatus.PENDING_DEPOSIT);
            reservation.setDepositAmount(VIP_DEPOSIT_AMOUNT);
        }

        Reservation saved = reservationRepository.save(reservation);
        // ★ KHÔNG gửi email cho VIP (theo yêu cầu)
        if (!isVipZone) {
            emailService.sendBookingConfirmation(saved, zone.getZoneName());
        }
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

                int totalFree = computeZoneFreeSeats(zone.getId(), date, windowStart, windowEnd);
                int confirmedSeats = zoneCapacity - totalFree;
                int remaining = totalFree;
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
                int booked = reservationRepository.sumBookedPartySizeForTable(
                    t.getId(), reservation.getReservationDate(), slotStart, slotEnd);
                // Trừ partySize của chính đơn này nếu đã gán bàn này
                int selfSeats = reservationRepository
                    .findOverlappingReservationsForTable(
                        t.getId(), reservation.getReservationDate(), slotStart, slotEnd)
                    .stream()
                    .filter(r -> r.getId().equals(reservation.getId()))
                    .mapToInt(Reservation::getPartySize).sum();
                int freeSeats = t.getCapacity() - (booked - selfSeats);
                return freeSeats > 0;
            })
            .map(t -> {
                int booked = reservationRepository.sumBookedPartySizeForTable(
                    t.getId(), reservation.getReservationDate(), slotStart, slotEnd);
                int selfSeats = reservationRepository
                    .findOverlappingReservationsForTable(
                        t.getId(), reservation.getReservationDate(), slotStart, slotEnd)
                    .stream()
                    .filter(r -> r.getId().equals(reservation.getId()))
                    .mapToInt(Reservation::getPartySize).sum();
                int freeSeats = t.getCapacity() - (booked - selfSeats);
                return TableResponse.from(t, t.getStatus(), freeSeats);
            })
            .toList();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReservationResponse updateStatus(String id, UpdateReservationStatusRequest request, String changedBy) {
        Reservation reservation = reservationRepository.findActiveByIdWithTableAndZone(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu đặt bàn"));

        ReservationStatus oldStatus = reservation.getStatus();
        ReservationStatus newStatus = request.status();

        validateTransition(oldStatus, newStatus);
        String oldTableId = reservation.getTableId();

        // 1. XÁC NHẬN ĐẶT BÀN & GỘP BÀN
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

            LocalTime slotStart = reservation.getReservationTime().minusMinutes(118);
            LocalTime slotEnd = reservation.getReservationTime().plusMinutes(118);

            int totalFreeSeats = 0;
            for (RestaurantTable table : assignedTables) {
                int booked = reservationRepository.sumBookedPartySizeForTable(
                    table.getId(), reservation.getReservationDate(), slotStart, slotEnd);
                int selfSeats = reservationRepository
                    .findOverlappingReservationsForTable(
                        table.getId(), reservation.getReservationDate(), slotStart, slotEnd)
                    .stream()
                    .filter(r -> r.getId().equals(reservation.getId()))
                    .mapToInt(Reservation::getPartySize).sum();
                totalFreeSeats += Math.max(0, table.getCapacity() - (booked - selfSeats));
            }
            if (totalFreeSeats < reservation.getPartySize()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    String.format("Tổng chỗ trống còn lại trên các bàn đã chọn là %d chỗ, không đủ cho đoàn %d khách.", totalFreeSeats, reservation.getPartySize()));
            }

            if (assignedTables.size() > 1) {
                // Nếu một bàn đơn đã đủ cho đoàn, chặn gộp dư thừa
                for (RestaurantTable t : assignedTables) {
                    if (t.getCapacity() >= reservation.getPartySize()) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            String.format("Bàn %s (sức chứa %d chỗ) đã đủ cho đoàn %d khách. Không cần gộp thêm bàn khác.",
                                t.getTableNumber(), t.getCapacity(), reservation.getPartySize()));
                    }
                }
                // Nếu có bàn dư thừa trong cụm gộp (khi chọn từ 3 bàn trở lên)
                if (assignedTables.size() > 2) {
                    for (RestaurantTable t : assignedTables) {
                        if (totalFreeSeats - t.getCapacity() >= reservation.getPartySize()) {
                            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                String.format("Gộp bàn dư thừa: Bàn %s không cần thiết vì các bàn còn lại đã đủ cho đoàn %d khách.",
                                    t.getTableNumber(), reservation.getPartySize()));
                        }
                    }
                }
            }

            for (RestaurantTable table : assignedTables) {
                if (table.getStatus() == TableStatus.MAINTENANCE) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                        String.format("Bàn %s đang ở trạng thái bảo trì/tạm ẩn, không thể gán.", table.getTableNumber()));
                }

                List<Reservation> overlapping = reservationRepository.findOverlappingReservationsForTable(
                    table.getId(), reservation.getReservationDate(), slotStart, slotEnd
                );

                int otherBooked = overlapping.stream()
                    .filter(r -> !r.getId().equals(reservation.getId()))
                    .mapToInt(Reservation::getPartySize).sum();
                int freeSeats = table.getCapacity() - otherBooked;
                if (assignedTables.size() == 1) {
                    if (freeSeats < reservation.getPartySize()) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                            String.format("Bàn %s chỉ còn %d chỗ trống (đã có %d chỗ được đặt), không đủ cho đoàn %d khách.",
                                table.getTableNumber(), freeSeats, otherBooked, reservation.getPartySize()));
                    }
                } else {
                    if (freeSeats <= 0) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                            String.format("Bàn %s đã hết chỗ trống.", table.getTableNumber()));
                    }
                }
            }

            // Giải phóng các bàn cũ nếu trước đó đã từng gán mà nay không nằm trong danh sách gộp mới
            Set<String> newTableSet = new HashSet<>(targetTableIds);
            Set<String> previousTableIds = new HashSet<>();
            if (reservation.getMergedTableIds() != null && !reservation.getMergedTableIds().isBlank()) {
                Arrays.stream(reservation.getMergedTableIds().split(","))
                    .map(String::trim).filter(s -> !s.isEmpty()).forEach(previousTableIds::add);
            }
            if (oldTableId != null && !oldTableId.isBlank()) {
                previousTableIds.add(oldTableId.trim());
            }
            previousTableIds.removeAll(newTableSet);
            for (String unassignedId : previousTableIds) {
                tableRepository.findByIdAndDeletedAtIsNull(unassignedId).ifPresent(tbl -> {
                    if (tbl.getStatus() == TableStatus.RESERVED) {
                        TableStatus prevStatus = tbl.getStatus();
                        tbl.setStatus(TableStatus.AVAILABLE);
                        tableRepository.save(tbl);
                        broadcastTableStatus(tbl, prevStatus, TableStatus.AVAILABLE, 0);
                    }
                });
            }

            RestaurantTable primaryTable = assignedTables.get(0);
            reservation.setTableId(primaryTable.getId());
            reservation.setTable(primaryTable);
            reservation.setZoneId(primaryZoneId);
            reservation.setMergedTableIds(String.join(",", targetTableIds));

            // ĐỒNG BỘ: Nếu đơn đặt bàn diễn ra trong ngày hôm nay, lập tức đổi các bàn sang RESERVED
            LocalDate today = LocalDate.now();
            if (reservation.getReservationDate().isEqual(today)) {
                int remainingParty = reservation.getPartySize();
                for (RestaurantTable tbl : assignedTables) {
                    int partyForTbl = Math.min(remainingParty, tbl.getCapacity());
                    remainingParty = Math.max(0, remainingParty - tbl.getCapacity());
                    if (tbl.getStatus() == TableStatus.AVAILABLE) {
                        TableStatus prevStatus = tbl.getStatus();
                        tbl.setStatus(TableStatus.RESERVED);
                        tableRepository.save(tbl);
                        broadcastTableStatus(tbl, prevStatus, TableStatus.RESERVED, partyForTbl);
                    } else {
                        broadcastTableStatus(tbl, tbl.getStatus(), tbl.getStatus(), partyForTbl);
                    }
                }
            }
        }

        // 2. CHECK-IN KHÁCH NHẬN BÀN
        if (newStatus == ReservationStatus.ARRIVED) {
            LocalDate today = LocalDate.now();
            if (!reservation.getReservationDate().isEqual(today)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chỉ có thể check-in các đơn đặt bàn trong ngày hôm nay.");
            }
            LocalDateTime bookingDateTime = LocalDateTime.of(reservation.getReservationDate(), reservation.getReservationTime());
            LocalDateTime nowTime = LocalDateTime.now();
            if (nowTime.isBefore(bookingDateTime.minusMinutes(30))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chưa đến thời gian nhận bàn (chỉ được nhận bàn trước tối đa 30 phút).");
            }
            if (nowTime.isAfter(bookingDateTime.plusMinutes(30))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đã quá thời gian nhận bàn (quá 30 phút so với giờ hẹn).");
            }

            List<String> targetTableIds = new ArrayList<>();
            if (reservation.getMergedTableIds() != null && !reservation.getMergedTableIds().isBlank()) {
                targetTableIds.addAll(Arrays.stream(reservation.getMergedTableIds().split(","))
                    .map(String::trim).filter(s -> !s.isEmpty()).distinct().toList());
            } else if (reservation.getTableId() != null) {
                targetTableIds.add(reservation.getTableId());
            }

            targetTableIds.sort(String::compareTo);
            int remainingParty = reservation.getPartySize();
            for (String tId : targetTableIds) {
                final int currentRemaining = remainingParty;
                tableRepository.lockActiveById(tId).ifPresent(t -> {
                    if (tableRepository.hasSeatedGuests(tId)) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Bàn " + t.getTableNumber() + " còn khách đang ngồi. Vui lòng xếp bàn khác trước khi check-in.");
                    }
                    int take = Math.min(currentRemaining, t.getCapacity());
                    TableStatus oldTStatus = t.getStatus();
                    t.setStatus(TableStatus.OCCUPIED);
                    tableRepository.save(t);
                    broadcastTableStatus(t, oldTStatus, TableStatus.OCCUPIED, take);
                });
                RestaurantTable locked = tableRepository.findById(tId).orElse(null);
                if (locked != null) {
                    remainingParty = Math.max(0, remainingParty - locked.getCapacity());
                }
            }
        }

        // 3. KHI HỦY HOẶC ĐÁNH DẤU NO_SHOW: TỰ ĐỘNG GIẢI PHÓNG TOÀN BỘ CÁC BÀN VỀ AVAILABLE NGAY LẬP TỨC
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
        String actor = null;
        boolean isGuest = false;

        if (reservation.getCustomerId() != null) {
            // Đơn thuộc tài khoản thành viên: Bắt buộc phải là chính tài khoản đó hủy
            if (customerId != null && reservation.getCustomerId().equals(customerId)) {
                isAuthorized = true;
                actor = customerId;
            }
        } else {
            // Đơn của khách vãng lai: Cho phép hủy khi cung cấp chính xác số điện thoại đặt bàn
            if (request != null && request.phone() != null && !request.phone().isBlank()) {
                if (request.phone().trim().equals(reservation.getGuestPhone())) {
                    isAuthorized = true;
                    isGuest = true;
                    actor = null; // null để tránh vi phạm khóa ngoại fk_res_audit_user (users.id)
                }
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

        // Tự động giải phóng triệt để toàn bộ các bàn đã gán (kể cả bàn gộp) về trạng thái AVAILABLE ngay lập tức
        releaseAssignedTables(reservation);

        ReservationStatus oldStatus = reservation.getStatus();
        reservation.setStatus(ReservationStatus.CANCELLED);
        String reason = (request != null && request.reason() != null && !request.reason().isBlank())
            ? request.reason() : "Khách hàng tự hủy trên hệ thống";
        reservation.setCancellationReason(reason);

        Reservation saved = reservationRepository.save(reservation);

        ReservationAudit audit = new ReservationAudit(
            UUID.randomUUID().toString(), saved.getId(), actor, oldStatus, ReservationStatus.CANCELLED,
            reservation.getTableId(), null, isGuest ? ("[Khách vãng lai] " + reason) : reason
        );
        reservationAuditRepository.save(audit);

        ReservationResponse response = toResponse(saved);
        emailService.sendBookingCancelledEmail(saved, reason, response.zoneName());
        return response;
    }

    /**
     * Tự động giải phóng toàn bộ các bàn đã gán trong đơn đặt bàn (hỗ trợ cả bàn đơn và cụm bàn gộp)
     * về trạng thái AVAILABLE ngay lập tức và phát thông báo WebSocket đến toàn bộ client.
     */
    public void releaseAssignedTables(Reservation reservation) {
        Set<String> targetTableIds = new HashSet<>();
        if (reservation.getMergedTableIds() != null && !reservation.getMergedTableIds().isBlank()) {
            Arrays.stream(reservation.getMergedTableIds().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .forEach(targetTableIds::add);
        }
        if (reservation.getTableId() != null && !reservation.getTableId().isBlank()) {
            targetTableIds.add(reservation.getTableId().trim());
        }

        for (String tableId : targetTableIds.stream().sorted().toList()) {
            tableRepository.lockActiveById(tableId).ifPresent(t -> {
                TableStatus oldStatus = t.getStatus();
                if (oldStatus == TableStatus.RESERVED) {
                    t.setStatus(TableStatus.AVAILABLE);
                    tableRepository.save(t);
                    broadcastTableStatus(t, oldStatus, TableStatus.AVAILABLE, 0);
                    logger.info("Table {} ({}) automatically released {} -> AVAILABLE due to cancellation of reservation {}",
                        t.getTableNumber(), t.getId(), oldStatus, reservation.getId());
                } else if (oldStatus == TableStatus.AVAILABLE) {
                    // Ngay cả khi bàn trên DB đã là AVAILABLE, vẫn phát broadcast để client (POS, FloorPlan)
                    // hủy cache hiển thị trạng thái ảo theo đơn đặt bàn
                    broadcastTableStatus(t, TableStatus.RESERVED, TableStatus.AVAILABLE, 0);
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
        int totalFree = computeZoneFreeSeats(zoneId, date, windowStart, windowEnd);
        return totalFree >= partySize;
    }

    /**
     * Tính chỗ trống thực tế PER TABLE rồi cộng lại, trừ đi số khách của các đơn chưa gán bàn.
     * Hỗ trợ shared seating: mỗi bàn = capacity - SUM(partySize đang booked).
     */
    private int computeZoneFreeSeats(String zoneId, LocalDate date,
                                      LocalTime windowStart, LocalTime windowEnd) {
        List<RestaurantTable> zoneTables = tableRepository
            .findAllByZoneIdAndDeletedAtIsNull(zoneId);
        int totalFreeTableSeats = 0;
        for (RestaurantTable table : zoneTables) {
            if (table.getStatus() == TableStatus.MAINTENANCE) continue;
            int booked = reservationRepository.sumBookedPartySizeForTable(
                table.getId(), date, windowStart, windowEnd);
            totalFreeTableSeats += Math.max(0, table.getCapacity() - booked);
        }

        // Trừ các đơn đặt bàn đang giữ chỗ trong khu vực nhưng chưa được gán bàn cụ thể (PENDING_HOLD / PENDING_DEPOSIT)
        int unassignedBooked = reservationRepository.sumUnassignedPartySizeForZone(
            zoneId, date, windowStart, windowEnd);

        return Math.max(0, totalFreeTableSeats - unassignedBooked);
    }

    /**
     * Xác nhận đặt cọc VNPay thành công → chuyển PENDING_DEPOSIT → PENDING_HOLD.
     */
    @Transactional
    public ReservationResponse confirmDeposit(String reservationId, String transactionRef) {
        Reservation reservation = reservationRepository.findActiveByIdWithTableAndZone(reservationId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                "Không tìm thấy đơn đặt bàn"));

        if (reservation.getStatus() != ReservationStatus.PENDING_DEPOSIT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Đơn đặt bàn không ở trạng thái chờ đặt cọc");
        }

        reservation.setStatus(ReservationStatus.PENDING_HOLD);
        Reservation saved = reservationRepository.save(reservation);

        // Audit
        ReservationAudit audit = new ReservationAudit(
            UUID.randomUUID().toString(), saved.getId(), null,
            ReservationStatus.PENDING_DEPOSIT, ReservationStatus.PENDING_HOLD,
            null, null,
            "Đặt cọc VNPay thành công. Mã GD: " + transactionRef
        );
        reservationAuditRepository.save(audit);

        String zoneName = (saved.getZone() != null) ? saved.getZone().getZoneName() : "Khu vực VIP";
        emailService.sendBookingConfirmation(saved, zoneName);

        return toResponse(saved);
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
        broadcastTableStatus(table, oldStatus, newStatus, null);
    }

    private void broadcastTableStatus(RestaurantTable table, TableStatus oldStatus, TableStatus newStatus, Integer occupiedSeats) {
        if (messagingTemplate == null) return;
        try {
            String zoneName = table.getZone() != null ? table.getZone().getZoneName() : null;
            messagingTemplate.convertAndSend("/topic/table-status",
                new TableStatusMessage(table.getId(), table.getTableNumber(), oldStatus, newStatus, zoneName, occupiedSeats));
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