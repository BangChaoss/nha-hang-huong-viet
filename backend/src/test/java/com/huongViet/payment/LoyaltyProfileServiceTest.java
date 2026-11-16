package com.huongViet.payment;

import com.huongViet.auth.User;
import com.huongViet.auth.UserRole;
import com.huongViet.voucher.Voucher;
import com.huongViet.voucher.VoucherRepository;
import com.huongViet.voucher.VoucherUsageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoyaltyProfileServiceTest {

    @Mock
    private LoyaltyAccountRepository accounts;

    @Mock
    private VoucherRepository vouchers;

    @Mock
    private VoucherUsageRepository voucherUsages;

    private LoyaltyProfileService loyaltyProfileService;

    @BeforeEach
    void setUp() {
        loyaltyProfileService = new LoyaltyProfileService(accounts, vouchers, voucherUsages);
    }

    private Voucher buildVoucher(String id, String code, boolean active,
                                 LocalDateTime start, LocalDateTime end,
                                 Integer totalQty, Integer usedQty, Integer maxUsesPerCustomer) {
        Voucher v = new Voucher(
                id, code, "Tiêu đề " + code, "Mô tả",
                Voucher.DiscountType.PERCENTAGE, new BigDecimal("10"),
                null, BigDecimal.ZERO, start, end, totalQty, maxUsesPerCustomer
        );
        v.setActive(active);
        v.setUsedQuantity(usedQty);
        return v;
    }

    @Test
    @DisplayName("Lỗi 401 UNAUTHORIZED khi người dùng chưa đăng nhập (user = null)")
    void testGetProfile_Unauthenticated() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> loyaltyProfileService.getProfile(null));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    @DisplayName("Lấy thông tin tích điểm: Trả về số dư điểm và danh sách voucher còn hiệu lực")
    void testGetProfile_SuccessWithFilters() {
        User user = new User("user-1", "khachhang", "khach@test.com", "0901234567", "hashed", UserRole.CUSTOMER);

        LoyaltyAccount account = new LoyaltyAccount("acc-1", "0901234567");
        account.addPoints(new BigDecimal("1250"));
        when(accounts.findByCustomerPhone("0901234567")).thenReturn(Optional.of(account));

        LocalDateTime now = LocalDateTime.now();

        // 1. Voucher hợp lệ 1 - hết hạn sau 2 ngày
        Voucher v1 = buildVoucher("v1", "VALID_SOON", true, now.minusDays(1), now.plusDays(2), 100, 10, 1);
        // 2. Voucher hợp lệ 2 - hết hạn sau 5 ngày
        Voucher v2 = buildVoucher("v2", "VALID_LATER", true, now.minusDays(1), now.plusDays(5), 100, 5, 2);
        // 3. Voucher bị tắt (active = false) -> phải bị lọc bỏ
        Voucher v3 = buildVoucher("v3", "INACTIVE", false, now.minusDays(1), now.plusDays(2), 100, 0, 1);
        // 4. Voucher đã hết hạn -> phải bị lọc bỏ
        Voucher v4 = buildVoucher("v4", "EXPIRED", true, now.minusDays(10), now.minusDays(1), 100, 0, 1);
        // 5. Voucher chưa tới ngày bắt đầu -> phải bị lọc bỏ
        Voucher v5 = buildVoucher("v5", "FUTURE", true, now.plusDays(2), now.plusDays(10), 100, 0, 1);
        // 6. Voucher đã hết số lượng phát hành (used >= total) -> phải bị lọc bỏ
        Voucher v6 = buildVoucher("v6", "OUT_OF_STOCK", true, now.minusDays(1), now.plusDays(2), 10, 10, 1);
        // 7. Voucher khách đã dùng hết lượt cho phép (usedByPhone >= maxUsesPerCustomer) -> phải bị lọc bỏ
        Voucher v7 = buildVoucher("v7", "MAX_USED", true, now.minusDays(1), now.plusDays(2), 100, 5, 1);

        when(vouchers.findAllByDeletedAtIsNullOrderByCreatedAtDesc())
                .thenReturn(List.of(v1, v2, v3, v4, v5, v6, v7));

        when(voucherUsages.countByVoucherAndPhone("v1", "0901234567")).thenReturn(0L);
        when(voucherUsages.countByVoucherAndPhone("v2", "0901234567")).thenReturn(1L); // max là 2 -> vẫn còn 1 lượt
        when(voucherUsages.countByVoucherAndPhone("v7", "0901234567")).thenReturn(1L); // max là 1 -> hết lượt

        LoyaltyProfileResponse response = loyaltyProfileService.getProfile(user);

        assertEquals("0901234567", response.phoneNumber());
        assertEquals(new BigDecimal("1250"), response.pointsBalance());
        assertEquals(2, response.vouchers().size());

        // Kiểm tra sắp xếp theo endDate tăng dần (VALID_SOON trước, sau đó tới VALID_LATER)
        assertEquals("VALID_SOON", response.vouchers().get(0).voucherCode());
        assertEquals("VALID_LATER", response.vouchers().get(1).voucherCode());
    }

    @Test
    @DisplayName("Khách hàng mới chưa có tài khoản ví điểm: trả về số dư 0")
    void testGetProfile_NewCustomerWithoutAccount() {
        User user = new User("user-2", "newbie", "new@test.com", "0988776655", "hashed", UserRole.CUSTOMER);
        when(accounts.findByCustomerPhone("0988776655")).thenReturn(Optional.empty());
        when(vouchers.findAllByDeletedAtIsNullOrderByCreatedAtDesc()).thenReturn(List.of());

        LoyaltyProfileResponse response = loyaltyProfileService.getProfile(user);

        assertEquals("0988776655", response.phoneNumber());
        assertEquals(BigDecimal.ZERO, response.pointsBalance());
        assertTrue(response.vouchers().isEmpty());
    }

    @Test
    @DisplayName("Người dùng không có số điện thoại (phone = null): Trả về số dư 0 và SĐT rỗng")
    void testGetProfile_UserWithoutPhoneNumber() {
        User user = new User("user-no-phone", "nophone", "nophone@test.com", null, "hashed", UserRole.CUSTOMER);
        when(accounts.findByCustomerPhone("")).thenReturn(Optional.empty());
        when(vouchers.findAllByDeletedAtIsNullOrderByCreatedAtDesc()).thenReturn(List.of());

        LoyaltyProfileResponse response = loyaltyProfileService.getProfile(user);

        assertEquals("", response.phoneNumber());
        assertEquals(BigDecimal.ZERO, response.pointsBalance());
        assertTrue(response.vouchers().isEmpty());
    }
}
