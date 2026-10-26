
package com.huongViet.payment;

import com.huongViet.order.OrderRepository;
import com.huongViet.order.OrderService;
import com.huongViet.order.CashCheckoutRequest;
import com.huongViet.table.TableRepository;
import com.huongViet.voucher.VoucherService;
import com.huongViet.voucher.VoucherValidationRequest;
import com.huongViet.voucher.VoucherValidationResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.UUID;

@Service
public class PaymentService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal MIN_PAYMENT = BigDecimal.valueOf(1000);
    private static final String DEFAULT_SETTINGS_ID = "default";

    private final PaymentOrderRepository orders;
    private final LoyaltyAccountRepository loyaltyAccounts;
    private final LoyaltySettingsRepository settings;
    private final VoucherService voucherService;

    /*
     * Các repository này được giữ để tương thích với cấu trúc
     * PaymentService hiện tại của dự án.
     */
    private final TableRepository tableRepository;
    private final OrderRepository orderRepository;
    private final OrderService orderService;

    @Value("${VNPAY_SANDBOX_TMN_CODE:${vnpay.tmn-code:}}")
    private String tmnCode;

    @Value("${VNPAY_SANDBOX_HASH_SECRET:${vnpay.hash-secret:}}")
    private String hashSecret;

    @Value("${vnpay.pay-url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}")
    private String payUrl;

    @Value("${vnpay.return-url:http://localhost:8080/api/payments/vnpay/return}")
    private String returnUrl;

    @Value("${vnpay.ipn-url:http://localhost:8080/api/payments/vnpay/ipn}")
    private String ipnUrl;

    @Value("${vnpay.version:2.1.0}")
    private String version;

    @Value("${vnpay.command:pay}")
    private String command;

    @Value("${vnpay.order-type:other}")
    private String orderType;

    public PaymentService(
            PaymentOrderRepository orders,
            LoyaltyAccountRepository loyaltyAccounts,
            LoyaltySettingsRepository settings,
            VoucherService voucherService,
            TableRepository tableRepository,
            OrderRepository orderRepository,
            OrderService orderService
    ) {
        this.orders = orders;
        this.loyaltyAccounts = loyaltyAccounts;
        this.settings = settings;
        this.voucherService = voucherService;
        this.tableRepository = tableRepository;
        this.orderRepository = orderRepository;
        this.orderService = orderService;
    }

    /**
     * Tạo giao dịch VNPay.
     *
     * Quy trình:
     * 1. Kiểm tra cấu hình VNPay.
     * 2. Kiểm tra số điện thoại, voucher và điểm muốn sử dụng.
     * 3. Tính số tiền thực tế cần thanh toán.
     * 4. Lưu giao dịch PENDING cùng số điểm dự kiến sử dụng.
     * 5. Tạo URL thanh toán VNPay.
     *
     * Lưu ý: req.subtotal() được hiểu là tổng tiền hàng mà frontend
     * gửi lên. Phương thức này không tự cộng VAT lần nữa.
     */
    @Transactional
    public Map<String, String> createPayment(
            VnpayCreateRequest req,
            String clientIp
    ) {
        if (tmnCode == null || tmnCode.isBlank()
                || hashSecret == null || hashSecret.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Chưa cấu hình VNPAY_SANDBOX_TMN_CODE và VNPAY_SANDBOX_HASH_SECRET."
            );
        }

        if (req == null || req.subtotal() == null
                || req.subtotal().compareTo(ZERO) <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Tổng tiền thanh toán không hợp lệ."
            );
        }

        String phone = normalizePhone(
                req.phoneNumber() == null ? "" : req.phoneNumber()
        );

        String voucherCode = req.voucherCode() == null
                ? ""
                : req.voucherCode().trim().toUpperCase(Locale.ROOT);

        boolean usePoints = Boolean.TRUE.equals(req.usePoints());

        if ((!voucherCode.isBlank() || usePoints) && phone.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Vui lòng nhập số điện thoại để sử dụng voucher hoặc điểm tích lũy."
            );
        }

        BigDecimal subtotal = req.subtotal()
                .setScale(2, RoundingMode.HALF_UP);

        /*
         * Bước 1: kiểm tra voucher.
         */
        BigDecimal discount = ZERO;

        if (!voucherCode.isBlank()) {
            VoucherValidationResponse validation =
                    voucherService.validate(
                            new VoucherValidationRequest(
                                    voucherCode,
                                    phone,
                                    subtotal
                            )
                    );

            if (validation == null || !validation.valid()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        validation == null
                                ? "Không thể kiểm tra voucher."
                                : validation.message()
                );
            }

            discount = validation.discountAmount() == null
                    ? ZERO
                    : validation.discountAmount();

            discount = discount.max(ZERO).min(subtotal);
        }

        /*
         * Bước 2: tính tiền sau khi giảm voucher.
         */
        BigDecimal afterVoucher = subtotal.subtract(discount)
                .max(ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        /*
         * Bước 3: xác định số điểm khách muốn sử dụng.
         *
         * Chưa trừ điểm khỏi ví tại bước này vì khách có thể
         * hủy giao dịch hoặc thanh toán VNPay thất bại.
         */
        BigDecimal pointsUsed = ZERO;

        if (usePoints) {
            LoyaltyAccount account = loyaltyAccounts
                    .findByCustomerPhone(phone)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Không tìm thấy ví điểm của khách hàng."
                    ));

            BigDecimal balance = account.getPointsBalance() == null
                    ? ZERO
                    : account.getPointsBalance();

            if (balance.compareTo(ZERO) < 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Số dư ví điểm không hợp lệ."
                );
            }

            /*
             * Không cho dùng số điểm vượt quá:
             * - Số dư ví điểm hiện tại.
             * - Số tiền còn lại sau khi áp dụng voucher.
             */
            // Điểm dùng để giảm giá phải là bội số 100 và không vượt số dư/hóa đơn.
            BigDecimal redeemable = balance.min(afterVoucher);
            pointsUsed = redeemable.divide(new BigDecimal("100"), 0, RoundingMode.DOWN)
                    .multiply(new BigDecimal("100")).setScale(0, RoundingMode.UNNECESSARY);
        }

        /*
         * Bước 4: số tiền khách thực sự trả qua VNPay.
         */
        BigDecimal amount = afterVoucher.subtract(pointsUsed)
                .max(ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        if (amount.compareTo(MIN_PAYMENT) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Số tiền thanh toán qua VNPay tối thiểu là 1.000 VND. "
                            + "Vui lòng điều chỉnh số điểm sử dụng hoặc phương thức thanh toán."
            );
        }

        /*
         * Bước 5: lưu giao dịch.
         *
         * PaymentOrder cần có trường pointsUsed và constructor
         * tương ứng để lưu số điểm dự kiến sử dụng vào database.
         */
        String orderId = UUID.randomUUID().toString();

        PaymentOrder paymentOrder = new PaymentOrder(
                orderId,
                req.tableId(),
                phone,
                subtotal,
                discount,
                amount,
                voucherCode.isBlank() ? null : voucherCode,
                pointsUsed
        );

        orders.save(paymentOrder);

        /*
         * Bước 6: tạo tham số gửi VNPay.
         */
        Map<String, String> params = new TreeMap<>();

        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", tmnCode);

        params.put(
                "vnp_Amount",
                amount.multiply(BigDecimal.valueOf(100))
                        .setScale(0, RoundingMode.HALF_UP)
                        .toPlainString()
        );

        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", orderId);
        params.put("vnp_OrderInfo", "Thanh toan don hang " + orderId);
        params.put("vnp_OrderType", orderType);
        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", returnUrl);
        params.put("vnp_IpAddr", normalizeIp(clientIp));

        ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
        DateTimeFormatter formatter =
                DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

        LocalDateTime now = LocalDateTime.now(zone);

        params.put("vnp_CreateDate", now.format(formatter));
        params.put(
                "vnp_ExpireDate",
                now.plusMinutes(15).format(formatter)
        );

        String query = toQuery(params);
        String signature = hmacSha512(hashSecret, query);

        String paymentUrl = payUrl
                + "?"
                + query
                + "&vnp_SecureHash="
                + signature;

        return Map.of(
                "orderId", orderId,
                "paymentUrl", paymentUrl
        );
    }

    /**
     * Xác minh chữ ký callback từ VNPay.
     */
    public boolean verify(Map<String, String> input) {
        if (input == null || input.isEmpty()
                || hashSecret == null || hashSecret.isBlank()) {
            return false;
        }

        String received = input.get("vnp_SecureHash");

        if (received == null
                || !received.matches("(?i)^[0-9a-f]{128}$")) {
            return false;
        }

        Map<String, String> params = new TreeMap<>(input);
        params.remove("vnp_SecureHash");
        params.remove("vnp_SecureHashType");

        String expected = hmacSha512(
                hashSecret,
                toQuery(params)
        );

        return constantTimeEquals(expected, received);
    }

    /**
     * Xử lý callback/IPN của VNPay.
     *
     * Chỉ khi VNPay xác nhận giao dịch thành công mới:
     * - Trừ số điểm khách đã sử dụng.
     * - Cộng điểm thưởng mới.
     * - Lưu số dư ví điểm.
     * - Đánh dấu giao dịch PAID.
     *
     * Phương thức cần chạy trong transaction để các thay đổi ví điểm
     * và trạng thái PaymentOrder được commit cùng nhau.
     */
    @Transactional
    public String processCallback(Map<String, String> params) {
        if (!verify(params)) {
            return "INVALID_SIGNATURE";
        }

        String orderId = params.get("vnp_TxnRef");

        if (orderId == null || orderId.isBlank()) {
            return "ORDER_NOT_FOUND";
        }

        /*
         * lockById cần sử dụng pessimistic write lock ở repository
         * để tránh IPN và browser return xử lý cùng một đơn đồng thời.
         */
        PaymentOrder order = orders.lockById(orderId).orElse(null);

        if (order == null) {
            return "ORDER_NOT_FOUND";
        }

        String response = params.getOrDefault("vnp_ResponseCode", "");
        String transaction = params.getOrDefault("vnp_TransactionNo", "");
        String transactionStatus =
                params.getOrDefault("vnp_TransactionStatus", "");
        String vnpAmount = params.get("vnp_Amount");

        String expectedAmount = order.getAmount()
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .toPlainString();

        /*
         * Không tin số tiền gửi từ callback nếu nó không khớp
         * số tiền đã lưu trong PaymentOrder.
         */
        if (vnpAmount == null || !expectedAmount.equals(vnpAmount)) {
            return "INVALID_AMOUNT";
        }

        /*
         * Idempotency: callback thành công lặp lại không được
         * trừ điểm hoặc cộng điểm thưởng lần thứ hai.
         */
        if ("PAID".equalsIgnoreCase(order.getStatus())) {
            return "00";
        }

        /*
         * Chỉ xử lý ví điểm khi cả ResponseCode và TransactionStatus
         * đều là "00".
         */
        if (!"00".equals(response)
                || !"00".equals(transactionStatus)) {
            order.markFailed(transaction, response);
            return "PAYMENT_FAILED";
        }

        String phone = normalizePhone(order.getCustomerPhone() == null ? "" : order.getCustomerPhone());
        if (order.getTableId() == null || order.getTableId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Giao dịch không được liên kết với bàn POS.");
        }

        /*
         * Dùng đúng nghiệp vụ checkout của tiền mặt để tạo hóa đơn,
         * ghi nhận voucher, trừ điểm, cộng điểm thưởng và giải phóng bàn.
         * Nếu số tiền checkout không khớp giao dịch VNPay, ném exception để
         * rollback toàn bộ thay đổi trong transaction.
         */
        var checkout = orderService.checkoutCash(
                order.getTableId(),
                new CashCheckoutRequest(
                        phone.isBlank() ? null : phone,
                        order.getVoucherCode(),
                        order.getPointsUsed() != null && order.getPointsUsed().compareTo(ZERO) > 0
                )
        );

        BigDecimal checkoutTotal = checkout.total() == null ? ZERO : checkout.total();
        if (checkoutTotal.compareTo(order.getAmount()) != 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Số tiền checkout không khớp số tiền giao dịch VNPay đã lưu.");
        }

        BigDecimal pointsAwarded = checkout.pointsAwarded() == null ? ZERO : checkout.pointsAwarded();
        order.markPointsDeducted();
        order.markPaid(transaction, response, pointsAwarded);

        return "00";
    }

    /**
     * Tra cứu số dư ví điểm theo số điện thoại.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getLoyaltyBalance(String rawPhone) {
        String phone = normalizePhone(
                rawPhone == null ? "" : rawPhone.trim()
        );

        if (phone.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Vui lòng nhập số điện thoại để tra cứu điểm."
            );
        }

        if (!phone.matches("^[0-9+]{8,20}$")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Số điện thoại không hợp lệ."
            );
        }

        BigDecimal balance = loyaltyAccounts
                .findByCustomerPhone(phone)
                .map(LoyaltyAccount::getPointsBalance)
                .orElse(ZERO);

        return Map.of(
                "phoneNumber", phone,
                "pointsBalance", balance == null ? ZERO : balance
        );
    }

    /**
     * Lấy tỷ lệ tích điểm hiện tại.
     */
    @Transactional(readOnly = true)
    public LoyaltyRateResponse getRate() {
        LoyaltySettings loyaltySettings = settings
                .findById(DEFAULT_SETTINGS_ID)
                .orElse(new LoyaltySettings(
                        DEFAULT_SETTINGS_ID,
                        new BigDecimal("5.000")
                ));

        return LoyaltyRateResponse.from(loyaltySettings);
    }

    /**
     * Cập nhật tỷ lệ tích điểm.
     */
    @Transactional
    public LoyaltyRateResponse updateRate(LoyaltyRateRequest request) {
        if (request == null || request.earnRatePercent() == null
                || request.earnRatePercent().compareTo(ZERO) < 0
                || request.earnRatePercent().compareTo(ONE_HUNDRED) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Tỷ lệ tích điểm phải nằm trong khoảng từ 0% đến 100%."
            );
        }

        LoyaltySettings loyaltySettings = settings
                .findById(DEFAULT_SETTINGS_ID)
                .orElseGet(() -> settings.save(
                        new LoyaltySettings(
                                DEFAULT_SETTINGS_ID,
                                new BigDecimal("5.000")
                        )
                ));

        loyaltySettings.updateRate(request.earnRatePercent());

        return LoyaltyRateResponse.from(
                settings.save(loyaltySettings)
        );
    }

    /**
     * Tra cứu trạng thái giao dịch.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getOrder(String id) {
        PaymentOrder paymentOrder = orders.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy giao dịch."
                ));

        return Map.of(
                "orderId", paymentOrder.getId(),
                "tableId", paymentOrder.getTableId() == null ? "" : paymentOrder.getTableId(),
                "status", paymentOrder.getStatus(),
                "amount", paymentOrder.getAmount(),
                "phoneNumber",
                paymentOrder.getCustomerPhone() == null
                        ? ""
                        : paymentOrder.getCustomerPhone(),
                "pointsAwarded",
                paymentOrder.getPointsAwarded() == null
                        ? ZERO
                        : paymentOrder.getPointsAwarded(),
                "pointsUsed",
                paymentOrder.getPointsUsed() == null
                        ? ZERO
                        : paymentOrder.getPointsUsed(),
                "responseCode",
                paymentOrder.getVnpResponseCode() == null
                        ? ""
                        : paymentOrder.getVnpResponseCode(),
                "transactionNo",
                paymentOrder.getVnpTransactionNo() == null
                        ? ""
                        : paymentOrder.getVnpTransactionNo()
        );
    }

    private String normalizePhone(String phone) {
        if (phone == null) {
            return "";
        }

        return phone.replaceAll("[\\s()-]", "");
    }

    private String normalizeIp(String ip) {
        if (ip == null || ip.isBlank()
                || "0:0:0:0:0:0:0:1".equals(ip)
                || "::1".equals(ip)) {
            return "127.0.0.1";
        }

        return ip;
    }

    /**
     * Tạo query string theo thứ tự key tăng dần để ký VNPay.
     */
    private String toQuery(Map<String, String> params) {
        StringJoiner joiner = new StringJoiner("&");

        params.entrySet()
                .stream()
                .filter(entry -> entry.getValue() != null
                        && !entry.getValue().isBlank())
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> joiner.add(
                        URLEncoder.encode(
                                entry.getKey(),
                                StandardCharsets.UTF_8
                        )
                                + "="
                                + URLEncoder.encode(
                                entry.getValue(),
                                StandardCharsets.UTF_8
                        )
                ));

        return joiner.toString();
    }

    /**
     * Tạo chữ ký HMAC-SHA512.
     */
    private String hmacSha512(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");

            mac.init(new SecretKeySpec(
                    key.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA512"
            ));

            byte[] bytes = mac.doFinal(
                    data.getBytes(StandardCharsets.UTF_8)
            );

            StringBuilder result = new StringBuilder();

            for (byte value : bytes) {
                result.append(String.format("%02x", value));
            }

            return result.toString();
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Không thể tạo chữ ký VNPay.",
                    exception
            );
        }
    }

    /**
     * So sánh chữ ký theo cách hạn chế timing attack.
     */
    private boolean constantTimeEquals(String first, String second) {
        if (first == null || second == null) {
            return false;
        }

        byte[] firstBytes = first.toLowerCase(Locale.ROOT)
                .getBytes(StandardCharsets.UTF_8);

        byte[] secondBytes = second.toLowerCase(Locale.ROOT)
                .getBytes(StandardCharsets.UTF_8);

        return java.security.MessageDigest.isEqual(
                firstBytes,
                secondBytes
        );
    }
}
