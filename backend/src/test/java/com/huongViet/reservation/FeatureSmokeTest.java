package com.huongViet.reservation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.huongViet.auth.User;
import com.huongViet.auth.UserRole;
import com.huongViet.config.GlobalExceptionHandler;
import com.huongViet.reservation.dto.CancelReservationRequest;
import com.huongViet.reservation.dto.ReservationResponse;
import com.huongViet.reservation.dto.TimeSlotAvailability;
import com.huongViet.reservation.dto.ZoneAvailabilityResponse;

import com.huongViet.reservation.dto.UpdateReservationStatusRequest;
import com.huongViet.table.TableResponse;
import com.huongViet.table.TableStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FeatureSmokeTest {

    private MockMvc mockAdminMvc;
    private MockMvc mockPublicMvc;

    @Mock
    private ReservationService reservationService;

    @InjectMocks
    private ReservationAdminController reservationAdminController;

    @Mock
    private com.huongViet.table.ZoneService zoneService;

    @InjectMocks
    private ReservationPublicController reservationPublicController;

    private ObjectMapper objectMapper;

    private User mockStaffUser;
    private User mockCustomerUser;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        mockStaffUser = new User(
            "staff-uuid-1", "staff1", "staff1@huongviet.com", "0900000001", "hash", UserRole.STAFF
        );

        mockCustomerUser = new User(
            "cust-uuid-1", "customer1", "cust1@huongviet.com", "0900000002", "hash", UserRole.CUSTOMER
        );

        // Handler to resolve @AuthenticationPrincipal User for Admin controller
        HandlerMethodArgumentResolver staffPrincipalResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter,
                                          ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest,
                                          WebDataBinderFactory binderFactory) {
                return mockStaffUser;
            }
        };

        // Handler to resolve @AuthenticationPrincipal User for Public controller
        HandlerMethodArgumentResolver customerPrincipalResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter,
                                          ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest,
                                          WebDataBinderFactory binderFactory) {
                String authHeader = webRequest.getHeader("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer valid-token")) {
                    return mockCustomerUser;
                }
                return null;
            }
        };

        mockAdminMvc = MockMvcBuilders
            .standaloneSetup(reservationAdminController)
            .setCustomArgumentResolvers(staffPrincipalResolver)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

        mockPublicMvc = MockMvcBuilders
            .standaloneSetup(reservationPublicController)
            .setCustomArgumentResolvers(customerPrincipalResolver)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    @DisplayName("UC09 Smoke: GET /api/admin/reservations?date=&status= returns 200 with list")
    void testAdminListByDateAndStatus() throws Exception {
        LocalDate date = LocalDate.of(2026, 10, 23);
        ReservationResponse res = new ReservationResponse(
            "res-1", "Nguyễn Văn A", "0912345678", "a@test.com", 4,
            date, LocalTime.of(18, 0), null,
            ReservationStatus.PENDING_HOLD, "z1", "Trong Nhà", false,
            null, null, null, null, LocalDateTime.now()
        );

        when(reservationService.listByDate(eq(date), eq(ReservationStatus.PENDING_HOLD)))
            .thenReturn(List.of(res));

        mockAdminMvc.perform(get("/api/admin/reservations")
                .param("date", "2026-10-23")
                .param("status", "PENDING_HOLD")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value("res-1"))
            .andExpect(jsonPath("$[0].status").value("PENDING_HOLD"));
    }

    @Test
    @DisplayName("UC10 Smoke: GET /api/admin/reservations/{id}/available-tables returns 200 with tables")
    void testAdminGetAvailableTables() throws Exception {
        TableResponse tbl = new TableResponse(
            "tbl-1", "B01", 4, TableStatus.AVAILABLE, "z1", "Trong Nhà", false
        );

        when(reservationService.getAvailableTablesForReservation("res-1"))
            .thenReturn(List.of(tbl));

        mockAdminMvc.perform(get("/api/admin/reservations/res-1/available-tables")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value("tbl-1"))
            .andExpect(jsonPath("$[0].tableNumber").value("B01"))
            .andExpect(jsonPath("$[0].capacity").value(4));
    }

    @Test
    @DisplayName("UC10 Smoke: PATCH /api/admin/reservations/{id}/status confirms and assigns table")
    void testAdminConfirmAndAssignTable() throws Exception {
        UpdateReservationStatusRequest req = new UpdateReservationStatusRequest(
            ReservationStatus.CONFIRMED, "tbl-1", "Đã gọi điện xác nhận"
        );

        ReservationResponse res = new ReservationResponse(
            "res-1", "Nguyễn Văn A", "0912345678", "a@test.com", 4,
            LocalDate.of(2026, 10, 23), LocalTime.of(18, 0), null,
            ReservationStatus.CONFIRMED, "z1", "Trong Nhà", false,
            "tbl-1", "B01", null, null, LocalDateTime.now()
        );

        when(reservationService.updateStatus(eq("res-1"), any(UpdateReservationStatusRequest.class), any()))
            .thenReturn(res);

        mockAdminMvc.perform(patch("/api/admin/reservations/res-1/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CONFIRMED"))
            .andExpect(jsonPath("$.tableNumber").value("B01"));
    }

    @Test
    @DisplayName("UC13 Smoke: PATCH /api/admin/reservations/{id}/status check-in sets status to ARRIVED")
    void testAdminCheckInArrived() throws Exception {
        UpdateReservationStatusRequest req = new UpdateReservationStatusRequest(
            ReservationStatus.ARRIVED, null, null
        );

        ReservationResponse res = new ReservationResponse(
            "res-1", "Nguyễn Văn A", "0912345678", "a@test.com", 4,
            LocalDate.of(2026, 10, 23), LocalTime.of(18, 0), null,
            ReservationStatus.ARRIVED, "z1", "Trong Nhà", false,
            "tbl-1", "B01", null, null, LocalDateTime.now()
        );

        when(reservationService.updateStatus(eq("res-1"), any(UpdateReservationStatusRequest.class), any()))
            .thenReturn(res);

        mockAdminMvc.perform(patch("/api/admin/reservations/res-1/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ARRIVED"));
    }

    @Test
    @DisplayName("UC12 Smoke: POST /api/reservations/{id}/cancel without token returns 401 UNAUTHORIZED")
    void testCustomerCancelUnauthenticated() throws Exception {
        mockPublicMvc.perform(post("/api/reservations/res-1/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CancelReservationRequest("Bận đột xuất"))))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("UC12 Smoke: POST /api/reservations/{id}/cancel with valid token returns 200 CANCELLED")
    void testCustomerCancelAuthenticated() throws Exception {
        ReservationResponse res = new ReservationResponse(
            "res-1", "Nguyễn Văn A", "0912345678", "a@test.com", 4,
            LocalDate.of(2026, 10, 23), LocalTime.of(18, 0), null,
            ReservationStatus.CANCELLED, "z1", "Trong Nhà", false,
            null, null, null, "Bận đột xuất", LocalDateTime.now()
        );

        when(reservationService.cancelByCustomer(eq("res-1"), eq("cust-uuid-1"), any(CancelReservationRequest.class)))
            .thenReturn(res);

        mockPublicMvc.perform(post("/api/reservations/res-1/cancel")
                .header("Authorization", "Bearer valid-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CancelReservationRequest("Bận đột xuất"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"))
            .andExpect(jsonPath("$.cancellationReason").value("Bận đột xuất"));
    }

    @Test
    @DisplayName("Req 1 Smoke: GET /api/reservations/availability returns 200 with capacity pool")
    void testAvailabilityEndpoint() throws Exception {
        LocalDate date = LocalDate.of(2026, 10, 25);
        ZoneAvailabilityResponse zoneAvail = new ZoneAvailabilityResponse(
            "zone-1", "Trong Nhà", false, 20,
            List.of(new TimeSlotAvailability(LocalTime.of(18, 0), "18:00", 4, 16, true))
        );

        when(reservationService.checkAvailability(eq(date), eq(4)))
            .thenReturn(List.of(zoneAvail));

        mockPublicMvc.perform(get("/api/reservations/availability")
                .param("date", "2026-10-25")
                .param("partySize", "4"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].zoneId").value("zone-1"))
            .andExpect(jsonPath("$[0].timeSlots[0].remainingCapacity").value(16));
    }

    @Test
    @DisplayName("Req 4 Smoke: GET /api/reservations/guest-lookup with both phone and code returns 200")
    void testGuestLookupWithBothPhoneAndCode() throws Exception {
        ReservationResponse res = new ReservationResponse(
            "res-1", "Khách Test", "0912345678", "guest@test.com", 2,
            LocalDate.of(2026, 10, 25), LocalTime.of(19, 0), null,
            ReservationStatus.CONFIRMED, "zone-1", "Sân Vườn", false,
            "tbl-1", "B01", null, null, LocalDateTime.now()
        );

        when(reservationService.getGuestBookings("0912345678", "RES-ABC1234"))
            .thenReturn(List.of(res));

        mockPublicMvc.perform(get("/api/reservations/guest-lookup")
                .param("phone", "0912345678")
                .param("code", "RES-ABC1234"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value("res-1"))
            .andExpect(jsonPath("$[0].zoneName").value("Sân Vườn"));
    }

    @Test
    @DisplayName("Req 4 Smoke: GET /api/reservations/guest-lookup missing code returns 400 Bad Request")
    void testGuestLookupMissingCode() throws Exception {
        mockPublicMvc.perform(get("/api/reservations/guest-lookup")
                .param("phone", "0912345678"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Req 6 Smoke: POST /api/reservations/{id}/cancel by unauthenticated guest with phone returns 200")
    void testGuestCancelWithPhoneSuccess() throws Exception {
        CancelReservationRequest req = new CancelReservationRequest("0912345678", "Bận việc gia đình");
        ReservationResponse res = new ReservationResponse(
            "res-1", "Khách Test", "0912345678", "guest@test.com", 2,
            LocalDate.of(2026, 10, 25), LocalTime.of(19, 0), null,
            ReservationStatus.CANCELLED, "zone-1", "Sân Vườn", false,
            null, null, null, "Bận việc gia đình", LocalDateTime.now()
        );

        when(reservationService.cancelByCustomer(eq("res-1"), eq(null), any(CancelReservationRequest.class)))
            .thenReturn(res);

        mockPublicMvc.perform(post("/api/reservations/res-1/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("Req 6 Smoke: POST /api/reservations/{id}/cancel < 60 minutes throws 400 Bad Request")
    void testCancelUnder60MinutesThrows400() throws Exception {
        CancelReservationRequest req = new CancelReservationRequest("0912345678", "Hủy gấp");

        when(reservationService.cancelByCustomer(eq("res-1"), eq(null), any(CancelReservationRequest.class)))
            .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chỉ được hủy đặt bàn trước giờ đến tối thiểu 1 tiếng. Vui lòng liên hệ hotline nhà hàng để được hỗ trợ."));

        mockPublicMvc.perform(post("/api/reservations/res-1/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Chỉ được hủy đặt bàn trước giờ đến tối thiểu 1 tiếng. Vui lòng liên hệ hotline nhà hàng để được hỗ trợ."));
    }
}