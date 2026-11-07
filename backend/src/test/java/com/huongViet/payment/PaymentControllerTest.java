package com.huongViet.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huongViet.config.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    private MockMvc mockMvc;

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private PaymentController paymentController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(paymentController, "frontendUrl", "http://localhost:8081");
        mockMvc = MockMvcBuilders.standaloneSetup(paymentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /api/payments/vnpay/create trả về 200 và URL thanh toán")
    void testCreatePaymentEndpoint() throws Exception {
        VnpayCreateRequest request = new VnpayCreateRequest(
                "table-1", new BigDecimal("150000"), "0901234567", "SALE10", null, false
        );

        when(paymentService.createPayment(any(VnpayCreateRequest.class), eq("192.168.1.1")))
                .thenReturn(Map.of(
                        "orderId", "order-abc",
                        "paymentUrl", "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?orderId=order-abc"
                ));

        mockMvc.perform(post("/api/payments/vnpay/create")
                        .header("X-Forwarded-For", "192.168.1.1, 10.0.0.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("order-abc"))
                .andExpect(jsonPath("$.paymentUrl").value("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?orderId=order-abc"));
    }

    @Test
    @DisplayName("GET /api/payments/vnpay/ipn ánh xạ đúng RspCode của VNPay")
    void testIpnEndpoint_StatusMappings() throws Exception {
        // Success 00
        when(paymentService.processCallback(any())).thenReturn("00");
        mockMvc.perform(get("/api/payments/vnpay/ipn").param("vnp_TxnRef", "tx1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("00"))
                .andExpect(jsonPath("$.Message").value("Confirm Success"));

        // Invalid signature 97
        when(paymentService.processCallback(any())).thenReturn("INVALID_SIGNATURE");
        mockMvc.perform(get("/api/payments/vnpay/ipn").param("vnp_TxnRef", "tx1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("97"))
                .andExpect(jsonPath("$.Message").value("Invalid Signature"));

        // Invalid amount 04
        when(paymentService.processCallback(any())).thenReturn("INVALID_AMOUNT");
        mockMvc.perform(get("/api/payments/vnpay/ipn").param("vnp_TxnRef", "tx1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("04"))
                .andExpect(jsonPath("$.Message").value("Invalid Amount"));

        // Order not found 01
        when(paymentService.processCallback(any())).thenReturn("ORDER_NOT_FOUND");
        mockMvc.perform(get("/api/payments/vnpay/ipn").param("vnp_TxnRef", "tx1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("01"))
                .andExpect(jsonPath("$.Message").value("Order Not Found"));

        // Payment failed acknowledged with 00
        when(paymentService.processCallback(any())).thenReturn("PAYMENT_FAILED");
        mockMvc.perform(get("/api/payments/vnpay/ipn").param("vnp_TxnRef", "tx1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("00"))
                .andExpect(jsonPath("$.Message").value("Confirm Success"));
    }

    @Test
    @DisplayName("GET /api/payments/vnpay/return chuyển hướng về frontend với trạng thái thành công")
    void testReturnEndpoint_Success() throws Exception {
        when(paymentService.processCallback(any())).thenReturn("00");

        mockMvc.perform(get("/api/payments/vnpay/return")
                        .param("vnp_TxnRef", "tx-999")
                        .param("vnp_ResponseCode", "00")
                        .param("vnp_TransactionStatus", "00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost:8081/staff?paymentOrderId=tx-999&paymentStatus=success"));
    }

    @Test
    @DisplayName("GET /api/payments/vnpay/return chuyển hướng về frontend với trạng thái thất bại khi giao dịch lỗi")
    void testReturnEndpoint_Failed() throws Exception {
        when(paymentService.processCallback(any())).thenReturn("PAYMENT_FAILED");

        mockMvc.perform(get("/api/payments/vnpay/return")
                        .param("vnp_TxnRef", "tx-999")
                        .param("vnp_ResponseCode", "24")
                        .param("vnp_TransactionStatus", "02"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost:8081/staff?paymentOrderId=tx-999&paymentStatus=failed"));
    }

    @Test
    @DisplayName("GET /api/payments/vnpay/orders/{id} trả về 200 và thông tin đơn")
    void testGetOrderEndpoint() throws Exception {
        when(paymentService.getOrder("order-100")).thenReturn(Map.of(
                "orderId", "order-100",
                "tableId", "table-3",
                "status", "PAID",
                "amount", new BigDecimal("250000")
        ));

        mockMvc.perform(get("/api/payments/vnpay/orders/order-100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("order-100"))
                .andExpect(jsonPath("$.tableId").value("table-3"))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.amount").value(250000));
    }
}
