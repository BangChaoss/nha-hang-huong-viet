package com.huongViet.reservation;

import com.huongViet.reservation.dto.CreateReservationRequest;
import com.huongViet.reservation.dto.ReservationResponse;
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
            emailService
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
}
