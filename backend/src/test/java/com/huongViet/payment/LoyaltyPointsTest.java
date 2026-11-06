package com.huongViet.payment;

import com.huongViet.order.OrderRepository;
import com.huongViet.order.OrderService;
import com.huongViet.table.TableRepository;
import com.huongViet.voucher.VoucherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoyaltyPointsTest {

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
    }

    @Nested
    @DisplayName("Thực thể LoyaltyAccount (Entity Tests)")
    class LoyaltyAccountEntityTests {

        @Test
        @DisplayName("Cộng điểm tích lũy vào tài khoản (addPoints)")
        void testAddPoints() {
            LoyaltyAccount account = new LoyaltyAccount("acc-1", "0901234567");
            assertEquals(BigDecimal.ZERO, account.getPointsBalance());

            account.addPoints(new BigDecimal("1500"));
            assertEquals(new BigDecimal("1500"), account.getPointsBalance());

            account.addPoints(new BigDecimal("500"));
            assertEquals(new BigDecimal("2000"), account.getPointsBalance());
        }

        @Test
        @DisplayName("Trừ điểm khi tiêu dùng (redeemPoints)")
        void testRedeemPoints_Success() {
            LoyaltyAccount account = new LoyaltyAccount("acc-1", "0901234567");
            account.addPoints(new BigDecimal("1000"));

            account.redeemPoints(new BigDecimal("400"));
            assertEquals(new BigDecimal("600"), account.getPointsBalance());
        }

        @Test
        @DisplayName("Trừ điểm vượt quá số dư ném ngoại lệ IllegalArgumentException")
        void testRedeemPoints_InsufficientBalance() {
            LoyaltyAccount account = new LoyaltyAccount("acc-1", "0901234567");
            account.addPoints(new BigDecimal("300"));

            assertThrows(IllegalArgumentException.class, () -> account.redeemPoints(new BigDecimal("500")));
        }

        @Test
        @DisplayName("Trừ số điểm âm ném ngoại lệ IllegalArgumentException")
        void testRedeemPoints_NegativePoints() {
            LoyaltyAccount account = new LoyaltyAccount("acc-1", "0901234567");
            assertThrows(IllegalArgumentException.class, () -> account.redeemPoints(new BigDecimal("-100")));
        }
    }

    @Nested
    @DisplayName("Tra cứu số dư điểm (getLoyaltyBalance)")
    class GetLoyaltyBalanceTests {

        @Test
        @DisplayName("Lỗi 400 khi số điện thoại để trống")
        void testGetLoyaltyBalance_BlankPhone() {
            ResponseStatusException ex1 = assertThrows(ResponseStatusException.class,
                    () -> paymentService.getLoyaltyBalance(""));
            assertEquals(HttpStatus.BAD_REQUEST, ex1.getStatusCode());

            ResponseStatusException ex2 = assertThrows(ResponseStatusException.class,
                    () -> paymentService.getLoyaltyBalance(null));
            assertEquals(HttpStatus.BAD_REQUEST, ex2.getStatusCode());
        }

        @Test
        @DisplayName("Lỗi 400 khi định dạng số điện thoại không hợp lệ")
        void testGetLoyaltyBalance_InvalidPhoneFormat() {
            // Chứa ký tự chữ cái
            ResponseStatusException ex1 = assertThrows(ResponseStatusException.class,
                    () -> paymentService.getLoyaltyBalance("0901abcd56"));
            assertEquals(HttpStatus.BAD_REQUEST, ex1.getStatusCode());

            // Quá ngắn (< 8 số)
            ResponseStatusException ex2 = assertThrows(ResponseStatusException.class,
                    () -> paymentService.getLoyaltyBalance("12345"));
            assertEquals(HttpStatus.BAD_REQUEST, ex2.getStatusCode());
        }

        @Test
        @DisplayName("Chuẩn hóa số điện thoại chứa khoảng trắng và dấu gạch nối")
        void testGetLoyaltyBalance_PhoneNormalization() {
            LoyaltyAccount account = new LoyaltyAccount("acc-1", "0901234567");
            account.addPoints(new BigDecimal("800"));
            when(loyaltyAccountRepository.findByCustomerPhone("0901234567"))
                    .thenReturn(Optional.of(account));
            when(loyaltySettingsRepository.findById("default"))
                    .thenReturn(Optional.of(new LoyaltySettings("default", new BigDecimal("5.000"), BigDecimal.ONE)));

            Map<String, Object> result = paymentService.getLoyaltyBalance(" 0901-234-567 ");
            assertEquals("0901234567", result.get("phoneNumber"));
            assertEquals(new BigDecimal("800"), result.get("pointsBalance"));
        }

        @Test
        @DisplayName("Khách hàng chưa có ví điểm thì trả về số dư bằng 0")
        void testGetLoyaltyBalance_AccountNotFoundReturnsZero() {
            when(loyaltyAccountRepository.findByCustomerPhone("0988776655"))
                    .thenReturn(Optional.empty());
            when(loyaltySettingsRepository.findById("default"))
                    .thenReturn(Optional.of(new LoyaltySettings("default", new BigDecimal("5.000"), BigDecimal.ONE)));

            Map<String, Object> result = paymentService.getLoyaltyBalance("0988776655");
            assertEquals("0988776655", result.get("phoneNumber"));
            assertEquals(BigDecimal.ZERO, result.get("pointsBalance"));
        }
    }

    @Nested
    @DisplayName("Cấu hình tỷ lệ tích điểm (getRate & updateRate)")
    class LoyaltyRateTests {

        @Test
        @DisplayName("Lấy tỷ lệ tích điểm mặc định khi chưa có trong CSDL (5.000%)")
        void testGetRate_DefaultWhenNotFound() {
            when(loyaltySettingsRepository.findById("default")).thenReturn(Optional.empty());
            when(loyaltySettingsRepository.save(any(LoyaltySettings.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            LoyaltyRateResponse response = paymentService.getRate();
            assertNotNull(response);
            assertEquals(new BigDecimal("5.000"), response.earnRatePercent());
        }

        @Test
        @DisplayName("Lấy tỷ lệ tích điểm đã được cấu hình trong CSDL")
        void testGetRate_Configured() {
            LoyaltySettings settings = new LoyaltySettings("default", new BigDecimal("10.000"));
            when(loyaltySettingsRepository.findById("default")).thenReturn(Optional.of(settings));

            LoyaltyRateResponse response = paymentService.getRate();
            assertNotNull(response);
            assertEquals(new BigDecimal("10.000"), response.earnRatePercent());
        }

        @Test
        @DisplayName("Lỗi 400 khi cập nhật tỷ lệ null, âm hoặc lớn hơn 100%")
        void testUpdateRate_InvalidValues() {
            assertThrows(ResponseStatusException.class,
                    () -> paymentService.updateRate(null));

            assertThrows(ResponseStatusException.class,
                    () -> paymentService.updateRate(new LoyaltyRateRequest(null)));

            assertThrows(ResponseStatusException.class,
                    () -> paymentService.updateRate(new LoyaltyRateRequest(new BigDecimal("-1.000"))));

            assertThrows(ResponseStatusException.class,
                    () -> paymentService.updateRate(new LoyaltyRateRequest(new BigDecimal("100.500"))));
        }

        @Test
        @DisplayName("Cập nhật tỷ lệ tích điểm thành công")
        void testUpdateRate_Success() {
            LoyaltySettings existing = new LoyaltySettings("default", new BigDecimal("5.000"));
            when(loyaltySettingsRepository.findById("default")).thenReturn(Optional.of(existing));
            when(loyaltySettingsRepository.save(any(LoyaltySettings.class))).thenAnswer(inv -> inv.getArgument(0));

            LoyaltyRateResponse response = paymentService.updateRate(new LoyaltyRateRequest(new BigDecimal("7.500")));
            assertEquals(new BigDecimal("7.500"), response.earnRatePercent());
            assertEquals(new BigDecimal("7.500"), existing.getEarnRatePercent());
        }
    }
}
