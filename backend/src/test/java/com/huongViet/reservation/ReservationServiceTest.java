package com.huongViet.reservation;

import com.huongViet.reservation.dto.CancelReservationRequest;
import com.huongViet.reservation.dto.CreateReservationRequest;
import com.huongViet.reservation.dto.ReservationResponse;
import com.huongViet.reservation.dto.UpdateReservationStatusRequest;
import com.huongViet.reservation.dto.ZoneAvailabilityResponse;
import com.huongViet.table.RestaurantTable;
import com.huongViet.table.TableRepository;
import com.huongViet.table.TableZone;
import com.huongViet.table.ZoneRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private ZoneRepository zoneRepository;

    @Mock
    private TableRepository tableRepository;

    @Mock
    private ReservationAuditRepository reservationAuditRepository;

    @Mock
    private ReservationEmailService emailService;
    @Mock
    private org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;

    private ReservationService reservationService;

    private TableZone sampleZone;
    private List<RestaurantTable> sampleTables;

    @BeforeEach
    void setUp() {
        reservationService = new ReservationService(
            reservationRepository,
            zoneRepository,
            tableRepository,
            reservationAuditRepository,
            emailService,
            messagingTemplate
        );

        sampleZone = new TableZone("zone-1", "Tầng trệt trong nhà", false);
        sampleTables = List.of(
            new RestaurantTable("tbl-1", "zone-1", "Bàn 01", 4),
            new RestaurantTable("tbl-2", "zone-1", "Bàn 02", 6),
            new RestaurantTable("tbl-3", "zone-1", "Bàn 03", 10)
        ); // Total capacity = 20
    }

    @Test
    @DisplayName("1. createBooking_validRequest_returnsReservation - Đặt bàn thành công trả về PENDING_HOLD")
    void createBooking_validRequest_returnsReservation() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        LocalTime slot = LocalTime.of(18, 0);
        CreateReservationRequest request = new CreateReservationRequest(
            "Nguyễn Văn A",
            "0912345678",
            "nguyenvana@gmail.com",
            tomorrow,
            slot,
            4,
            "zone-1",
            "Gần cửa sổ"
        );

        when(zoneRepository.findById("zone-1")).thenReturn(Optional.of(sampleZone));
        when(tableRepository.findAllByZoneIdAndDeletedAtIsNull("zone-1")).thenReturn(sampleTables);
        when(reservationRepository.sumConfirmedPartySizeByZoneAndSlot(eq("zone-1"), eq(tomorrow), any(), any()))
            .thenReturn(0);
        when(reservationRepository.save(any(Reservation.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        ReservationResponse response = reservationService.createBooking(request, null);

        assertNotNull(response);
        assertEquals("Nguyễn Văn A", response.guestName());
        assertEquals("0912345678", response.guestPhone());
        assertEquals(ReservationStatus.PENDING_HOLD, response.status());
        assertEquals(4, response.partySize());
        assertEquals("zone-1", response.zoneId());
        assertEquals("Tầng trệt trong nhà", response.zoneName());
        assertNull(response.tableId());

        verify(emailService, times(1)).sendBookingConfirmation(any(Reservation.class), eq("Tầng trệt trong nhà"));
    }

    @Test
    @DisplayName("2. createBooking_partySizeOver15_throwsBR17 - Đoàn trên 15 khách quăng lỗi 400 Bad Request")
    void createBooking_partySizeOver15_throwsBR17() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        CreateReservationRequest request = new CreateReservationRequest(
            "Trần Thị B",
            "0987654321",
            null,
            tomorrow,
            LocalTime.of(18, 0),
            16,
            "zone-1",
            null
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            reservationService.createBooking(request, null)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Đoàn trên 15 khách cần đặt sự kiện"));
        verifyNoInteractions(reservationRepository);
    }

    @Test
    @DisplayName("3. createBooking_pastDate_throwsValidation - Đặt ngày trong quá khứ quăng lỗi 400")
    void createBooking_pastDate_throwsValidation() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        CreateReservationRequest request = new CreateReservationRequest(
            "Lê Văn C",
            "0901234567",
            null,
            yesterday,
            LocalTime.of(12, 0),
            2,
            "zone-1",
            null
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            reservationService.createBooking(request, null)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("quá khứ"));
        verifyNoInteractions(reservationRepository);
    }

    @Test
    @DisplayName("4. createBooking_invalidTimeSlot_throwsError - Giờ đặt không nằm trong predefined slots quăng lỗi 400")
    void createBooking_invalidTimeSlot_throwsError() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        CreateReservationRequest request = new CreateReservationRequest(
            "Phạm Văn D",
            "0933445566",
            null,
            tomorrow,
            LocalTime.of(15, 17), // Invalid slot
            2,
            "zone-1",
            null
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            reservationService.createBooking(request, null)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Khung giờ đặt bàn không hợp lệ"));
    }

    @Test
    @DisplayName("5. createBooking_overCapacity_throwsConflictE1 - Hết chỗ quăng lỗi 409 Conflict kèm giờ gợi ý")
    void createBooking_overCapacity_throwsConflictE1() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        LocalTime requestedSlot = LocalTime.of(18, 0);
        CreateReservationRequest request = new CreateReservationRequest(
            "Hoàng Văn E",
            "0944556677",
            null,
            tomorrow,
            requestedSlot,
            6,
            "zone-1",
            null
        );

        when(zoneRepository.findById("zone-1")).thenReturn(Optional.of(sampleZone));
        when(tableRepository.findAllByZoneIdAndDeletedAtIsNull("zone-1")).thenReturn(sampleTables); // capacity = 20

        // Slot 18:00 has 18 confirmed seats -> remaining = 2 < 6 (over capacity)
        when(reservationRepository.sumConfirmedPartySizeByZoneAndSlot(eq("zone-1"), eq(tomorrow), any(), any()))
            .thenAnswer(invocation -> {
                LocalTime start = invocation.getArgument(2);
                LocalTime end = invocation.getArgument(3);
                // If checking 18:00 window, return 18 confirmed seats
                if (start.isBefore(requestedSlot) && end.isAfter(requestedSlot)) {
                    return 18;
                }
                // Other slots have 0 confirmed seats (available as suggestions)
                return 0;
            });

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            reservationService.createBooking(request, null)
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Khung giờ đã đầy cho khu vực này"));
        assertTrue(ex.getReason().contains("Khung giờ gợi ý"));
    }

    @Test
    @DisplayName("6. createBooking_pendingDoesNotConsumeCapacity_BR16 - Đơn PENDING không làm giảm sức chứa (BR-16)")
    void createBooking_pendingDoesNotConsumeCapacity_BR16() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        LocalTime slot = LocalTime.of(12, 0);
        CreateReservationRequest request = new CreateReservationRequest(
            "Đặng Văn F",
            "0966778899",
            null,
            tomorrow,
            slot,
            10,
            "zone-1",
            null
        );

        when(zoneRepository.findById("zone-1")).thenReturn(Optional.of(sampleZone));
        when(tableRepository.findAllByZoneIdAndDeletedAtIsNull("zone-1")).thenReturn(sampleTables); // capacity = 20
        // Confirmed seats is 5, so remaining capacity is 15 >= 10 (even if pending seats exist)
        when(reservationRepository.sumConfirmedPartySizeByZoneAndSlot(eq("zone-1"), eq(tomorrow), any(), any()))
            .thenReturn(5);
        when(reservationRepository.save(any(Reservation.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        ReservationResponse response = reservationService.createBooking(request, null);

        assertNotNull(response);
        assertEquals(ReservationStatus.PENDING_HOLD, response.status());
    }

    @Test
    @DisplayName("7. createBooking_guestWithoutLogin_nullCustomerId - Khách vãng lai đặt bàn có customerId = null")
    void createBooking_guestWithoutLogin_nullCustomerId() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        CreateReservationRequest request = new CreateReservationRequest(
            "Khách Vãng Lai",
            "0911223344",
            null,
            tomorrow,
            LocalTime.of(19, 0),
            2,
            "zone-1",
            null
        );

        when(zoneRepository.findById("zone-1")).thenReturn(Optional.of(sampleZone));
        when(tableRepository.findAllByZoneIdAndDeletedAtIsNull("zone-1")).thenReturn(sampleTables);
        when(reservationRepository.sumConfirmedPartySizeByZoneAndSlot(eq("zone-1"), eq(tomorrow), any(), any()))
            .thenReturn(0);

        ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
        when(reservationRepository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        reservationService.createBooking(request, null);

        Reservation savedEntity = captor.getValue();
        assertNull(savedEntity.getCustomerId());
        assertEquals("Khách Vãng Lai", savedEntity.getGuestName());
    }

    @Test
    @DisplayName("8. createBooking_authenticatedUser_setsCustomerId - Khách đăng nhập đặt bàn được gán customerId")
    void createBooking_authenticatedUser_setsCustomerId() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        CreateReservationRequest request = new CreateReservationRequest(
            "Thành viên VIP",
            "0977889900",
            "vip@huongviet.com",
            tomorrow,
            LocalTime.of(19, 30),
            4,
            "zone-1",
            null
        );

        when(zoneRepository.findById("zone-1")).thenReturn(Optional.of(sampleZone));
        when(tableRepository.findAllByZoneIdAndDeletedAtIsNull("zone-1")).thenReturn(sampleTables);
        when(reservationRepository.sumConfirmedPartySizeByZoneAndSlot(eq("zone-1"), eq(tomorrow), any(), any()))
            .thenReturn(0);

        ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
        when(reservationRepository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        reservationService.createBooking(request, "customer-uuid-123");

        Reservation savedEntity = captor.getValue();
        assertEquals("customer-uuid-123", savedEntity.getCustomerId());
        assertEquals("vip@huongviet.com", savedEntity.getGuestEmail());
    }

    @Test
    @DisplayName("9. checkAvailability_returnsPerZoneSlots - Kiểm tra tính khả dụng trả về danh sách đầy đủ các zone và khung giờ")
    void checkAvailability_returnsPerZoneSlots() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        when(zoneRepository.findAll()).thenReturn(List.of(sampleZone));
        when(tableRepository.findAllByZoneIdAndDeletedAtIsNull("zone-1")).thenReturn(sampleTables);
        when(reservationRepository.sumConfirmedPartySizeByZoneAndSlot(eq("zone-1"), eq(tomorrow), any(), any()))
            .thenReturn(4);

        List<ZoneAvailabilityResponse> result = reservationService.checkAvailability(tomorrow, 4);

        assertNotNull(result);
        assertEquals(1, result.size());
        ZoneAvailabilityResponse zoneResp = result.get(0);
        assertEquals("zone-1", zoneResp.zoneId());
        assertEquals(20, zoneResp.totalCapacity());
        assertEquals(ReservationService.TIME_SLOTS.size(), zoneResp.timeSlots().size());
        assertTrue(zoneResp.timeSlots().get(0).available());
        assertEquals(16, zoneResp.timeSlots().get(0).remainingCapacity());
    }

    @Test
    @DisplayName("10. checkAvailability_fullSlot_markedUnavailable - Khung giờ đầy chỗ được đánh dấu available = false")
    void checkAvailability_fullSlot_markedUnavailable() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        when(zoneRepository.findAll()).thenReturn(List.of(sampleZone));
        when(tableRepository.findAllByZoneIdAndDeletedAtIsNull("zone-1")).thenReturn(sampleTables); // capacity = 20
        // Confirmed = 18 -> remaining = 2
        when(reservationRepository.sumConfirmedPartySizeByZoneAndSlot(eq("zone-1"), eq(tomorrow), any(), any()))
            .thenReturn(18);

        // Requested party size = 4 > remaining 2 -> should be marked available = false
        List<ZoneAvailabilityResponse> result = reservationService.checkAvailability(tomorrow, 4);

        assertFalse(result.get(0).timeSlots().get(0).available());
        assertEquals(2, result.get(0).timeSlots().get(0).remainingCapacity());
    }

    @Test
    @DisplayName("11. continuousSequenceOfSlots_has21SlotsFrom11To21 - Chuỗi khung giờ liên tục từ 11:00 đến 21:00")
    void continuousSequenceOfSlots_has21SlotsFrom11To21() {
        assertEquals(21, ReservationService.TIME_SLOTS.size());
        assertEquals(LocalTime.of(11, 0), ReservationService.TIME_SLOTS.get(0));
        assertEquals(LocalTime.of(21, 0), ReservationService.TIME_SLOTS.get(20));
        for (int i = 0; i < ReservationService.TIME_SLOTS.size() - 1; i++) {
            assertEquals(
                ReservationService.TIME_SLOTS.get(i).plusMinutes(30),
                ReservationService.TIME_SLOTS.get(i + 1),
                "Mỗi slot phải cách nhau đúng 30 phút liên tục"
            );
        }
    }

    @Test
    @DisplayName("12. turnTimeOverlap_adjacentSlotsDoNotConflict - Thời lượng 1h59m không làm 13:00 xung đột với 11:00")
    void turnTimeOverlap_adjacentSlotsDoNotConflict() {
        LocalDate date = LocalDate.now().plusDays(1);
        LocalTime slot13 = LocalTime.of(13, 0);

        when(zoneRepository.findAll()).thenReturn(List.of(sampleZone));
        when(tableRepository.findAllByZoneIdAndDeletedAtIsNull("zone-1")).thenReturn(sampleTables);
        when(reservationRepository.sumConfirmedPartySizeByZoneAndSlot(any(), any(), any(), any()))
            .thenReturn(0);

        List<ZoneAvailabilityResponse> result = reservationService.checkAvailability(date, 4);

        assertNotNull(result);
        var slot13Resp = result.get(0).timeSlots().stream()
            .filter(s -> s.time().equals(slot13))
            .findFirst();
        assertTrue(slot13Resp.isPresent());
        assertTrue(slot13Resp.get().available());

        verify(reservationRepository).sumConfirmedPartySizeByZoneAndSlot(
            eq("zone-1"), eq(date), eq(LocalTime.of(11, 2)), eq(LocalTime.of(14, 58))
        );
    }

    @Test
    @DisplayName("13. updateStatus_staffCheckIn_outsideWindow_throwsBadRequest - Check-in quá sớm (>30p) hoặc quá trễ (>30p) quăng 400")
    void updateStatus_staffCheckIn_outsideWindow_throwsBadRequest() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime tooEarly = now.plusMinutes(45);
        Reservation earlyReservation = new Reservation(
            "res-early", null, "Khách A", "0900000001", null, 4,
            tooEarly.toLocalDate(), tooEarly.toLocalTime(), null, "zone-1"
        );
        earlyReservation.setStatus(ReservationStatus.CONFIRMED);
        earlyReservation.setTableId("tbl-1");

        when(reservationRepository.findActiveByIdWithTableAndZone("res-early"))
            .thenReturn(Optional.of(earlyReservation));

        UpdateReservationStatusRequest checkInReq = new UpdateReservationStatusRequest(
            ReservationStatus.ARRIVED, null, null, null
        );

        ResponseStatusException exEarly = assertThrows(ResponseStatusException.class, () ->
            reservationService.updateStatus("res-early", checkInReq, "staff-1")
        );
        assertEquals(HttpStatus.BAD_REQUEST, exEarly.getStatusCode());
        assertTrue(exEarly.getReason().contains("Chưa đến thời gian nhận bàn"));

        LocalDateTime tooLate = now.minusMinutes(45);
        Reservation lateReservation = new Reservation(
            "res-late", null, "Khách B", "0900000002", null, 4,
            tooLate.toLocalDate(), tooLate.toLocalTime(), null, "zone-1"
        );
        lateReservation.setStatus(ReservationStatus.CONFIRMED);
        lateReservation.setTableId("tbl-1");

        when(reservationRepository.findActiveByIdWithTableAndZone("res-late"))
            .thenReturn(Optional.of(lateReservation));

        ResponseStatusException exLate = assertThrows(ResponseStatusException.class, () ->
            reservationService.updateStatus("res-late", checkInReq, "staff-1")
        );
        assertEquals(HttpStatus.BAD_REQUEST, exLate.getStatusCode());
        assertTrue(exLate.getReason().contains("Đã quá thời gian nhận bàn"));
    }

    @Test
    @DisplayName("14. updateStatus_staffCheckIn_withinWindow_success - Check-in trong phạm vi [-30m, +30m] thành công")
    void updateStatus_staffCheckIn_withinWindow_success() {
        LocalDateTime now = LocalDateTime.now();
        Reservation validReservation = new Reservation(
            "res-valid", null, "Khách C", "0900000003", null, 4,
            now.toLocalDate(), now.toLocalTime(), null, "zone-1"
        );
        validReservation.setStatus(ReservationStatus.CONFIRMED);
        validReservation.setTableId("tbl-1");

        when(reservationRepository.findActiveByIdWithTableAndZone("res-valid"))
            .thenReturn(Optional.of(validReservation));
        when(reservationRepository.save(any(Reservation.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        UpdateReservationStatusRequest checkInReq = new UpdateReservationStatusRequest(
            ReservationStatus.ARRIVED, null, null, null
        );

        ReservationResponse response = reservationService.updateStatus("res-valid", checkInReq, "staff-1");
        assertNotNull(response);
        assertEquals(ReservationStatus.ARRIVED, response.status());
    }

    @Test
    @DisplayName("15. updateStatus_assignMergedTables_sameZoneAndCapacity_success - Gộp bàn cùng khu vực đủ sức chứa thành công")
    void updateStatus_assignMergedTables_sameZoneAndCapacity_success() {
        Reservation reservation = new Reservation(
            "res-merge", null, "Đoàn đông", "0988776655", null, 9,
            LocalDate.now().plusDays(1), LocalTime.of(18, 0), null, "zone-1"
        );
        reservation.setStatus(ReservationStatus.PENDING_HOLD);

        RestaurantTable t1 = new RestaurantTable("tbl-1", "zone-1", "Bàn 01", 4);
        RestaurantTable t2 = new RestaurantTable("tbl-2", "zone-1", "Bàn 02", 6);

        when(reservationRepository.findActiveByIdWithTableAndZone("res-merge"))
            .thenReturn(Optional.of(reservation));
        when(tableRepository.findAllById(List.of("tbl-1", "tbl-2")))
            .thenReturn(List.of(t1, t2));
        when(reservationRepository.findOverlappingReservationsForTable(any(), any(), any(), any()))
            .thenReturn(List.of());
        when(reservationRepository.save(any(Reservation.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        UpdateReservationStatusRequest mergeReq = new UpdateReservationStatusRequest(
            ReservationStatus.CONFIRMED, null, List.of("tbl-1", "tbl-2"), null
        );

        ReservationResponse response = reservationService.updateStatus("res-merge", mergeReq, "staff-1");
        assertNotNull(response);
        assertEquals(ReservationStatus.CONFIRMED, response.status());
        assertEquals("tbl-1,tbl-2", response.mergedTableIds());
        assertEquals("Bàn 01 + Bàn 02", response.tableNumber());
        verify(emailService, times(1)).sendBookingApprovedEmail(any(Reservation.class), eq("Bàn 01 + Bàn 02"), any());
    }

    @Test
    @DisplayName("16. updateStatus_assignMergedTables_differentZones_throwsBadRequest - Gộp bàn khác khu vực quăng 400")
    void updateStatus_assignMergedTables_differentZones_throwsBadRequest() {
        Reservation reservation = new Reservation(
            "res-diff-zones", null, "Đoàn khách", "0988776655", null, 6,
            LocalDate.now().plusDays(1), LocalTime.of(18, 0), null, "zone-1"
        );
        reservation.setStatus(ReservationStatus.PENDING_HOLD);

        RestaurantTable t1 = new RestaurantTable("tbl-1", "zone-1", "01", 4);
        RestaurantTable t2 = new RestaurantTable("tbl-other", "zone-2", "VIP-01", 6);

        when(reservationRepository.findActiveByIdWithTableAndZone("res-diff-zones"))
            .thenReturn(Optional.of(reservation));
        when(tableRepository.findAllById(List.of("tbl-1", "tbl-other")))
            .thenReturn(List.of(t1, t2));

        UpdateReservationStatusRequest mergeReq = new UpdateReservationStatusRequest(
            ReservationStatus.CONFIRMED, null, List.of("tbl-1", "tbl-other"), null
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            reservationService.updateStatus("res-diff-zones", mergeReq, "staff-1")
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("cùng một khu vực"));
    }

    @Test
    @DisplayName("17. updateStatus_assignMergedTables_insufficientCapacity_throwsBadRequest - Tổng sức chứa bàn gộp không đủ quăng 400")
    void updateStatus_assignMergedTables_insufficientCapacity_throwsBadRequest() {
        Reservation reservation = new Reservation(
            "res-small-tables", null, "Đoàn 12 người", "0988776655", null, 12,
            LocalDate.now().plusDays(1), LocalTime.of(18, 0), null, "zone-1"
        );
        reservation.setStatus(ReservationStatus.PENDING_HOLD);

        RestaurantTable t1 = new RestaurantTable("tbl-1", "zone-1", "01", 4);
        RestaurantTable t2 = new RestaurantTable("tbl-2", "zone-1", "02", 4); // sum = 8 < 12

        when(reservationRepository.findActiveByIdWithTableAndZone("res-small-tables"))
            .thenReturn(Optional.of(reservation));
        when(tableRepository.findAllById(List.of("tbl-1", "tbl-2")))
            .thenReturn(List.of(t1, t2));

        UpdateReservationStatusRequest mergeReq = new UpdateReservationStatusRequest(
            ReservationStatus.CONFIRMED, null, List.of("tbl-1", "tbl-2"), null
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            reservationService.updateStatus("res-small-tables", mergeReq, "staff-1")
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("không đủ cho đoàn"));
    }

    @Test
    @DisplayName("18. updateStatus_assignMergedTables_singleTableAlreadySufficient_throwsBadRequest - Đơn 2 người chọn bàn 4 và bàn 6 quăng 400 chặn gộp dư thừa")
    void updateStatus_assignMergedTables_singleTableAlreadySufficient_throwsBadRequest() {
        Reservation reservation = new Reservation(
            "res-party-2", null, "Khách 2 người", "0911223344", null, 2,
            LocalDate.now().plusDays(1), LocalTime.of(18, 0), null, "zone-1"
        );
        reservation.setStatus(ReservationStatus.PENDING_HOLD);

        RestaurantTable t1 = new RestaurantTable("tbl-1", "zone-1", "01", 4); // 4 >= 2
        RestaurantTable t2 = new RestaurantTable("tbl-2", "zone-1", "02", 6);

        when(reservationRepository.findActiveByIdWithTableAndZone("res-party-2"))
            .thenReturn(Optional.of(reservation));
        when(tableRepository.findAllById(List.of("tbl-1", "tbl-2")))
            .thenReturn(List.of(t1, t2));

        UpdateReservationStatusRequest mergeReq = new UpdateReservationStatusRequest(
            ReservationStatus.CONFIRMED, null, List.of("tbl-1", "tbl-2"), null
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            reservationService.updateStatus("res-party-2", mergeReq, "staff-1")
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("đã đủ cho đoàn"));
    }

    @Test
    @DisplayName("19. updateStatus_assignMergedTables_redundantTableInSet_throwsBadRequest - Đơn 7 người gộp 3 bàn 4+4+4 quăng 400 vì bàn 3 dư thừa")
    void updateStatus_assignMergedTables_redundantTableInSet_throwsBadRequest() {
        Reservation reservation = new Reservation(
            "res-party-7", null, "Đoàn 7 người", "0911223344", null, 7,
            LocalDate.now().plusDays(1), LocalTime.of(18, 0), null, "zone-1"
        );
        reservation.setStatus(ReservationStatus.PENDING_HOLD);

        RestaurantTable t1 = new RestaurantTable("tbl-1", "zone-1", "01", 4);
        RestaurantTable t2 = new RestaurantTable("tbl-2", "zone-1", "02", 4);
        RestaurantTable t3 = new RestaurantTable("tbl-3", "zone-1", "03", 4); // 4+4 = 8 >= 7, tbl-3 redundant

        when(reservationRepository.findActiveByIdWithTableAndZone("res-party-7"))
            .thenReturn(Optional.of(reservation));
        when(tableRepository.findAllById(List.of("tbl-1", "tbl-2", "tbl-3")))
            .thenReturn(List.of(t1, t2, t3));

        UpdateReservationStatusRequest mergeReq = new UpdateReservationStatusRequest(
            ReservationStatus.CONFIRMED, null, List.of("tbl-1", "tbl-2", "tbl-3"), null
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            reservationService.updateStatus("res-party-7", mergeReq, "staff-1")
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("dư thừa"));
    }

    @Test
    @DisplayName("20. updateStatus_cancelBooking_triggersCancelledEmail - Staff hủy đơn gửi email kèm lý do")
    void updateStatus_cancelBooking_triggersCancelledEmail() {
        Reservation reservation = new Reservation(
            "res-cancel", null, "Khách đặt bàn", "0911223344", "khach@example.com", 2,
            LocalDate.now().plusDays(1), LocalTime.of(18, 0), null, "zone-1"
        );
        reservation.setStatus(ReservationStatus.PENDING_HOLD);

        when(reservationRepository.findActiveByIdWithTableAndZone("res-cancel"))
            .thenReturn(Optional.of(reservation));
        when(reservationRepository.save(any(Reservation.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        UpdateReservationStatusRequest cancelReq = new UpdateReservationStatusRequest(
            ReservationStatus.CANCELLED, null, null, "Hết bàn phục vụ vào khung giờ này"
        );

        ReservationResponse response = reservationService.updateStatus("res-cancel", cancelReq, "staff-1");
        assertNotNull(response);
        assertEquals(ReservationStatus.CANCELLED, response.status());
        assertEquals("Hết bàn phục vụ vào khung giờ này", response.cancellationReason());

        verify(emailService, times(1)).sendBookingCancelledEmail(
            any(Reservation.class),
            eq("Hết bàn phục vụ vào khung giờ này"),
            any()
        );
    }

    @Test
    @DisplayName("21. cancelByCustomer_triggersCancelledEmail - Khách tự hủy đơn gửi email kèm lý do")
    void cancelByCustomer_triggersCancelledEmail() {
        Reservation reservation = new Reservation(
            "res-self-cancel", "cust-1", "Khách tự hủy", "0909998877", "cust@example.com", 2,
            LocalDate.now().plusDays(2), LocalTime.of(19, 0), null, "zone-1"
        );
        reservation.setStatus(ReservationStatus.PENDING_HOLD);

        when(reservationRepository.findActiveByIdWithTableAndZone("res-self-cancel"))
            .thenReturn(Optional.of(reservation));
        when(reservationRepository.save(any(Reservation.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        CancelReservationRequest req = new CancelReservationRequest("Tôi có việc bận gia đình", "0909998877");

        ReservationResponse response = reservationService.cancelByCustomer("res-self-cancel", "cust-1", req);
        assertNotNull(response);
        assertEquals(ReservationStatus.CANCELLED, response.status());
        assertEquals("Tôi có việc bận gia đình", response.cancellationReason());

        verify(emailService, times(1)).sendBookingCancelledEmail(
            any(Reservation.class),
            eq("Tôi có việc bận gia đình"),
            any()
        );
    }
}
