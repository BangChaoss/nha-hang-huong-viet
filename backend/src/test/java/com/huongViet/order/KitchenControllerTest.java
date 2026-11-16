package com.huongViet.order;

import com.huongViet.config.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm thử REST API hàng đợi bếp KDS.
 */
@ExtendWith(MockitoExtension.class)
class KitchenControllerTest {

    @Mock
    private KitchenTicketService kitchenTicketService;

    private MockMvc mockMvc;

    private KitchenTicketResponse ticket;

    @BeforeEach
    void setUp() {
        KitchenController controller = new KitchenController(kitchenTicketService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

        ticket = new KitchenTicketResponse(
            "ticket-1", "order-1", "item-1", "Bàn 1", "Phở Bò", 2,
            "Ít cay", CookingStatus.QUEUED, "2026-11-11T10:00:00", null);
    }

    @Test
    @DisplayName("GET /api/kitchen/tickets - trả về toàn bộ hàng đợi theo FIFO")
    void listAll_NoFilter_ReturnsQueue() throws Exception {
        when(kitchenTicketService.listAll(null, null)).thenReturn(List.of(ticket));

        mockMvc.perform(get("/api/kitchen/tickets"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value("ticket-1"))
            .andExpect(jsonPath("$[0].tableNumber").value("Bàn 1"))
            .andExpect(jsonPath("$[0].status").value("QUEUED"));

        verify(kitchenTicketService).listAll(null, null);
    }

    @Test
    @DisplayName("GET /api/kitchen/tickets?status=PREPARING - lọc phiếu đang nấu")
    void listAll_FilterByStatus() throws Exception {
        KitchenTicketResponse preparing = new KitchenTicketResponse(
            "ticket-2", "order-1", "item-2", "Bàn 2", "Lẩu Riêu Cua", 1,
            null, CookingStatus.PREPARING, "2026-11-11T10:05:00", null);
        when(kitchenTicketService.listAll(CookingStatus.PREPARING, null)).thenReturn(List.of(preparing));

        mockMvc.perform(get("/api/kitchen/tickets").param("status", "PREPARING"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].status").value("PREPARING"));

        verify(kitchenTicketService).listAll(CookingStatus.PREPARING, null);
    }

    @Test
    @DisplayName("PATCH /api/kitchen/tickets/{id}/status - bếp chuyển phiếu sang PREPARING")
    void updateStatus_ReturnsUpdatedTicket() throws Exception {
        KitchenTicketResponse preparing = new KitchenTicketResponse(
            "ticket-1", "order-1", "item-1", "Bàn 1", "Phở Bò", 2,
            "Ít cay", CookingStatus.PREPARING, "2026-11-11T10:00:00", null);
        when(kitchenTicketService.updateStatus(eq("ticket-1"), any(UpdateTicketStatusRequest.class)))
            .thenReturn(preparing);

        mockMvc.perform(patch("/api/kitchen/tickets/ticket-1/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"PREPARING\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PREPARING"));
    }

    @Test
    @DisplayName("PATCH /api/kitchen/tickets/{id}/status - phiếu không tồn tại trả về 404")
    void updateStatus_NotFound_Returns404() throws Exception {
        when(kitchenTicketService.updateStatus(eq("ticket-x"), any(UpdateTicketStatusRequest.class)))
            .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phiếu bếp."));

        mockMvc.perform(patch("/api/kitchen/tickets/ticket-x/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"PREPARING\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Không tìm thấy phiếu bếp."));
    }

    @Test
    @DisplayName("PATCH /api/kitchen/tickets/{id}/status - trạng thái null trả về 400")
    void updateStatus_NullStatus_Returns400() throws Exception {
        mockMvc.perform(patch("/api/kitchen/tickets/ticket-1/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":null}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("PATCH /api/kitchen/tickets/{id}/status - trạng thái lạ trả về 400")
    void updateStatus_UnknownStatus_Returns400() throws Exception {
        mockMvc.perform(patch("/api/kitchen/tickets/ticket-1/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DANG_LAM\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
    }
}
