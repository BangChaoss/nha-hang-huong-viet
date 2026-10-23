package com.huongViet.reservation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.huongViet.config.GlobalExceptionHandler;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ReservationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ReservationService reservationService;

    @Mock
    private ZoneService zoneService;

    @InjectMocks
    private ReservationPublicController reservationPublicController;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
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
    @DisplayName("GET /api/reservations/guest-lookup - Tra cứu thành công")
    void guestLookup_Success() throws Exception {
        when(reservationService.getGuestBookings("0912345678")).thenReturn(List.of());

        mockMvc.perform(get("/api/reservations/guest-lookup")
                .param("phone", "0912345678"))
            .andExpect(status().isOk());
    }
}
