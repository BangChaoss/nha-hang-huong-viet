package com.huongViet.voucher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.huongViet.config.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class VoucherControllerTest {

    private MockMvc mockAdminMvc;
    private MockMvc mockPublicMvc;

    @Mock
    private VoucherService voucherService;

    @InjectMocks
    private VoucherController voucherController;

    private VoucherPublicController voucherPublicController;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        voucherPublicController = new VoucherPublicController(voucherService);

        mockAdminMvc = MockMvcBuilders.standaloneSetup(voucherController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockPublicMvc = MockMvcBuilders.standaloneSetup(voucherPublicController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private Voucher sampleVoucher(String id, String code, Voucher.DiscountType type, BigDecimal value, boolean active) {
        Voucher v = new Voucher(
                id, code, "Tiêu đề " + code, "Mô tả",
                type, value, null, BigDecimal.ZERO,
                LocalDateTime.now(), LocalDateTime.now().plusDays(5), 100, 1
        );
        v.setActive(active);
        return v;
    }

    @Test
    @DisplayName("GET /api/admin/vouchers trả về danh sách voucher")
    void testListVouchers() throws Exception {
        VoucherResponse v = VoucherResponse.from(sampleVoucher("1", "SALE20", Voucher.DiscountType.PERCENTAGE, new BigDecimal("20"), true));
        when(voucherService.list()).thenReturn(List.of(v));

        mockAdminMvc.perform(get("/api/admin/vouchers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].voucherCode").value("SALE20"));
    }

    @Test
    @DisplayName("GET /api/admin/vouchers/{id}/usages trả về lịch sử dùng voucher")
    void testGetUsages() throws Exception {
        VoucherUsageResponse usage = new VoucherUsageResponse(
                "u-1", "1", "0901234567", "inv-1", "HV-INV-1",
                new BigDecimal("20000"), LocalDateTime.now()
        );
        when(voucherService.getUsages("1")).thenReturn(List.of(usage));

        mockAdminMvc.perform(get("/api/admin/vouchers/1/usages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].customerPhone").value("0901234567"))
                .andExpect(jsonPath("$[0].invoiceCode").value("HV-INV-1"));
    }

    @Test
    @DisplayName("POST /api/admin/vouchers tạo voucher trả về 201 CREATED")
    void testCreateVoucher() throws Exception {
        VoucherRequest request = new VoucherRequest(
                "DISCOUNT10", "Tiêu đề", "Mô tả", Voucher.DiscountType.PERCENTAGE,
                new BigDecimal("10"), null, BigDecimal.ZERO,
                LocalDateTime.now(), LocalDateTime.now().plusDays(10), 100, 1
        );

        VoucherResponse response = VoucherResponse.from(sampleVoucher("10", "DISCOUNT10", Voucher.DiscountType.PERCENTAGE, new BigDecimal("10"), true));
        when(voucherService.create(any(VoucherRequest.class))).thenReturn(response);

        mockAdminMvc.perform(post("/api/admin/vouchers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.voucherCode").value("DISCOUNT10"))
                .andExpect(jsonPath("$.id").value("10"));
    }

    @Test
    @DisplayName("PATCH /api/admin/vouchers/{id}/status cập nhật trạng thái bật/tắt")
    void testUpdateStatus() throws Exception {
        VoucherResponse response = VoucherResponse.from(sampleVoucher("1", "SALE20", Voucher.DiscountType.PERCENTAGE, new BigDecimal("20"), false));
        when(voucherService.setActive("1", false)).thenReturn(response);

        mockAdminMvc.perform(patch("/api/admin/vouchers/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @DisplayName("DELETE /api/admin/vouchers/{id} xóa voucher trả về 204 NO_CONTENT")
    void testDeleteVoucher() throws Exception {
        doNothing().when(voucherService).delete("1");

        mockAdminMvc.perform(delete("/api/admin/vouchers/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST /api/vouchers/validate kiểm tra voucher từ phía khách/POS")
    void testPublicValidate() throws Exception {
        VoucherValidationRequest req = new VoucherValidationRequest("PROMO", "0901234567", new BigDecimal("100000"));
        VoucherValidationResponse res = new VoucherValidationResponse(true, "Hợp lệ", new BigDecimal("10000"), "PROMO");

        when(voucherService.validate(any(VoucherValidationRequest.class))).thenReturn(res);

        mockPublicMvc.perform(post("/api/vouchers/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.discountAmount").value(10000));
    }

    @Test
    @DisplayName("POST /api/vouchers/redeem - gọi endpoint redeem trực tiếp trả về 400")
    void testPublicRedeem() throws Exception {
        VoucherValidationRequest req = new VoucherValidationRequest("PROMO", "0901234567", new BigDecimal("100000"));
        when(voucherService.redeem(any(VoucherValidationRequest.class)))
                .thenThrow(new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST, "Vui lòng áp dụng voucher qua quy trình thanh toán"));

        mockPublicMvc.perform(post("/api/vouchers/redeem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/admin/vouchers - mã voucher rỗng trả về 400 Bad Request")
    void testCreateVoucher_InvalidInput() throws Exception {
        VoucherRequest request = new VoucherRequest(
                "", "Tiêu đề", "Mô tả", Voucher.DiscountType.PERCENTAGE,
                new BigDecimal("10"), null, BigDecimal.ZERO,
                LocalDateTime.now(), LocalDateTime.now().plusDays(10), 100, 1
        );

        mockAdminMvc.perform(post("/api/admin/vouchers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE /api/admin/vouchers/{id} - voucher không tồn tại trả về 404")
    void testDeleteVoucher_NotFound() throws Exception {
        doThrow(new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "Không tìm thấy voucher."))
                .when(voucherService).delete("999");

        mockAdminMvc.perform(delete("/api/admin/vouchers/999"))
                .andExpect(status().isNotFound());
    }
}
