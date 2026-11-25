package com.huongViet.reservation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.huongViet.config.GlobalExceptionHandler;
import com.huongViet.payment.PaymentService;
import com.huongViet.reservation.dto.CreateReservationRequest;
import com.huongViet.reservation.dto.ReservationResponse;
import com.huongViet.reservation.dto.TimeSlotAvailability;
import com.huongViet.reservation.dto.ZoneAvailabilityResponse;
import com.huongViet.table.ZoneResponse;
import com.huongViet.table.ZoneService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ReservationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ReservationService reservationService;

    @Mock
    private ZoneService zoneService;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private ReservationPublicController reservationPublicController;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(reservationPublicController, "frontendUrl", "http://localhost:8081");
        mockMvc = MockMvcBuilders
            .standaloneSetup(reservationPublicController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("POST /api/reservations - Thành công trả về 201 Created")
    void createBooking_Success() throws Exception {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        CreateReservationRequest request = new CreateReservationRequest(
            "Nguyễn Văn A",
            "0912345678",
            "nguyenvana@gmail.com",
            tomorrow,
            LocalTime.of(18, 0),
            4,
            "zone-1",
            "Bàn cạnh cửa sổ"
        );

        ReservationResponse mockResponse = new ReservationResponse(
            "res-uuid-1",
            "Nguyễn Văn A",
            "0912345678",
            "nguyenvana@gmail.com",
            4,
            tomorrow,
            LocalTime.of(18, 0),
            "Bàn cạnh cửa sổ",
            ReservationStatus.PENDING_HOLD,
            "zone-1",
            "Tầng trệt trong nhà",
            false,
            null,
            null,
            null,
            null,
            LocalDateTime.now()
        );

        when(reservationService.createBooking(any(CreateReservationRequest.class), any()))
            .thenReturn(mockResponse);

        mockMvc.perform(post("/api/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value("res-uuid-1"))
            .andExpect(jsonPath("$.guestName").value("Nguyễn Văn A"))
            .andExpect(jsonPath("$.status").value("PENDING_HOLD"));
    }

    @Test
    @DisplayName("POST /api/reservations - Lỗi validation khi tên rỗng trả về 400")
    void createBooking_ValidationError_EmptyName() throws Exception {
        CreateReservationRequest invalidRequest = new CreateReservationRequest(
            "", // Blank name
            "0912345678",
            null,
            LocalDate.now().plusDays(1),
            LocalTime.of(18, 0),
            4,
            "zone-1",
            null
        );

        mockMvc.perform(post("/api/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/reservations/availability - Trả về 200 OK với danh sách slot")
    void checkAvailability_Success() throws Exception {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        List<ZoneAvailabilityResponse> mockAvailability = List.of(
            new ZoneAvailabilityResponse(
                "zone-1",
                "Tầng trệt trong nhà",
                false,
                20,
                List.of(new TimeSlotAvailability(LocalTime.of(18, 0), "18:00", 0, 20, true))
            )
        );

        when(reservationService.checkAvailability(eq(tomorrow), eq(4))).thenReturn(mockAvailability);

        mockMvc.perform(get("/api/reservations/availability")
                .param("date", tomorrow.toString())
                .param("partySize", "4"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].zoneId").value("zone-1"))
            .andExpect(jsonPath("$[0].totalCapacity").value(20));
    }

    @Test
    @DisplayName("GET /api/reservations/time-slots - Trả về danh sách khung giờ")
    void getTimeSlots_Success() throws Exception {
        when(reservationService.getTimeSlots()).thenReturn(List.of("11:00", "11:30", "18:00"));

        mockMvc.perform(get("/api/reservations/time-slots"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0]").value("11:00"))
            .andExpect(jsonPath("$[2]").value("18:00"));
    }

    @Test
    @DisplayName("GET /api/reservations/zones - Trả về danh sách khu vực")
    void getZones_Success() throws Exception {
        when(zoneService.listAll()).thenReturn(List.of(new ZoneResponse("zone-1", "Sân vườn", false, 0L)));

        mockMvc.perform(get("/api/reservations/zones"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].zoneName").value("Sân vườn"));
    }

    @Test
    @DisplayName("GET /api/reservations/guest-lookup - Tra cứu thành công khi có đủ phone và code")
    void guestLookup_Success() throws Exception {
        when(reservationService.getGuestBookings("0912345678", "RES-1234")).thenReturn(List.of());

        mockMvc.perform(get("/api/reservations/guest-lookup")
                .param("phone", "0912345678")
                .param("code", "RES-1234"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/reservations/guest-lookup - Lỗi 400 khi thiếu mã đặt bàn")
    void guestLookup_MissingCode_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/reservations/guest-lookup")
                .param("phone", "0912345678"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/reservations/guest-lookup - Lỗi 400 khi thiếu số điện thoại")
    void guestLookup_MissingPhone_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/reservations/guest-lookup")
                .param("code", "RES-1234"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/reservations/{id}/deposit - Truyền returnPath=history sang PaymentService")
    void createDepositPayment_WithReturnPathHistory() throws Exception {
        Reservation vip = new Reservation(
            "res-vip-1", null, "Khách VIP", "0909999999", "vip@example.com", 4,
            LocalDate.now().plusDays(1), LocalTime.of(19, 0), null, "zone-vip"
        );
        vip.setStatus(ReservationStatus.PENDING_DEPOSIT);
        vip.setDepositAmount(new BigDecimal("250000"));

        when(reservationRepository.findActiveByIdWithTableAndZone("res-vip-1")).thenReturn(Optional.of(vip));
        when(paymentService.createReservationDepositPayment(
            eq("res-vip-1"), eq(new BigDecimal("250000")), any(), eq("history")))
            .thenReturn(Map.of("paymentUrl", "https://sandbox.vnpayment.vn/pay?test=1", "txnRef", "DEP_res-vip-1_123"));

        mockMvc.perform(post("/api/reservations/res-vip-1/deposit")
                .param("returnPath", "history"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paymentUrl").value("https://sandbox.vnpayment.vn/pay?test=1"));

        verify(paymentService).createReservationDepositPayment(
            eq("res-vip-1"), eq(new BigDecimal("250000")), any(), eq("history")
        );
    }

    @Test
    @DisplayName("GET /api/reservations/deposit-callback - returnPath=history loại returnPath khỏi verify và redirect về ?tab=history")
    void depositCallback_ReturnPathHistory_RedirectsToHistoryTab() throws Exception {
        when(paymentService.verify(argThat(map -> map != null && !map.containsKey("returnPath") && map.containsKey("vnp_TxnRef"))))
            .thenReturn(true);

        mockMvc.perform(get("/api/reservations/deposit-callback")
                .param("returnPath", "history")
                .param("vnp_TxnRef", "DEP_res-vip-1_1700000")
                .param("vnp_ResponseCode", "00")
                .param("vnp_TransactionStatus", "00")
                .param("vnp_TransactionNo", "VNP123"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("http://localhost:8081/?tab=history&reservationId=res-vip-1&depositStatus=success"));

        verify(reservationService).confirmDeposit("res-vip-1", "VNP123");
    }

    @Test
    @DisplayName("GET /api/reservations/deposit-callback - mặc định hoặc returnPath=book redirect về ?tab=book")
    void depositCallback_ReturnPathBook_RedirectsToBookTab() throws Exception {
        when(paymentService.verify(argThat(map -> map != null && !map.containsKey("returnPath"))))
            .thenReturn(true);

        mockMvc.perform(get("/api/reservations/deposit-callback")
                .param("returnPath", "book")
                .param("vnp_TxnRef", "DEP_res-vip-2_1700000")
                .param("vnp_ResponseCode", "00")
                .param("vnp_TransactionStatus", "00")
                .param("vnp_TransactionNo", "VNP456"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("http://localhost:8081/?tab=book&reservationId=res-vip-2&depositStatus=success"));
    }

    @Test
    @DisplayName("GET /api/reservations/deposit-callback - thất bại với returnPath=history redirect về ?tab=history&depositStatus=failed")
    void depositCallback_FailedWithHistory_RedirectsToHistoryTabWithFailed() throws Exception {
        when(paymentService.verify(any())).thenReturn(false);

        mockMvc.perform(get("/api/reservations/deposit-callback")
                .param("returnPath", "history")
                .param("vnp_TxnRef", "DEP_res-vip-3_1700000")
                .param("vnp_ResponseCode", "24")
                .param("vnp_TransactionStatus", "02"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("http://localhost:8081/?tab=history&reservationId=res-vip-3&depositStatus=failed"));
    }
}
