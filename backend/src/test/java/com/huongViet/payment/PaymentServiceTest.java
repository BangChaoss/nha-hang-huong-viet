package com.huongViet.payment;

import com.huongViet.order.CashCheckoutRequest;
import com.huongViet.order.CashCheckoutResponse;
import com.huongViet.order.OrderRepository;
import com.huongViet.order.OrderService;
import com.huongViet.table.TableRepository;
import com.huongViet.voucher.VoucherService;
import com.huongViet.voucher.VoucherValidationRequest;
import com.huongViet.voucher.VoucherValidationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentOrderRepository orderRepository;

    @Mock
    private LoyaltyAccountRepository loyaltyAccountRepository;

    @Mock
    private LoyaltySettingsRepository loyaltySettingsRepository;

    @Mock
    private VoucherService voucherService;

    @Mock
    private TableRepository tableRepository;

    @Mock
    private OrderRepository posOrderRepository;

    @Mock
    private OrderService orderService;

    private PaymentService paymentService;

    private static final String TEST_TMN = "TEST_TMN_CODE";
    private static final String TEST_SECRET = "1234567890ABCDEF1234567890ABCDEF1234567890ABCDEF1234567890ABCDEF";

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
                orderRepository,
                loyaltyAccountRepository,
                loyaltySettingsRepository,
                voucherService,
                tableRepository,
                posOrderRepository,
                orderService
        );

        ReflectionTestUtils.setField(paymentService, "tmnCode", TEST_TMN);
        ReflectionTestUtils.setField(paymentService, "hashSecret", TEST_SECRET);
        ReflectionTestUtils.setField(paymentService, "payUrl", "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        ReflectionTestUtils.setField(paymentService, "returnUrl", "http://localhost:8080/api/payments/vnpay/return");
        ReflectionTestUtils.setField(paymentService, "ipnUrl", "http://localhost:8080/api/payments/vnpay/ipn");
        ReflectionTestUtils.setField(paymentService, "version", "2.1.0");
        ReflectionTestUtils.setField(paymentService, "command", "pay");
        ReflectionTestUtils.setField(paymentService, "orderType", "other");
    }

    @Nested
    @DisplayName("Tạo thanh toán VNPay (createPayment)")
    class CreatePaymentTests {

        @Test
        @DisplayName("Lỗi 503 khi chưa cấu hình TMN Code hoặc Hash Secret")
        void testCreatePayment_MissingConfig() {
            ReflectionTestUtils.setField(paymentService, "tmnCode", "");

            VnpayCreateRequest req = new VnpayCreateRequest(
                    "table-1", new BigDecimal("100000"), "0901234567", null, null, false
            );

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> paymentService.createPayment(req, "127.0.0.1"));
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
        }

        @Test
        @DisplayName("Lỗi 400 khi tổng tiền subtotal null hoặc <= 0")
        void testCreatePayment_InvalidSubtotal() {
            VnpayCreateRequest reqZero = new VnpayCreateRequest(
                    "table-1", BigDecimal.ZERO, "0901234567", null, null, false
            );
            assertThrows(ResponseStatusException.class,
                    () -> paymentService.createPayment(reqZero, "127.0.0.1"));

            VnpayCreateRequest reqNegative = new VnpayCreateRequest(
                    "table-1", new BigDecimal("-50000"), "0901234567", null, null, false
            );
            assertThrows(ResponseStatusException.class,
                    () -> paymentService.createPayment(reqNegative, "127.0.0.1"));
        }

        @Test
        @DisplayName("Lỗi 400 khi dùng voucher hoặc điểm mà không nhập số điện thoại")
        void testCreatePayment_MissingPhoneWhenUsingVoucherOrPoints() {
            VnpayCreateRequest reqVoucherNoPhone = new VnpayCreateRequest(
                    "table-1", new BigDecimal("100000"), "", "SALE50", null, false
            );
            ResponseStatusException ex1 = assertThrows(ResponseStatusException.class,
                    () -> paymentService.createPayment(reqVoucherNoPhone, "127.0.0.1"));
            assertEquals(HttpStatus.BAD_REQUEST, ex1.getStatusCode());

            VnpayCreateRequest reqPointsNoPhone = new VnpayCreateRequest(
                    "table-1", new BigDecimal("100000"), null, null, null, true
            );
            ResponseStatusException ex2 = assertThrows(ResponseStatusException.class,
                    () -> paymentService.createPayment(reqPointsNoPhone, "127.0.0.1"));
            assertEquals(HttpStatus.BAD_REQUEST, ex2.getStatusCode());
        }

        @Test
        @DisplayName("Lỗi 400 khi voucher không hợp lệ")
        void testCreatePayment_InvalidVoucher() {
            when(voucherService.validate(any(VoucherValidationRequest.class)))
                    .thenReturn(new VoucherValidationResponse(false, "Voucher hết hạn", BigDecimal.ZERO, "EXPIRED"));

            VnpayCreateRequest req = new VnpayCreateRequest(
                    "table-1", new BigDecimal("100000"), "0901234567", "EXPIRED", null, false
            );

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> paymentService.createPayment(req, "127.0.0.1"));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
            assertTrue(ex.getReason().contains("Voucher hết hạn"));
        }

        @Test
        @DisplayName("Lỗi 400 khi dùng điểm mà không tìm thấy ví điểm hoặc số dư âm")
        void testCreatePayment_LoyaltyAccountNotFoundOrNegative() {
            when(loyaltyAccountRepository.findByCustomerPhone("0901234567"))
                    .thenReturn(Optional.empty());

            VnpayCreateRequest req = new VnpayCreateRequest(
                    "table-1", new BigDecimal("100000"), "0901234567", null, null, true
            );

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> paymentService.createPayment(req, "127.0.0.1"));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
            assertTrue(ex.getReason().contains("Không tìm thấy ví điểm"));
        }

        @Test
        @DisplayName("Dùng điểm: điểm được làm tròn bội số 100 và không vượt quá số dư/sau giảm giá")
        void testCreatePayment_PointsCalculationAndRounding() {
            LoyaltyAccount account = new LoyaltyAccount("acc-1", "0901234567");
            account.addPoints(new BigDecimal("250")); // Có 250 điểm
            when(loyaltyAccountRepository.findByCustomerPhone("0901234567"))
                    .thenReturn(Optional.of(account));

            VnpayCreateRequest req = new VnpayCreateRequest(
                    "table-1", new BigDecimal("50000"), "0901234567", null, null, true
            );

            Map<String, String> res = paymentService.createPayment(req, "127.0.0.1");
            assertNotNull(res.get("paymentUrl"));
            assertNotNull(res.get("orderId"));

            ArgumentCaptor<PaymentOrder> captor = ArgumentCaptor.forClass(PaymentOrder.class);
            verify(orderRepository).save(captor.capture());
            PaymentOrder saved = captor.getValue();

            // 250 làm tròn xuống bội 100 -> 200 điểm được dùng
            assertEquals(new BigDecimal("200"), saved.getPointsUsed());
            // Số tiền cần trả: 50.000 - 200 = 49.800
            assertEquals(new BigDecimal("49800.00"), saved.getAmount());
        }

        @Test
        @DisplayName("Lỗi 400 khi số tiền thanh toán cuối cùng < 1.000 VND (VNPay MIN_PAYMENT)")
        void testCreatePayment_AmountLessThanMin() {
            when(voucherService.validate(any(VoucherValidationRequest.class)))
                    .thenReturn(new VoucherValidationResponse(true, "Hợp lệ", new BigDecimal("99500"), "DISCOUNT"));

            VnpayCreateRequest req = new VnpayCreateRequest(
                    "table-1", new BigDecimal("100000"), "0901234567", "DISCOUNT", null, false
            );

            // Subtotal 100.000 - 99.500 = 500 VND < 1.000 VND
            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> paymentService.createPayment(req, "127.0.0.1"));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
            assertTrue(ex.getReason().contains("tối thiểu là 1.000 VND"));
        }

        @Test
        @DisplayName("Tạo thanh toán thành công: Lưu PaymentOrder PENDING và sinh paymentUrl có hash chuẩn")
        void testCreatePayment_Success() {
            when(voucherService.validate(any(VoucherValidationRequest.class)))
                    .thenReturn(new VoucherValidationResponse(true, "Hợp lệ", new BigDecimal("20000"), "SUMMER"));

            LoyaltyAccount account = new LoyaltyAccount("acc-1", "0901234567");
            account.addPoints(new BigDecimal("5000"));
            when(loyaltyAccountRepository.findByCustomerPhone("0901234567"))
                    .thenReturn(Optional.of(account));

            VnpayCreateRequest req = new VnpayCreateRequest(
                    "table-1", new BigDecimal("100000"), "0901-234-567", "SUMMER", null, true
            );

            Map<String, String> res = paymentService.createPayment(req, "::1");

            assertNotNull(res.get("orderId"));
            String paymentUrl = res.get("paymentUrl");
            assertNotNull(paymentUrl);
            assertTrue(paymentUrl.startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?"));
            assertTrue(paymentUrl.contains("vnp_TmnCode=" + TEST_TMN));
            assertTrue(paymentUrl.contains("vnp_SecureHash="));
            assertTrue(paymentUrl.contains("vnp_IpAddr=127.0.0.1")); // IPv6 ::1 normalized to 127.0.0.1

            ArgumentCaptor<PaymentOrder> captor = ArgumentCaptor.forClass(PaymentOrder.class);
            verify(orderRepository).save(captor.capture());
            PaymentOrder saved = captor.getValue();
            assertEquals("PENDING", saved.getStatus());
            assertEquals("0901234567", saved.getCustomerPhone());
            assertEquals(new BigDecimal("100000.00"), saved.getSubtotal());
            assertEquals(new BigDecimal("20000"), saved.getDiscountAmount());
            assertEquals(new BigDecimal("5000"), saved.getPointsUsed());
            // 100.000 - 20.000 - 5.000 = 75.000
            assertEquals(new BigDecimal("75000.00"), saved.getAmount());
        }
    }

    @Nested
    @DisplayName("Xác thực chữ ký VNPay (verify)")
    class VerifySignatureTests {

        @Test
        @DisplayName("verify trả về false khi params rỗng hoặc thiếu vnp_SecureHash")
        void testVerify_NullOrEmpty() {
            assertFalse(paymentService.verify(null));
            assertFalse(paymentService.verify(Collections.emptyMap()));
            assertFalse(paymentService.verify(Map.of("vnp_TxnRef", "123")));
        }

        @Test
        @DisplayName("verify trả về false khi vnp_SecureHash không đúng định dạng hex 128 ký tự")
        void testVerify_InvalidHashFormat() {
            Map<String, String> params = Map.of(
                    "vnp_TxnRef", "123",
                    "vnp_SecureHash", "invalid_short_hash"
            );
            assertFalse(paymentService.verify(params));
        }

        @Test
        @DisplayName("verify trả về true khi chữ ký HMAC-SHA512 khớp chính xác")
        void testVerify_ValidSignature() {
            Map<String, String> params = new TreeMap<>();
            params.put("vnp_Amount", "10000000");
            params.put("vnp_Command", "pay");
            params.put("vnp_ResponseCode", "00");
            params.put("vnp_TmnCode", TEST_TMN);
            params.put("vnp_TxnRef", "test-order-123");

            String query = buildQueryString(params);
            String validHash = calculateHmacSha512(TEST_SECRET, query);

            params.put("vnp_SecureHash", validHash);

            assertTrue(paymentService.verify(params));
        }

        @Test
        @DisplayName("verify trả về false khi dữ liệu bị sửa đổi (tampered data)")
        void testVerify_TamperedData() {
            Map<String, String> params = new TreeMap<>();
            params.put("vnp_Amount", "10000000");
            params.put("vnp_TxnRef", "test-order-123");

            String query = buildQueryString(params);
            String validHash = calculateHmacSha512(TEST_SECRET, query);

            params.put("vnp_Amount", "99999999"); // Dữ liệu bị sửa
            params.put("vnp_SecureHash", validHash);

            assertFalse(paymentService.verify(params));
        }
    }

    @Nested
    @DisplayName("Xử lý Callback/IPN từ VNPay (processCallback)")
    class ProcessCallbackTests {

        private Map<String, String> createSignedParams(Map<String, String> data) {
            Map<String, String> map = new TreeMap<>(data);
            String query = buildQueryString(map);
            String hash = calculateHmacSha512(TEST_SECRET, query);
            map.put("vnp_SecureHash", hash);
            return map;
        }

        @Test
        @DisplayName("Callback thất bại với INVALID_SIGNATURE khi sai chữ ký")
        void testProcessCallback_InvalidSignature() {
            Map<String, String> params = Map.of(
                    "vnp_TxnRef", "order-1",
                    "vnp_SecureHash", "0".repeat(128)
            );
            assertEquals("INVALID_SIGNATURE", paymentService.processCallback(params));
        }

        @Test
        @DisplayName("Callback thất bại với ORDER_NOT_FOUND khi đơn hàng không tồn tại")
        void testProcessCallback_OrderNotFound() {
            Map<String, String> params = createSignedParams(Map.of(
                    "vnp_TxnRef", "order-not-exist"
            ));
            when(orderRepository.lockById("order-not-exist")).thenReturn(Optional.empty());

            assertEquals("ORDER_NOT_FOUND", paymentService.processCallback(params));
        }

        @Test
        @DisplayName("Callback thất bại với INVALID_AMOUNT khi số tiền callback không khớp đơn hàng")
        void testProcessCallback_InvalidAmount() {
            PaymentOrder order = new PaymentOrder(
                    "order-1", "table-1", "0901234567",
                    new BigDecimal("100000"), BigDecimal.ZERO, new BigDecimal("100000"), null, BigDecimal.ZERO
            );
            when(orderRepository.lockById("order-1")).thenReturn(Optional.of(order));

            // Số tiền mong đợi là 100000 * 100 = 10000000, nhưng gửi lên 5000000
            Map<String, String> params = createSignedParams(Map.of(
                    "vnp_TxnRef", "order-1",
                    "vnp_Amount", "5000000"
            ));

            assertEquals("INVALID_AMOUNT", paymentService.processCallback(params));
        }

        @Test
        @DisplayName("Callback thành công đã được xử lý (Idempotency): Trả về '00' ngay")
        void testProcessCallback_IdempotentAlreadyPaid() {
            PaymentOrder order = new PaymentOrder(
                    "order-1", "table-1", "0901234567",
                    new BigDecimal("100000"), BigDecimal.ZERO, new BigDecimal("100000"), null, BigDecimal.ZERO
            );
            order.markPaid("TXN123", "00", BigDecimal.ZERO);
            when(orderRepository.lockById("order-1")).thenReturn(Optional.of(order));

            Map<String, String> params = createSignedParams(Map.of(
                    "vnp_TxnRef", "order-1",
                    "vnp_Amount", "10000000"
            ));

            assertEquals("00", paymentService.processCallback(params));
            verifyNoInteractions(orderService);
        }

        @Test
        @DisplayName("Callback thanh toán thất bại (vnp_ResponseCode != 00): Đánh dấu FAILED và trả PAYMENT_FAILED")
        void testProcessCallback_PaymentFailed() {
            PaymentOrder order = new PaymentOrder(
                    "order-1", "table-1", "0901234567",
                    new BigDecimal("100000"), BigDecimal.ZERO, new BigDecimal("100000"), null, BigDecimal.ZERO
            );
            when(orderRepository.lockById("order-1")).thenReturn(Optional.of(order));

            Map<String, String> params = createSignedParams(Map.of(
                    "vnp_TxnRef", "order-1",
                    "vnp_Amount", "10000000",
                    "vnp_ResponseCode", "24", // Khách hủy thanh toán
                    "vnp_TransactionStatus", "02",
                    "vnp_TransactionNo", "TXN_FAILED_99"
            ));

            String result = paymentService.processCallback(params);
            assertEquals("PAYMENT_FAILED", result);
            assertEquals("FAILED", order.getStatus());
            assertEquals("24", order.getVnpResponseCode());
        }

        @Test
        @DisplayName("Lỗi 409 CONFLICT nếu đơn thanh toán VNPay không liên kết bàn POS")
        void testProcessCallback_MissingTableId() {
            PaymentOrder order = new PaymentOrder(
                    "order-1", null, "0901234567",
                    new BigDecimal("100000"), BigDecimal.ZERO, new BigDecimal("100000"), null, BigDecimal.ZERO
            );
            when(orderRepository.lockById("order-1")).thenReturn(Optional.of(order));

            Map<String, String> params = createSignedParams(Map.of(
                    "vnp_TxnRef", "order-1",
                    "vnp_Amount", "10000000",
                    "vnp_ResponseCode", "00",
                    "vnp_TransactionStatus", "00"
            ));

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> paymentService.processCallback(params));
            assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        }

        @Test
        @DisplayName("Callback thành công: Gọi orderService.checkoutCash, đánh dấu PAID và lưu điểm thưởng")
        void testProcessCallback_Success() {
            PaymentOrder order = new PaymentOrder(
                    "order-1", "table-10", "0901234567",
                    new BigDecimal("100000"), BigDecimal.ZERO, new BigDecimal("100000"), "VOUCHER10", new BigDecimal("500")
            );
            when(orderRepository.lockById("order-1")).thenReturn(Optional.of(order));

            CashCheckoutResponse checkoutResponse = new CashCheckoutResponse(
                    "table-10",
                    new BigDecimal("100000"), // Tiền món
                    new BigDecimal("10000"),  // Thuế
                    new BigDecimal("10000"),  // Giảm giá
                    new BigDecimal("500"),    // Điểm đã dùng
                    new BigDecimal("100000"), // Tổng khớp với order.getAmount()
                    new BigDecimal("5000"),   // Điểm thưởng
                    "Bàn 10"                  // Số bàn
            );
            when(orderService.checkoutCash(eq("table-10"), any(CashCheckoutRequest.class)))
                    .thenReturn(checkoutResponse);

            Map<String, String> params = createSignedParams(Map.of(
                    "vnp_TxnRef", "order-1",
                    "vnp_Amount", "10000000",
                    "vnp_ResponseCode", "00",
                    "vnp_TransactionStatus", "00",
                    "vnp_TransactionNo", "VNPAY_TXN_777"
            ));

            String result = paymentService.processCallback(params);
            assertEquals("00", result);
            assertEquals("PAID", order.getStatus());
            assertEquals("VNPAY_TXN_777", order.getVnpTransactionNo());
            assertEquals(new BigDecimal("5000"), order.getPointsAwarded());
            assertTrue(order.isPointsDeducted());
            assertNotNull(order.getPaidAt());
        }
    }

    @Nested
    @DisplayName("Tra cứu đơn giao dịch (getOrder)")
    class GetOrderTests {

        @Test
        @DisplayName("getOrder thành công trả về đầy đủ thông tin giao dịch")
        void testGetOrder_Found() {
            PaymentOrder order = new PaymentOrder(
                    "order-xyz", "table-5", "0912345678",
                    new BigDecimal("120000"), new BigDecimal("20000"), new BigDecimal("100000"), "DISCOUNT", new BigDecimal("100")
            );
            order.markPaid("TXN-999", "00", new BigDecimal("500"));
            when(orderRepository.findById("order-xyz")).thenReturn(Optional.of(order));

            Map<String, Object> result = paymentService.getOrder("order-xyz");
            assertEquals("order-xyz", result.get("orderId"));
            assertEquals("table-5", result.get("tableId"));
            assertEquals("PAID", result.get("status"));
            assertEquals(new BigDecimal("100000"), result.get("amount"));
            assertEquals("0912345678", result.get("phoneNumber"));
            assertEquals(new BigDecimal("500"), result.get("pointsAwarded"));
            assertEquals(new BigDecimal("100"), result.get("pointsUsed"));
            assertEquals("00", result.get("responseCode"));
            assertEquals("TXN-999", result.get("transactionNo"));
        }

        @Test
        @DisplayName("getOrder ném 404 NOT_FOUND khi không tìm thấy đơn")
        void testGetOrder_NotFound() {
            when(orderRepository.findById("not-found")).thenReturn(Optional.empty());

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> paymentService.getOrder("not-found"));
            assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        }
    }

    // Helper functions để tính hash chuẩn HMAC SHA-512 giống PaymentService
    private static String buildQueryString(Map<String, String> params) {
        StringJoiner joiner = new StringJoiner("&");
        params.entrySet().stream()
                .filter(e -> e.getValue() != null && !e.getValue().isBlank())
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> joiner.add(
                        URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                                + "="
                                + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8)
                ));
        return joiner.toString();
    }

    private static String calculateHmacSha512(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
