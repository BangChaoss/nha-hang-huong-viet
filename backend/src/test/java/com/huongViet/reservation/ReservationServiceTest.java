package com.huongViet.reservation;

import com.huongViet.reservation.dto.CancelReservationRequest;
import com.huongViet.order.Order;
import com.huongViet.order.OrderRepository;
import com.huongViet.order.OrderStatus;
import com.huongViet.order.OrderType;
import com.huongViet.reservation.dto.CreateReservationRequest;
import com.huongViet.reservation.dto.ReservationResponse;
import com.huongViet.reservation.dto.UpdateReservationStatusRequest;
import com.huongViet.reservation.dto.ZoneAvailabilityResponse;
import com.huongViet.table.RestaurantTable;
import com.huongViet.table.TableRepository;
import com.huongViet.table.TableResponse;
import com.huongViet.table.TableStatus;
import com.huongViet.table.TableZone;
import com.huongViet.table.ZoneRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
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
    @Mock
    private OrderRepository orderRepository;

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
            messagingTemplate,
            orderRepository
        );

        sampleZone = new TableZone("zone-1", "Tầng trệt trong nhà", false);
        sampleTables = List.of(
            new RestaurantTable("tbl-1", "zone-1", "Bàn 01", 4),
            new RestaurantTable("tbl-2", "zone-1", "Bàn 02", 6),
            new RestaurantTable("tbl-3", "zone-1", "Bàn 03", 10)
        ); // Total capacity = 20
    }

    @Test
    void checkIn_DoesNotTakeOverTableWithSeatedGuests() {
        LocalDateTime now = LocalDateTime.now();
        Reservation reservation = new Reservation("booking", null, "Khách", "0900000000", null,
            2, now.toLocalDate(), now.toLocalTime(), null);
        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservation.setTable(sampleTables.getFirst());
        when(reservationRepository.findActiveByIdWithTableAndZone("booking")).thenReturn(Optional.of(reservation));
        when(tableRepository.lockActiveById("tbl-1")).thenReturn(Optional.of(sampleTables.getFirst()));
        when(tableRepository.hasSeatedGuests("tbl-1")).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> reservationService.updateStatus(
            "booking", new UpdateReservationStatusRequest(ReservationStatus.ARRIVED, null, null), "staff-1"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals(ReservationStatus.CONFIRMED, reservation.getStatus());
        verify(tableRepository, never()).save(any());
    }

    @Test
    void releaseAssignedTables_KeepsOccupiedTableInUse() {
        RestaurantTable table = sampleTables.getFirst();
        table.setStatus(com.huongViet.table.TableStatus.OCCUPIED);
        Reservation reservation = new Reservation();
        reservation.setTable(table);
        when(tableRepository.lockActiveById("tbl-1")).thenReturn(Optional.of(table));

        reservationService.releaseAssignedTables(reservation);

        assertEquals(com.huongViet.table.TableStatus.OCCUPIED, table.getStatus());
        verify(tableRepository, never()).save(any());
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

        // Slot 18:00 has booked seats leaving only 2 free seats (< 6 needed)
        when(reservationRepository.sumBookedPartySizeForTable(any(), eq(tomorrow), any(), any()))
            .thenAnswer(invocation -> {
                LocalTime start = invocation.getArgument(2);
                LocalTime end = invocation.getArgument(3);
                String tableId = invocation.getArgument(0);
                if (start.isBefore(requestedSlot) && end.isAfter(requestedSlot)) {
                    if ("tbl-1".equals(tableId)) return 4;
                    if ("tbl-2".equals(tableId)) return 6;
                    if ("tbl-3".equals(tableId)) return 8; // free = 2 < 6
                }
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
        // tbl-1 (cap 4) has 4 booked -> 0 free. tbl-2 (cap 6) and tbl-3 (cap 10) have 0 booked -> total free = 16
        when(reservationRepository.sumBookedPartySizeForTable(eq("tbl-1"), eq(tomorrow), any(), any()))
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
        // tbl-1 booked 4 (free 0), tbl-2 booked 6 (free 0), tbl-3 booked 8 (free 2) -> total free = 2
        when(reservationRepository.sumBookedPartySizeForTable(eq("tbl-1"), eq(tomorrow), any(), any()))
            .thenReturn(4);
        when(reservationRepository.sumBookedPartySizeForTable(eq("tbl-2"), eq(tomorrow), any(), any()))
            .thenReturn(6);
        when(reservationRepository.sumBookedPartySizeForTable(eq("tbl-3"), eq(tomorrow), any(), any()))
            .thenReturn(8);

        // Requested party size = 4 > remaining 2 -> should be marked available = false
        List<ZoneAvailabilityResponse> result = reservationService.checkAvailability(tomorrow, 4);

        assertFalse(result.get(0).timeSlots().get(0).available());
        assertEquals(2, result.get(0).timeSlots().get(0).remainingCapacity());
    }

    @Test
    @DisplayName("10b. checkAvailability_unassignedReservationDeductsZoneFreeSeats - Đơn đặt chưa gán bàn cũng lập tức trừ số ghế khả dụng của khu vực")
    void checkAvailability_unassignedReservationDeductsZoneFreeSeats() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        when(zoneRepository.findAll()).thenReturn(List.of(sampleZone));
        when(tableRepository.findAllByZoneIdAndDeletedAtIsNull("zone-1")).thenReturn(sampleTables); // capacity = 20
        // Các bàn chưa gán ai
        when(reservationRepository.sumBookedPartySizeForTable(any(), eq(tomorrow), any(), any()))
            .thenReturn(0);
        // Có đơn vừa đặt 6 khách đang ở trạng thái PENDING_HOLD (chưa gán bàn)
        when(reservationRepository.sumUnassignedPartySizeForZone(eq("zone-1"), eq(tomorrow), any(), any()))
            .thenReturn(6);

        List<ZoneAvailabilityResponse> result = reservationService.checkAvailability(tomorrow, 4);

        // Tổng sức chứa 20, đã trừ 6 chỗ của đơn chưa gán bàn -> còn 14 chỗ
        assertEquals(14, result.get(0).timeSlots().get(0).remainingCapacity());
        assertEquals(6, result.get(0).timeSlots().get(0).confirmedSeats());
        assertTrue(result.get(0).timeSlots().get(0).available());
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
        when(reservationRepository.sumBookedPartySizeForTable(any(), any(), any(), any()))
            .thenReturn(0);

        List<ZoneAvailabilityResponse> result = reservationService.checkAvailability(date, 4);

        assertNotNull(result);
        var slot13Resp = result.get(0).timeSlots().stream()
            .filter(s -> s.time().equals(slot13))
            .findFirst();
        assertTrue(slot13Resp.isPresent());
        assertTrue(slot13Resp.get().available());

        verify(reservationRepository, atLeastOnce()).sumBookedPartySizeForTable(
            eq("tbl-1"), eq(date), eq(LocalTime.of(11, 2)), eq(LocalTime.of(14, 58))
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

        when(orderRepository.findSeatedOrdersByTableIds(List.of("tbl-1")))
            .thenReturn(List.of());
        when(orderRepository.save(any(Order.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        UpdateReservationStatusRequest checkInReq = new UpdateReservationStatusRequest(
            ReservationStatus.ARRIVED, null, null, null
        );

        ReservationResponse response = reservationService.updateStatus("res-valid", checkInReq, "staff-1");
        assertNotNull(response);
        assertEquals(ReservationStatus.ARRIVED, response.status());

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        Order createdOrder = orderCaptor.getValue();
        assertEquals("tbl-1", createdOrder.getTableId());
        assertEquals("staff-1", createdOrder.getStaffId());
        assertEquals(OrderType.DINE_IN, createdOrder.getOrderType());
        assertEquals(OrderStatus.IN_PROGRESS, createdOrder.getStatus());
        assertEquals(4, createdOrder.getPartySize());
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

    @Test
    @DisplayName("22. cancelByCustomer_guestCancelsOwnWalkInBooking_success - Khách vãng lai tự hủy đơn của chính mình thành công")
    void cancelByCustomer_guestCancelsOwnWalkInBooking_success() {
        Reservation guestReservation = new Reservation(
            "res-guest-cancel", null, "Khách Vãng Lai", "0987654321", "guest@example.com", 2,
            LocalDate.now().plusDays(2), LocalTime.of(19, 0), null, "zone-1"
        );
        guestReservation.setStatus(ReservationStatus.PENDING_HOLD);

        when(reservationRepository.findActiveByIdWithTableAndZone("res-guest-cancel"))
            .thenReturn(Optional.of(guestReservation));
        when(reservationRepository.save(any(Reservation.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        CancelReservationRequest req = new CancelReservationRequest("Thay đổi kế hoạch", "0987654321");

        ReservationResponse response = reservationService.cancelByCustomer("res-guest-cancel", null, req);
        assertNotNull(response);
        assertEquals(ReservationStatus.CANCELLED, response.status());
        assertEquals("Thay đổi kế hoạch", response.cancellationReason());

        // Xác nhận lưu ReservationAudit với changedBy = null để không vi phạm FK
        verify(reservationAuditRepository, times(1)).save(argThat(audit ->
            audit.getChangedBy() == null &&
            audit.getReason().contains("[Khách vãng lai]")
        ));
    }

    @Test
    @DisplayName("23. cancelByCustomer_guestAttemptsToCancelMemberBooking_throwsForbidden - Khách không thể hủy đơn của tài khoản thành viên khác")
    void cancelByCustomer_guestAttemptsToCancelMemberBooking_throwsForbidden() {
        Reservation memberReservation = new Reservation(
            "res-member-booking", "cust-owner", "Chủ tài khoản", "0987654321", "owner@example.com", 2,
            LocalDate.now().plusDays(2), LocalTime.of(19, 0), null, "zone-1"
        );
        memberReservation.setStatus(ReservationStatus.PENDING_HOLD);

        when(reservationRepository.findActiveByIdWithTableAndZone("res-member-booking"))
            .thenReturn(Optional.of(memberReservation));

        CancelReservationRequest req = new CancelReservationRequest("Hủy đơn", "0987654321");

        // Gọi từ khách chưa đăng nhập (customerId = null)
        ResponseStatusException ex1 = assertThrows(ResponseStatusException.class, () ->
            reservationService.cancelByCustomer("res-member-booking", null, req)
        );
        assertEquals(HttpStatus.FORBIDDEN, ex1.getStatusCode());

        // Hoặc gọi từ tài khoản khác (customerId = "cust-other")
        ResponseStatusException ex2 = assertThrows(ResponseStatusException.class, () ->
            reservationService.cancelByCustomer("res-member-booking", "cust-other", req)
        );
        assertEquals(HttpStatus.FORBIDDEN, ex2.getStatusCode());
    }

    @Test
    @DisplayName("24. test_availability_tableAware - Tính toán khả dụng chính xác theo từng bàn (Table-Aware)")
    void test_availability_tableAware() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        when(zoneRepository.findAll()).thenReturn(List.of(sampleZone));
        when(tableRepository.findAllByZoneIdAndDeletedAtIsNull("zone-1")).thenReturn(sampleTables);
        // sampleTables: tbl-1 (cap 4), tbl-2 (cap 6), tbl-3 (cap 10)
        // tbl-1 booked 2 -> free 2
        // tbl-2 booked 6 -> free 0
        // tbl-3 booked 0 -> free 10
        // Total free = 12
        when(reservationRepository.sumBookedPartySizeForTable(eq("tbl-1"), eq(tomorrow), any(), any()))
            .thenReturn(2);
        when(reservationRepository.sumBookedPartySizeForTable(eq("tbl-2"), eq(tomorrow), any(), any()))
            .thenReturn(6);
        when(reservationRepository.sumBookedPartySizeForTable(eq("tbl-3"), eq(tomorrow), any(), any()))
            .thenReturn(0);

        List<ZoneAvailabilityResponse> result = reservationService.checkAvailability(tomorrow, 10);
        assertNotNull(result);
        assertEquals(1, result.size());
        var slot = result.get(0).timeSlots().get(0);
        assertEquals(12, slot.remainingCapacity());
        assertTrue(slot.available(), "Đoàn 10 người phải khả dụng khi còn 12 chỗ trống");

        List<ZoneAvailabilityResponse> resultOver = reservationService.checkAvailability(tomorrow, 13);
        assertFalse(resultOver.get(0).timeSlots().get(0).available(), "Đoàn 13 người không khả dụng khi chỉ còn 12 chỗ trống");
    }

    @Test
    @DisplayName("25. test_sharedSeating_sameTable - Cho phép xếp 2 đoàn vào cùng 1 bàn nếu còn đủ ghế (Shared Seating)")
    void test_sharedSeating_sameTable() {
        Reservation reservation = new Reservation(
            "res-shared-1", null, "Khách đoàn 2 người", "0911223344", null, 2,
            LocalDate.now().plusDays(1), LocalTime.of(18, 0), null, "zone-1"
        );
        reservation.setStatus(ReservationStatus.PENDING_HOLD);

        RestaurantTable t1 = new RestaurantTable("tbl-1", "zone-1", "Bàn 01", 4);

        when(reservationRepository.findActiveByIdWithTableAndZone("res-shared-1"))
            .thenReturn(Optional.of(reservation));
        when(tableRepository.findAllById(List.of("tbl-1")))
            .thenReturn(List.of(t1));
        // Bàn 4 chỗ đã có đơn khác ngồi 2 người -> còn 2 chỗ trống >= 2
        when(reservationRepository.sumBookedPartySizeForTable(eq("tbl-1"), any(), any(), any()))
            .thenReturn(2);
        when(reservationRepository.save(any(Reservation.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        UpdateReservationStatusRequest req = new UpdateReservationStatusRequest(
            ReservationStatus.CONFIRMED, null, List.of("tbl-1"), null
        );

        ReservationResponse response = reservationService.updateStatus("res-shared-1", req, "staff-1");
        assertNotNull(response);
        assertEquals(ReservationStatus.CONFIRMED, response.status());
        assertEquals("tbl-1", response.tableId());
    }

    @Test
    @DisplayName("26. test_sharedSeating_exceedCapacity - Từ chối xếp thêm khách vào bàn nếu vượt quá ghế trống còn lại")
    void test_sharedSeating_exceedCapacity() {
        Reservation reservation = new Reservation(
            "res-shared-over", null, "Khách đoàn 2 người", "0911223344", null, 2,
            LocalDate.now().plusDays(1), LocalTime.of(18, 0), null, "zone-1"
        );
        reservation.setStatus(ReservationStatus.PENDING_HOLD);

        RestaurantTable t1 = new RestaurantTable("tbl-1", "zone-1", "Bàn 01", 4);

        when(reservationRepository.findActiveByIdWithTableAndZone("res-shared-over"))
            .thenReturn(Optional.of(reservation));
        when(tableRepository.findAllById(List.of("tbl-1")))
            .thenReturn(List.of(t1));
        // Bàn 4 chỗ đã có đơn khác ngồi 3 người -> chỉ còn 1 chỗ trống < 2
        when(reservationRepository.sumBookedPartySizeForTable(eq("tbl-1"), any(), any(), any()))
            .thenReturn(3);

        UpdateReservationStatusRequest req = new UpdateReservationStatusRequest(
            ReservationStatus.CONFIRMED, null, List.of("tbl-1"), null
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            reservationService.updateStatus("res-shared-over", req, "staff-1")
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("1 chỗ"));
        assertTrue(ex.getReason().contains("không đủ cho đoàn 2"));
    }

    @Test
    @DisplayName("27. test_availableTables_includesShared - Danh sách bàn gợi ý bao gồm bàn chia sẻ kèm availableSeats chính xác")
    void test_availableTables_includesShared() {
        Reservation reservation = new Reservation(
            "res-find-tables", null, "Khách 2 người", "0911223344", null, 2,
            LocalDate.now().plusDays(1), LocalTime.of(18, 0), null, "zone-1"
        );

        RestaurantTable t1 = new RestaurantTable("tbl-1", "zone-1", "Bàn 01", 4);
        t1.setZone(sampleZone);

        when(reservationRepository.findActiveByIdWithTableAndZone("res-find-tables"))
            .thenReturn(Optional.of(reservation));
        when(tableRepository.findAllActiveWithZone()).thenReturn(List.of(t1));
        // tbl-1 đã booked 2 chỗ -> còn 2 chỗ trống >= 2
        when(reservationRepository.sumBookedPartySizeForTable(eq("tbl-1"), any(), any(), any()))
            .thenReturn(2);
        when(reservationRepository.findOverlappingReservationsForTable(eq("tbl-1"), any(), any(), any()))
            .thenReturn(List.of());

        List<TableResponse> availableTables = reservationService.getAvailableTablesForReservation("res-find-tables");
        assertNotNull(availableTables);
        assertEquals(1, availableTables.size());
        assertEquals("tbl-1", availableTables.get(0).id());
        assertEquals(4, availableTables.get(0).capacity());
        assertEquals(2, availableTables.get(0).availableSeats());
    }

    @Test
    @DisplayName("27b. test_availableTables_returnsModularTablesForLargeGroup - Đoàn 6 khách nhận được các bàn modular còn chỗ để gộp bàn")
    void test_availableTables_returnsModularTablesForLargeGroup() {
        Reservation reservation = new Reservation(
            "res-merge-6", null, "Đoàn 6 người", "0911223344", null, 6,
            LocalDate.now().plusDays(1), LocalTime.of(18, 30), null, "zone-indoor"
        );

        RestaurantTable t03A = new RestaurantTable("tbl-3a", "zone-indoor", "Bàn 03A", 2);
        t03A.setZone(sampleZone);
        RestaurantTable t03B = new RestaurantTable("tbl-3b", "zone-indoor", "Bàn 03B", 4);
        t03B.setZone(sampleZone);

        when(reservationRepository.findActiveByIdWithTableAndZone("res-merge-6"))
            .thenReturn(Optional.of(reservation));
        when(tableRepository.findAllActiveWithZone()).thenReturn(List.of(t03A, t03B));
        when(reservationRepository.sumBookedPartySizeForTable(any(), any(), any(), any()))
            .thenReturn(0);
        when(reservationRepository.findOverlappingReservationsForTable(any(), any(), any(), any()))
            .thenReturn(List.of());

        List<TableResponse> availableTables = reservationService.getAvailableTablesForReservation("res-merge-6");
        assertNotNull(availableTables);
        assertEquals(2, availableTables.size());
        assertEquals("tbl-3a", availableTables.get(0).id());
        assertEquals(2, availableTables.get(0).availableSeats());
        assertEquals("tbl-3b", availableTables.get(1).id());
        assertEquals(4, availableTables.get(1).availableSeats());
    }

    @Test
    @DisplayName("28. test_vip_createBooking_pendingDeposit - Đặt bàn khu VIP tạo đơn PENDING_DEPOSIT và không gửi mail ngay")
    void test_vip_createBooking_pendingDeposit() {
        TableZone vipZone = new TableZone("zone-vip", "Phòng VIP Hoàng Gia", false);
        vipZone.setIsVip(true);

        LocalDate tomorrow = LocalDate.now().plusDays(1);
        LocalTime slot = LocalTime.of(19, 0);
        CreateReservationRequest request = new CreateReservationRequest(
            "Khách VIP",
            "0909999999",
            "vip@example.com",
            tomorrow,
            slot,
            4,
            "zone-vip",
            "Cần phòng riêng yên tĩnh"
        );

        when(zoneRepository.findById("zone-vip")).thenReturn(Optional.of(vipZone));
        when(tableRepository.findAllByZoneIdAndDeletedAtIsNull("zone-vip"))
            .thenReturn(List.of(new RestaurantTable("tbl-vip-1", "zone-vip", "VIP 01", 10)));
        when(reservationRepository.sumBookedPartySizeForTable(any(), any(), any(), any()))
            .thenReturn(0);
        when(reservationRepository.save(any(Reservation.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        ReservationResponse response = reservationService.createBooking(request, null);

        assertNotNull(response);
        assertEquals(ReservationStatus.PENDING_DEPOSIT, response.status());
        assertEquals(new BigDecimal("250000"), response.depositAmount());
        assertNotNull(response.createdAt());
        // BR-VIP: Không gửi email xác nhận đặt bàn trước khi cọc thành công
        verify(emailService, never()).sendBookingConfirmation(any(Reservation.class), anyString());
    }

    @Test
    @DisplayName("29. test_vip_confirmDeposit_success - Xác nhận cọc VIP chuyển đơn sang PENDING_HOLD và gửi mail xác nhận")
    void test_vip_confirmDeposit_success() {
        TableZone vipZone = new TableZone("zone-vip", "Phòng VIP Hoàng Gia", false);
        vipZone.setIsVip(true);

        Reservation vipReservation = new Reservation(
            "res-vip-1", null, "Khách VIP", "0909999999", "vip@example.com", 4,
            LocalDate.now().plusDays(1), LocalTime.of(19, 0), null, "zone-vip"
        );
        vipReservation.setZone(vipZone);
        vipReservation.setStatus(ReservationStatus.PENDING_DEPOSIT);
        vipReservation.setDepositAmount(new BigDecimal("250000"));

        when(reservationRepository.findActiveByIdWithTableAndZone("res-vip-1"))
            .thenReturn(Optional.of(vipReservation));
        when(reservationRepository.save(any(Reservation.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        ReservationResponse response = reservationService.confirmDeposit("res-vip-1", "VNP14829304");

        assertNotNull(response);
        assertEquals(ReservationStatus.PENDING_HOLD, response.status());
        verify(reservationAuditRepository, times(1)).save(argThat(audit ->
            audit.getOldStatus() == ReservationStatus.PENDING_DEPOSIT &&
            audit.getNewStatus() == ReservationStatus.PENDING_HOLD &&
            audit.getReason().contains("VNP14829304")
        ));
        verify(emailService, times(1)).sendBookingConfirmation(any(Reservation.class), eq("Phòng VIP Hoàng Gia"));
    }

    @Test
    @DisplayName("30. test_vip_confirmDeposit_wrongStatus_throwsBadRequest - Xác nhận cọc khi đơn không ở PENDING_DEPOSIT quăng lỗi 400")
    void test_vip_confirmDeposit_wrongStatus_throwsBadRequest() {
        Reservation reservation = new Reservation(
            "res-confirmed", null, "Khách thường", "0901112222", null, 2,
            LocalDate.now().plusDays(1), LocalTime.of(18, 0), null, "zone-1"
        );
        reservation.setStatus(ReservationStatus.CONFIRMED);

        when(reservationRepository.findActiveByIdWithTableAndZone("res-confirmed"))
            .thenReturn(Optional.of(reservation));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            reservationService.confirmDeposit("res-confirmed", "VNP999999")
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("chờ thanh toán phí thu & cọc phòng VIP"));
    }

    @Test
    @DisplayName("31. confirm_moreThan2HoursBefore_doesNotLockTableReserved - Xác nhận đơn trước > 2 tiếng chưa khóa bàn RESERVED")
    void confirm_moreThan2HoursBefore_doesNotLockTableReserved() {
        LocalDateTime later = LocalDateTime.now().plusHours(3);
        Reservation res = new Reservation(
            "res-3h", null, "Khách Tối", "0901112233", null, 4,
            later.toLocalDate(), later.toLocalTime(), null, "zone-1"
        );
        res.setStatus(ReservationStatus.PENDING_HOLD);

        RestaurantTable t1 = new RestaurantTable("tbl-1", "zone-1", "Bàn 01", 4);
        t1.setStatus(TableStatus.AVAILABLE);

        when(reservationRepository.findActiveByIdWithTableAndZone("res-3h")).thenReturn(Optional.of(res));
        when(tableRepository.findAllById(List.of("tbl-1"))).thenReturn(List.of(t1));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(i -> i.getArgument(0));

        ReservationResponse resp = reservationService.updateStatus(
            "res-3h", new UpdateReservationStatusRequest(ReservationStatus.CONFIRMED, "tbl-1", null), "staff-1"
        );

        assertEquals(ReservationStatus.CONFIRMED, resp.status());
        assertEquals(TableStatus.AVAILABLE, t1.getStatus());
        verify(tableRepository, never()).save(t1);
    }

    @Test
    @DisplayName("32. confirm_within2Hours_locksTableReserved - Xác nhận đơn trong vòng <= 2 tiếng lập tức khóa bàn RESERVED")
    void confirm_within2Hours_locksTableReserved() {
        LocalDateTime soon = LocalDateTime.now().plusMinutes(60);
        Reservation res = new Reservation(
            "res-1h", null, "Khách Sắp Đến", "0901112233", null, 4,
            soon.toLocalDate(), soon.toLocalTime(), null, "zone-1"
        );
        res.setStatus(ReservationStatus.PENDING_HOLD);

        RestaurantTable t1 = new RestaurantTable("tbl-1", "zone-1", "Bàn 01", 4);
        t1.setStatus(TableStatus.AVAILABLE);

        when(reservationRepository.findActiveByIdWithTableAndZone("res-1h")).thenReturn(Optional.of(res));
        when(tableRepository.findAllById(List.of("tbl-1"))).thenReturn(List.of(t1));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(i -> i.getArgument(0));

        ReservationResponse resp = reservationService.updateStatus(
            "res-1h", new UpdateReservationStatusRequest(ReservationStatus.CONFIRMED, "tbl-1", null), "staff-1"
        );

        assertEquals(ReservationStatus.CONFIRMED, resp.status());
        assertEquals(TableStatus.RESERVED, t1.getStatus());
        verify(tableRepository).save(t1);
    }

    @Test
    @DisplayName("33. checkIn_sharedSeatingTwoReservations_createsSecondLinkedOrder - Hai đơn đặt bàn ngồi ghép cùng bàn đều check-in được")
    void checkIn_sharedSeatingTwoReservations_createsSecondLinkedOrder() {
        LocalDateTime now = LocalDateTime.now();
        Reservation res2 = new Reservation(
            "res-shared-2", null, "Khách 2", "0902223333", null, 2,
            now.toLocalDate(), now.toLocalTime(), null, "zone-1"
        );
        res2.setStatus(ReservationStatus.CONFIRMED);
        res2.setTableId("tbl-1");

        RestaurantTable t1 = new RestaurantTable("tbl-1", "zone-1", "Bàn 01", 4);
        t1.setStatus(TableStatus.OCCUPIED);

        Order existingOrder = new Order("ord-1", "tbl-1", "staff-1", OrderType.DINE_IN);
        existingOrder.setReservationId("res-shared-1");
        existingOrder.setPartySize(2);

        when(reservationRepository.findActiveByIdWithTableAndZone("res-shared-2")).thenReturn(Optional.of(res2));
        when(tableRepository.lockActiveById("tbl-1")).thenReturn(Optional.of(t1));
        when(orderRepository.findSeatedOrdersByTableIds(List.of("tbl-1"))).thenReturn(List.of(existingOrder));
        when(orderRepository.findFirstByReservationIdAndStatusOrderByCreatedAtDesc("res-shared-2", OrderStatus.IN_PROGRESS))
            .thenReturn(Optional.empty());
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(i -> i.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        ReservationResponse resp = reservationService.updateStatus(
            "res-shared-2", new UpdateReservationStatusRequest(ReservationStatus.ARRIVED, null, null), "staff-1"
        );

        assertEquals(ReservationStatus.ARRIVED, resp.status());
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertEquals("res-shared-2", orderCaptor.getValue().getReservationId());
        assertEquals(2, orderCaptor.getValue().getPartySize());
    }
}
