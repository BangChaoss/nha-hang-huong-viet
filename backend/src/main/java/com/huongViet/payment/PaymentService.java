package com.huongViet.payment;

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
import java.util.*;

@Service
public class PaymentService {
    private final PaymentOrderRepository orders;
    private final LoyaltyAccountRepository loyaltyAccounts;
    private final LoyaltySettingsRepository settings;
    private final VoucherService voucherService;
    @Value("${VNPAY_SANDBOX_TMN_CODE:${vnpay.tmn-code:}}") private String tmnCode;
    @Value("${VNPAY_SANDBOX_HASH_SECRET:${vnpay.hash-secret:}}") private String hashSecret;
    @Value("${vnpay.pay-url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}") private String payUrl;
    @Value("${vnpay.return-url:http://localhost:8080/api/payments/vnpay/return}") private String returnUrl;
    @Value("${vnpay.ipn-url:http://localhost:8080/api/payments/vnpay/ipn}") private String ipnUrl;
    @Value("${vnpay.version:2.1.0}") private String version;
    @Value("${vnpay.command:pay}") private String command;
    @Value("${vnpay.order-type:other}") private String orderType;

    public PaymentService(PaymentOrderRepository orders, LoyaltyAccountRepository loyaltyAccounts,
                         LoyaltySettingsRepository settings, VoucherService voucherService) {
        this.orders=orders; this.loyaltyAccounts=loyaltyAccounts; this.settings=settings; this.voucherService=voucherService;
    }

    @Transactional
    public Map<String,String> createPayment(VnpayCreateRequest req, String clientIp) {
        if (tmnCode.isBlank() || hashSecret.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Chưa cấu hình VNPAY_SANDBOX_TMN_CODE và VNPAY_SANDBOX_HASH_SECRET.");
        }
        String phone=normalizePhone(req.phoneNumber()==null ? "" : req.phoneNumber());
        String code=req.voucherCode()==null ? "" : req.voucherCode().trim().toUpperCase(Locale.ROOT);
        if ((!code.isBlank()) && phone.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nhập số điện thoại để sử dụng voucher.");
        }
        BigDecimal subtotal=req.subtotal().setScale(2, RoundingMode.HALF_UP);
        BigDecimal discount=BigDecimal.ZERO;
        if (!code.isBlank()) {
            VoucherValidationResponse result=voucherService.validate(new VoucherValidationRequest(code, phone, subtotal));
            if (!result.valid()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, result.message());
            discount=result.discountAmount();
        }
        BigDecimal amount=subtotal.subtract(discount).setScale(2, RoundingMode.HALF_UP);
        if (amount.compareTo(BigDecimal.valueOf(1000)) < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số tiền thanh toán tối thiểu là 1.000 VND.");
        String orderId=UUID.randomUUID().toString();
        orders.save(new PaymentOrder(orderId, phone, subtotal, discount, amount, code.isBlank()?null:code));

        Map<String,String> params=new TreeMap<>();
        // VNPAY 2.1.0 contract: these protocol values must not drift with configuration.
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", tmnCode);
        params.put("vnp_Amount", amount.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).toPlainString());
        params.put("vnp_CurrCode", "VND"); params.put("vnp_TxnRef", orderId);
        params.put("vnp_OrderInfo", "Thanh toan don hang " + orderId);
        params.put("vnp_OrderType", orderType); params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", returnUrl); params.put("vnp_IpAddr", normalizeIp(clientIp));
        params.put("vnp_CreateDate", LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        params.put("vnp_ExpireDate", LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusMinutes(15).format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        String query=toQuery(params);
        return Map.of("orderId",orderId,"paymentUrl",payUrl+"?"+query+"&vnp_SecureHash="+hmacSha512(hashSecret,query));
    }

    public boolean verify(Map<String,String> input) {
        String received=input.get("vnp_SecureHash");
        if (received==null || !received.matches("(?i)^[0-9a-f]{128}$") || hashSecret.isBlank()) return false;
        Map<String,String> params=new TreeMap<>(input);
        params.remove("vnp_SecureHash"); params.remove("vnp_SecureHashType");
        return constantTimeEquals(hmacSha512(hashSecret, toQuery(params)), received);
    }

    @Transactional
    public String processCallback(Map<String,String> params) {
        if (!verify(params)) return "INVALID_SIGNATURE";
        String orderId=params.get("vnp_TxnRef");
        if (orderId==null) return "ORDER_NOT_FOUND";
        PaymentOrder order=orders.lockById(orderId).orElse(null);
        if (order==null) return "ORDER_NOT_FOUND";
        String response=params.getOrDefault("vnp_ResponseCode", "");
        String transaction=params.getOrDefault("vnp_TransactionNo", "");
        String txnStatus=params.getOrDefault("vnp_TransactionStatus", "");
        String amount=params.get("vnp_Amount");
        String expected=order.getAmount().multiply(BigDecimal.valueOf(100)).setScale(0,RoundingMode.HALF_UP).toPlainString();
        if (!expected.equals(amount)) return "INVALID_AMOUNT";
        // IPN and browser return may race; a paid order must never earn points twice.
        if ("PAID".equals(order.getStatus())) return "00";
        if ("00".equals(response) && "00".equals(txnStatus)) {
            // PaymentOrder hiện không liên kết với orders/invoices. Không ghi voucher_usages tại callback
            // bằng invoiceId giả: FK voucher_usages.invoice_id bắt buộc tham chiếu invoices.id thật.
            // Cần gắn PaymentOrder với invoiceId khi luồng VNPay được khởi tạo từ hóa đơn POS.
            BigDecimal points = BigDecimal.ZERO;
            if (order.getCustomerPhone() != null && !order.getCustomerPhone().isBlank()) {
                BigDecimal rate=settings.findById("default")
                    .orElseGet(() -> settings.save(new LoyaltySettings("default", new BigDecimal("5.000"))))
                    .getEarnRatePercent();
                points=order.getAmount().multiply(rate).divide(BigDecimal.valueOf(100),2,RoundingMode.HALF_UP);
                LoyaltyAccount account=loyaltyAccounts.findByCustomerPhone(order.getCustomerPhone())
                    .orElseGet(() -> new LoyaltyAccount(UUID.randomUUID().toString(), order.getCustomerPhone()));
                account.addPoints(points); loyaltyAccounts.save(account);
            }
            order.markPaid(transaction,response,points);
        } else {
            order.markFailed(transaction, response);
            return "PAYMENT_FAILED";
        }
        return "00";
    }

    @Transactional(readOnly=true)
    public Map<String,Object> getLoyaltyBalance(String rawPhone) {
        String phone = normalizePhone(rawPhone == null ? "" : rawPhone.trim());
        if (phone.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng nhập số điện thoại để tra cứu điểm.");
        }
        if (!phone.matches("^[0-9+]{8,20}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số điện thoại không hợp lệ.");
        }
        BigDecimal balance = loyaltyAccounts.findByCustomerPhone(phone)
            .map(LoyaltyAccount::getPointsBalance)
            .orElse(BigDecimal.ZERO);
        return Map.of("phoneNumber", phone, "pointsBalance", balance);
    }

    @Transactional(readOnly=true)
    public LoyaltyRateResponse getRate() {
        return LoyaltyRateResponse.from(settings.findById("default")
            .orElse(new LoyaltySettings("default",new BigDecimal("5.000"))));
    }
    @Transactional
    public LoyaltyRateResponse updateRate(LoyaltyRateRequest request) {
        LoyaltySettings s=settings.findById("default")
            .orElseGet(() -> settings.save(new LoyaltySettings("default",new BigDecimal("5.000"))));
        s.updateRate(request.earnRatePercent());
        return LoyaltyRateResponse.from(settings.save(s));
    }
    @Transactional(readOnly=true)
    public Map<String,Object> getOrder(String id) {
        PaymentOrder p=orders.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy giao dịch."));
        return Map.of("orderId",p.getId(),"status",p.getStatus(),"amount",p.getAmount(),"phoneNumber",p.getCustomerPhone(),
            "pointsAwarded",p.getPointsAwarded(),"responseCode",p.getVnpResponseCode()==null?"":p.getVnpResponseCode());
    }
    private String normalizePhone(String p){return p.replaceAll("[\\s()-]","");}
    private String normalizeIp(String ip){return ip==null||ip.isBlank()||"0:0:0:0:0:0:0:1".equals(ip)?"127.0.0.1":ip;}
    private String toQuery(Map<String,String> params) {
        StringJoiner joiner=new StringJoiner("&");
        params.entrySet().stream().filter(e->e.getValue()!=null && !e.getValue().isBlank()).sorted(Map.Entry.comparingByKey())
            .forEach(e->joiner.add(URLEncoder.encode(e.getKey(),StandardCharsets.UTF_8)+"="+URLEncoder.encode(e.getValue(),StandardCharsets.UTF_8)));
        return joiner.toString();
    }
    private String hmacSha512(String key,String data) {
        try { Mac mac=Mac.getInstance("HmacSHA512"); mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8),"HmacSHA512"));
            byte[] bytes=mac.doFinal(data.getBytes(StandardCharsets.UTF_8)); StringBuilder out=new StringBuilder();
            for(byte b:bytes) out.append(String.format("%02x",b)); return out.toString();
        } catch(Exception e){throw new IllegalStateException("Không thể tạo chữ ký VNPAY.",e);}
    }
    private boolean constantTimeEquals(String a,String b){return java.security.MessageDigest.isEqual(a.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8),b.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));}
}
