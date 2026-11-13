package com.huongViet.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.huongViet.config.GlobalExceptionHandler;
import com.huongViet.order.dto.InvoiceItemResponse;
import com.huongViet.order.dto.InvoiceResponse;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InvoiceControllerTest {

    private MockMvc mockMvc;

    @Mock
    private InvoiceService invoiceService;

    @InjectMocks
    private InvoiceController invoiceController;

    private InvoiceResponse sampleInvoice;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(invoiceController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        InvoiceItemResponse item = new InvoiceItemResponse(
                "item-1", "dish-1", "Phở Bò", new BigDecimal("50000"), 2, new BigDecimal("100000")
        );

        sampleInvoice = new InvoiceResponse(
                "inv-1",
                "HV-20261113-001",
                "order-1",
                "Bàn 01",
                "staff-1",
                "0901234567",
                "GIAM10",
                new BigDecimal("10000"),
                new BigDecimal("100000"),
                new BigDecimal("10000"),
                new BigDecimal("10000"),
                new BigDecimal("100000"),
                500,
                "PAID",
                LocalDateTime.of(2026, 11, 13, 12, 0, 0),
                List.of(item)
        );
    }

    @Test
    @DisplayName("GET /api/invoices - không truyền ngày thì trả về danh sách hóa đơn hôm nay")
    void listByDate_NoParam_ReturnsList() throws Exception {
        when(invoiceService.listByDate(null)).thenReturn(List.of(sampleInvoice));

        mockMvc.perform(get("/api/invoices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("inv-1"))
                .andExpect(jsonPath("$[0].invoiceCode").value("HV-20261113-001"))
                .andExpect(jsonPath("$[0].tableNumber").value("Bàn 01"))
                .andExpect(jsonPath("$[0].finalAmount").value(100000));

        verify(invoiceService).listByDate(null);
    }

    @Test
    @DisplayName("GET /api/invoices?date=2026-11-13 - truyền ngày hợp lệ")
    void listByDate_WithDateParam_ReturnsList() throws Exception {
        LocalDate date = LocalDate.of(2026, 11, 13);
        when(invoiceService.listByDate(eq(date))).thenReturn(List.of(sampleInvoice));

        mockMvc.perform(get("/api/invoices").param("date", "2026-11-13"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].customerPhone").value("0901234567"))
                .andExpect(jsonPath("$[0].items[0].dishName").value("Phở Bò"));

        verify(invoiceService).listByDate(date);
    }

    @Test
    @DisplayName("GET /api/invoices/{id} - tìm thấy hóa đơn trả về 200 OK")
    void getById_Found_ReturnsInvoice() throws Exception {
        when(invoiceService.getById("inv-1")).thenReturn(sampleInvoice);

        mockMvc.perform(get("/api/invoices/inv-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("inv-1"))
                .andExpect(jsonPath("$.invoiceCode").value("HV-20261113-001"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].totalPrice").value(100000));

        verify(invoiceService).getById("inv-1");
    }

    @Test
    @DisplayName("GET /api/invoices/{id} - không tìm thấy hóa đơn trả về 404 NOT_FOUND")
    void getById_NotFound_Returns404() throws Exception {
        when(invoiceService.getById("inv-999"))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hóa đơn."));

        mockMvc.perform(get("/api/invoices/inv-999"))
                .andExpect(status().isNotFound());

        verify(invoiceService).getById("inv-999");
    }
}
