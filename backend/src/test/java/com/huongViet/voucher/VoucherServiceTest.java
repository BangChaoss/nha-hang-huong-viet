package com.huongViet.voucher;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VoucherServiceTest {

    @Mock
    private VoucherRepository repository;

    @Mock
    private VoucherUsageRepository usageRepository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private VoucherService voucherService;

    @BeforeEach
    void setUp() {
        voucherService = new VoucherService(repository, usageRepository, jdbcTemplate);
    }

    private Voucher createSampleVoucher(String id, String code, Voucher.DiscountType type,
                                       BigDecimal val, BigDecimal maxDiscount, BigDecimal minOrder,
                                       int daysBefore, int daysAfter, Integer totalQty, Integer usedQty,
                                       Integer maxUsesPerCustomer, boolean active) {
        Voucher v = new Voucher(
                id,
                code,
                "Tiêu đề " + code,
                "Mô tả " + code,
                type,
                val,
                maxDiscount,
                minOrder,
                LocalDateTime.now().minusDays(daysBefore),
                LocalDateTime.now().plusDays(daysAfter),
                totalQty,
                maxUsesPerCustomer
        );
        v.setActive(active);
        v.setUsedQuantity(usedQty);
        return v;
    }

    @Nested
    @DisplayName("Danh sách voucher (list)")
    class ListTests {
        @Test
        @DisplayName("list trả về danh sách voucher chưa bị xóa")
        void testList() {
            Voucher v1 = createSampleVoucher("1", "V1", Voucher.DiscountType.PERCENTAGE,
                    new BigDecimal("10"), null, BigDecimal.ZERO, 1, 5, 100, 0, 1, true);
            Voucher v2 = createSampleVoucher("2", "V2", Voucher.DiscountType.FIXED_AMOUNT,
                    new BigDecimal("50000"), null, BigDecimal.ZERO, 1, 5, 50, 0, 1, true);

            when(repository.findAllByDeletedAtIsNullOrderByCreatedAtDesc())
                    .thenReturn(List.of(v1, v2));

            List<VoucherResponse> results = voucherService.list();
            assertEquals(2, results.size());
            assertEquals("V1", results.get(0).voucherCode());
            assertEquals("V2", results.get(1).voucherCode());
        }
    }

    @Nested
    @DisplayName("Tạo voucher (create)")
    class CreateTests {

        @Test
        @DisplayName("Lỗi 400 khi mã voucher để trống")
        void testCreate_BlankCode() {
            VoucherRequest req = new VoucherRequest(
                    " ", "Tiêu đề", "Mô tả", Voucher.DiscountType.PERCENTAGE,
                    new BigDecimal("10"), null, BigDecimal.ZERO,
                    LocalDateTime.now(), LocalDateTime.now().plusDays(5), 100, 1
            );
            ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> voucherService.create(req));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        }

        @Test
        @DisplayName("Lỗi 409 khi mã voucher đã tồn tại")
        void testCreate_DuplicateCode() {
            VoucherRequest req = new VoucherRequest(
                    "SALE10", "Tiêu đề", "Mô tả", Voucher.DiscountType.PERCENTAGE,
                    new BigDecimal("10"), null, BigDecimal.ZERO,
                    LocalDateTime.now(), LocalDateTime.now().plusDays(5), 100, 1
            );
            when(repository.existsByVoucherCodeIgnoreCaseAndDeletedAtIsNull("SALE10")).thenReturn(true);

            ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> voucherService.create(req));
            assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        }

        @Test
        @DisplayName("Lỗi 400 khi ngày kết thúc trước ngày bắt đầu")
        void testCreate_EndDateBeforeStartDate() {
            VoucherRequest req = new VoucherRequest(
                    "SUMMER", "Tiêu đề", "Mô tả", Voucher.DiscountType.PERCENTAGE,
                    new BigDecimal("10"), null, BigDecimal.ZERO,
                    LocalDateTime.now().plusDays(5), LocalDateTime.now().plusDays(2), 100, 1
            );
            ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> voucherService.create(req));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
            assertTrue(ex.getReason().contains("Ngày kết thúc không được trước ngày bắt đầu"));
        }

        @Test
        @DisplayName("Lỗi 400 khi giảm giá theo % không nằm trong khoảng (0, 100]")
        void testCreate_InvalidPercentage() {
            VoucherRequest reqOver = new VoucherRequest(
                    "SUMMER120", "Tiêu đề", "Mô tả", Voucher.DiscountType.PERCENTAGE,
                    new BigDecimal("120"), null, BigDecimal.ZERO,
                    LocalDateTime.now(), LocalDateTime.now().plusDays(5), 100, 1
            );
            assertThrows(ResponseStatusException.class, () -> voucherService.create(reqOver));

            VoucherRequest reqZero = new VoucherRequest(
                    "SUMMER0", "Tiêu đề", "Mô tả", Voucher.DiscountType.PERCENTAGE,
                    BigDecimal.ZERO, null, BigDecimal.ZERO,
                    LocalDateTime.now(), LocalDateTime.now().plusDays(5), 100, 1
            );
            assertThrows(ResponseStatusException.class, () -> voucherService.create(reqZero));
        }

        @Test
        @DisplayName("Lỗi 400 khi giảm tiền cố định có cài đặt maxDiscountAmount hoặc số tiền <= 0")
        void testCreate_InvalidFixedAmount() {
            VoucherRequest reqWithMax = new VoucherRequest(
                    "FIXEDMAX", "Tiêu đề", "Mô tả", Voucher.DiscountType.FIXED_AMOUNT,
                    new BigDecimal("50000"), new BigDecimal("100000"), BigDecimal.ZERO,
                    LocalDateTime.now(), LocalDateTime.now().plusDays(5), 100, 1
            );
            ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> voucherService.create(reqWithMax));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());

            VoucherRequest reqZero = new VoucherRequest(
                    "FIXEDZERO", "Tiêu đề", "Mô tả", Voucher.DiscountType.FIXED_AMOUNT,
                    BigDecimal.ZERO, null, BigDecimal.ZERO,
                    LocalDateTime.now(), LocalDateTime.now().plusDays(5), 100, 1
            );
            assertThrows(ResponseStatusException.class, () -> voucherService.create(reqZero));
        }

        @Test
        @DisplayName("Tạo voucher thành công: sinh mã số tăng tự động và lưu thành công")
        void testCreate_Success() {
            Voucher existing = createSampleVoucher("5", "OLD", Voucher.DiscountType.PERCENTAGE,
                    new BigDecimal("10"), null, BigDecimal.ZERO, 1, 5, 10, 0, 1, true);
            when(repository.findAll()).thenReturn(List.of(existing));

            VoucherRequest req = new VoucherRequest(
                    "welcome", "Chào mừng", "Mô tả", Voucher.DiscountType.PERCENTAGE,
                    new BigDecimal("15"), new BigDecimal("50000"), new BigDecimal("100000"),
                    LocalDateTime.now(), LocalDateTime.now().plusDays(10), 200, 2
            );

            when(repository.save(any(Voucher.class))).thenAnswer(inv -> inv.getArgument(0));

            VoucherResponse res = voucherService.create(req);
            assertEquals("WELCOME", res.voucherCode()); // Tự động uppercase
            assertEquals("6", res.id()); // 5 + 1 = 6
            assertEquals(new BigDecimal("15"), res.discountValue());
            assertEquals(200, res.totalQuantity());
        }
    }

    @Nested
    @DisplayName("Kiểm tra tính hợp lệ của voucher (validate)")
    class ValidateTests {

        @Test
        @DisplayName("Validate trả về invalid khi mã voucher rỗng")
        void testValidate_BlankCode() {
            VoucherValidationResponse res = voucherService.validate(
                    new VoucherValidationRequest("", "0901234567", new BigDecimal("100000"))
            );
            assertFalse(res.valid());
            assertEquals("Vui lòng nhập mã voucher.", res.message());
        }

        @Test
        @DisplayName("Validate trả về invalid khi voucher không tồn tại hoặc đã bị tắt")
        void testValidate_NotFoundOrInactive() {
            when(repository.findByVoucherCodeIgnoreCaseAndDeletedAtIsNull("UNKNOWN")).thenReturn(Optional.empty());

            VoucherValidationResponse res1 = voucherService.validate(
                    new VoucherValidationRequest("UNKNOWN", "0901234567", new BigDecimal("100000"))
            );
            assertFalse(res1.valid());
            assertTrue(res1.message().contains("không tồn tại"));

            Voucher inactive = createSampleVoucher("1", "INACTIVE", Voucher.DiscountType.PERCENTAGE,
                    new BigDecimal("10"), null, BigDecimal.ZERO, 1, 5, 10, 0, 1, false);
            when(repository.findByVoucherCodeIgnoreCaseAndDeletedAtIsNull("INACTIVE")).thenReturn(Optional.of(inactive));

            VoucherValidationResponse res2 = voucherService.validate(
                    new VoucherValidationRequest("INACTIVE", "0901234567", new BigDecimal("100000"))
            );
            assertFalse(res2.valid());
            assertTrue(res2.message().contains("không tồn tại hoặc đã bị tắt"));
        }

        @Test
        @DisplayName("Validate trả về invalid khi voucher hết hạn hoặc chưa đến ngày")
        void testValidate_OutOfDateRange() {
            // Voucher hết hạn (kết thúc hôm qua)
            Voucher expired = createSampleVoucher("1", "EXPIRED", Voucher.DiscountType.PERCENTAGE,
                    new BigDecimal("10"), null, BigDecimal.ZERO, 5, -1, 10, 0, 1, true);
            when(repository.findByVoucherCodeIgnoreCaseAndDeletedAtIsNull("EXPIRED")).thenReturn(Optional.of(expired));

            VoucherValidationResponse res = voucherService.validate(
                    new VoucherValidationRequest("EXPIRED", "0901234567", new BigDecimal("100000"))
            );
            assertFalse(res.valid());
            assertTrue(res.message().contains("chưa đến thời gian áp dụng hoặc đã hết hạn"));
        }

        @Test
        @DisplayName("Validate trả về invalid khi voucher đã hết lượt phát hành (used >= total)")
        void testValidate_ExhaustedQuantity() {
            Voucher full = createSampleVoucher("1", "FULL", Voucher.DiscountType.PERCENTAGE,
                    new BigDecimal("10"), null, BigDecimal.ZERO, 1, 5, 10, 10, 1, true);
            when(repository.findByVoucherCodeIgnoreCaseAndDeletedAtIsNull("FULL")).thenReturn(Optional.of(full));

            VoucherValidationResponse res = voucherService.validate(
                    new VoucherValidationRequest("FULL", "0901234567", new BigDecimal("100000"))
            );
            assertFalse(res.valid());
            assertEquals("Voucher đã hết lượt phát hành.", res.message());
        }

        @Test
        @DisplayName("Validate trả về invalid khi giá trị đơn hàng < minOrderAmount")
        void testValidate_BelowMinOrderAmount() {
            Voucher minOrder = createSampleVoucher("1", "MIN200", Voucher.DiscountType.PERCENTAGE,
                    new BigDecimal("10"), null, new BigDecimal("200000"), 1, 5, 10, 0, 1, true);
            when(repository.findByVoucherCodeIgnoreCaseAndDeletedAtIsNull("MIN200")).thenReturn(Optional.of(minOrder));

            VoucherValidationResponse res = voucherService.validate(
                    new VoucherValidationRequest("MIN200", "0901234567", new BigDecimal("150000"))
            );
            assertFalse(res.valid());
            assertEquals("Đơn hàng chưa đạt giá trị tối thiểu.", res.message());
        }

        @Test
        @DisplayName("Validate trả về invalid khi chưa nhập số điện thoại")
        void testValidate_MissingPhone() {
            Voucher v = createSampleVoucher("1", "VOUCHER", Voucher.DiscountType.PERCENTAGE,
                    new BigDecimal("10"), null, BigDecimal.ZERO, 1, 5, 10, 0, 1, true);
            when(repository.findByVoucherCodeIgnoreCaseAndDeletedAtIsNull("VOUCHER")).thenReturn(Optional.of(v));

            VoucherValidationResponse res = voucherService.validate(
                    new VoucherValidationRequest("VOUCHER", "", new BigDecimal("100000"))
            );
            assertFalse(res.valid());
            assertEquals("Vui lòng nhập số điện thoại để sử dụng voucher.", res.message());
        }

        @Test
        @DisplayName("Validate trả về invalid khi khách hàng đã dùng hết lượt cho phép (maxUsesPerCustomer)")
        void testValidate_MaxUsesExceeded() {
            Voucher v = createSampleVoucher("1", "MAX1", Voucher.DiscountType.PERCENTAGE,
                    new BigDecimal("10"), null, BigDecimal.ZERO, 1, 5, 10, 2, 1, true);
            when(repository.findByVoucherCodeIgnoreCaseAndDeletedAtIsNull("MAX1")).thenReturn(Optional.of(v));
            when(usageRepository.countByVoucherAndPhone("1", "0901234567")).thenReturn(1L);

            VoucherValidationResponse res = voucherService.validate(
                    new VoucherValidationRequest("MAX1", "0901234567", new BigDecimal("100000"))
            );
            assertFalse(res.valid());
            assertEquals("Số điện thoại này đã sử dụng hết lượt voucher cho phép.", res.message());
        }

        @Test
        @DisplayName("Validate thành công: Voucher % với giới hạn maxDiscountAmount")
        void testValidate_SuccessPercentageWithMaxDiscount() {
            // Giảm 20%, tối đa 30.000 VND
            Voucher v = createSampleVoucher("1", "PERCENT20", Voucher.DiscountType.PERCENTAGE,
                    new BigDecimal("20"), new BigDecimal("30000"), new BigDecimal("50000"), 1, 5, 100, 0, 2, true);
            when(repository.findByVoucherCodeIgnoreCaseAndDeletedAtIsNull("PERCENT20")).thenReturn(Optional.of(v));
            when(usageRepository.countByVoucherAndPhone("1", "0901234567")).thenReturn(0L);

            // Đơn hàng 200.000 -> 20% = 40.000 -> Giới hạn max là 30.000
            VoucherValidationResponse res = voucherService.validate(
                    new VoucherValidationRequest("PERCENT20", "0901234567", new BigDecimal("200000"))
            );
            assertTrue(res.valid());
            assertEquals("Voucher hợp lệ.", res.message());
            assertEquals(new BigDecimal("30000"), res.discountAmount());
        }

        @Test
        @DisplayName("Validate thành công: Voucher giảm tiền cố định FIXED_AMOUNT")
        void testValidate_SuccessFixedAmount() {
            // Giảm 50.000 VND
            Voucher v = createSampleVoucher("1", "FIXED50", Voucher.DiscountType.FIXED_AMOUNT,
                    new BigDecimal("50000"), null, new BigDecimal("100000"), 1, 5, 100, 0, 2, true);
            when(repository.findByVoucherCodeIgnoreCaseAndDeletedAtIsNull("FIXED50")).thenReturn(Optional.of(v));
            when(usageRepository.countByVoucherAndPhone("1", "0901234567")).thenReturn(0L);

            VoucherValidationResponse res = voucherService.validate(
                    new VoucherValidationRequest("FIXED50", "0901234567", new BigDecimal("120000"))
            );
            assertTrue(res.valid());
            assertEquals(new BigDecimal("50000"), res.discountAmount());
        }

        @Test
        @DisplayName("Validate không phân biệt chữ hoa thường (code 'save10' khớp 'SAVE10')")
        void testValidate_CaseInsensitiveCode() {
            Voucher v = createSampleVoucher("1", "SAVE10", Voucher.DiscountType.PERCENTAGE,
                    new BigDecimal("10"), null, BigDecimal.ZERO, 1, 5, 100, 0, 1, true);
            when(repository.findByVoucherCodeIgnoreCaseAndDeletedAtIsNull("SAVE10")).thenReturn(Optional.of(v));
            when(usageRepository.countByVoucherAndPhone("1", "0901234567")).thenReturn(0L);

            VoucherValidationResponse res = voucherService.validate(
                    new VoucherValidationRequest("save10", "0901234567", new BigDecimal("100000"))
            );
            assertTrue(res.valid());
            assertEquals(new BigDecimal("10000.00"), res.discountAmount());
        }

        @Test
        @DisplayName("Validate voucher phần trăm không có mức giảm tối đa (maxDiscountAmount = null)")
        void testValidate_NoMaxDiscountAmount() {
            Voucher v = createSampleVoucher("1", "NOCAP20", Voucher.DiscountType.PERCENTAGE,
                    new BigDecimal("20"), null, BigDecimal.ZERO, 1, 5, 100, 0, 1, true);
            when(repository.findByVoucherCodeIgnoreCaseAndDeletedAtIsNull("NOCAP20")).thenReturn(Optional.of(v));
            when(usageRepository.countByVoucherAndPhone("1", "0901234567")).thenReturn(0L);

            VoucherValidationResponse res = voucherService.validate(
                    new VoucherValidationRequest("NOCAP20", "0901234567", new BigDecimal("1000000"))
            );
            assertTrue(res.valid());
            assertEquals(new BigDecimal("200000.00"), res.discountAmount());
        }

        @Test
        @DisplayName("Validate voucher có minOrderAmount = null được áp dụng cho mọi giá trị đơn")
        void testValidate_NullMinOrderAmount_TreatedAsZero() {
            Voucher v = createSampleVoucher("1", "FREEFORALL", Voucher.DiscountType.FIXED_AMOUNT,
                    new BigDecimal("10000"), null, null, 1, 5, 100, 0, 1, true);
            when(repository.findByVoucherCodeIgnoreCaseAndDeletedAtIsNull("FREEFORALL")).thenReturn(Optional.of(v));
            when(usageRepository.countByVoucherAndPhone("1", "0901234567")).thenReturn(0L);

            VoucherValidationResponse res = voucherService.validate(
                    new VoucherValidationRequest("FREEFORALL", "0901234567", new BigDecimal("25000"))
            );
            assertTrue(res.valid());
            assertEquals(new BigDecimal("10000"), res.discountAmount());
        }
    }

    @Nested
    @DisplayName("Ghi nhận sử dụng voucher (redeemForInvoice)")
    class RedeemTests {

        @Test
        @DisplayName("Lỗi 400 khi thiếu hóa đơn invoiceId hoặc mã voucher")
        void testRedeemForInvoice_MissingArgs() {
            assertThrows(ResponseStatusException.class, () -> voucherService.redeemForInvoice(
                    new VoucherValidationRequest("", "0901234567", new BigDecimal("100000")), "INV-1"
            ));

            assertThrows(ResponseStatusException.class, () -> voucherService.redeemForInvoice(
                    new VoucherValidationRequest("CODE", "0901234567", new BigDecimal("100000")), ""
            ));
        }

        @Test
        @DisplayName("redeemForInvoice thành công: Lưu VoucherUsage và tăng usedQuantity")
        void testRedeemForInvoice_Success() {
            Voucher v = createSampleVoucher("v-1", "VOUCHER10", Voucher.DiscountType.PERCENTAGE,
                    new BigDecimal("10"), null, BigDecimal.ZERO, 1, 5, 50, 5, 2, true);
            when(repository.lockByCode("VOUCHER10")).thenReturn(Optional.of(v));
            when(usageRepository.countByVoucherAndPhone("v-1", "0901234567")).thenReturn(0L);

            VoucherValidationResponse res = voucherService.redeemForInvoice(
                    new VoucherValidationRequest("VOUCHER10", "0901234567", new BigDecimal("100000")),
                    "INV-1001"
            );

            assertTrue(res.valid());
            assertEquals(new BigDecimal("10000.00"), res.discountAmount());
            assertEquals(6, v.getUsedQuantity()); // 5 tăng lên 6

            ArgumentCaptor<VoucherUsage> captor = ArgumentCaptor.forClass(VoucherUsage.class);
            verify(usageRepository).save(captor.capture());
            VoucherUsage usage = captor.getValue();
            assertEquals("v-1", usage.getVoucherId());
            assertEquals("0901234567", usage.getCustomerPhone());
            assertEquals("INV-1001", usage.getInvoiceId());
            assertEquals(new BigDecimal("10000.00"), usage.getDiscountApplied());
        }

        @Test
        @DisplayName("redeem phương thức cũ luôn ném BAD_REQUEST hướng dẫn dùng checkout")
        void testRedeem_ThrowsException() {
            ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                    voucherService.redeem(new VoucherValidationRequest("CODE", "0901234567", BigDecimal.TEN))
            );
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        }
    }

    @Nested
    @DisplayName("Cập nhật trạng thái và xóa voucher")
    class ManageVoucherTests {

        @Test
        @DisplayName("setActive bật/tắt voucher thành công")
        void testSetActive() {
            Voucher v = createSampleVoucher("1", "V1", Voucher.DiscountType.PERCENTAGE,
                    new BigDecimal("10"), null, BigDecimal.ZERO, 1, 5, 10, 0, 1, true);
            when(repository.findByIdAndDeletedAtIsNull("1")).thenReturn(Optional.of(v));

            VoucherResponse res = voucherService.setActive("1", false);
            assertFalse(res.active());
            assertFalse(v.getActive());
        }

        @Test
        @DisplayName("delete thực hiện soft delete (active=false, deletedAt != null)")
        void testDelete() {
            Voucher v = createSampleVoucher("1", "V1", Voucher.DiscountType.PERCENTAGE,
                    new BigDecimal("10"), null, BigDecimal.ZERO, 1, 5, 10, 0, 1, true);
            when(repository.findByIdAndDeletedAtIsNull("1")).thenReturn(Optional.of(v));

            voucherService.delete("1");
            assertFalse(v.getActive());
            assertNotNull(v.getDeletedAt());
        }

        @Test
        @DisplayName("getUsages ném 404 nếu không tìm thấy voucher")
        void testGetUsages_NotFound() {
            when(repository.findByIdAndDeletedAtIsNull("unknown")).thenReturn(Optional.empty());
            assertThrows(ResponseStatusException.class, () -> voucherService.getUsages("unknown"));
        }

        @Test
        @DisplayName("getUsages trả về lịch sử dùng voucher")
        void testGetUsages_Success() {
            Voucher v = createSampleVoucher("1", "V1", Voucher.DiscountType.PERCENTAGE,
                    new BigDecimal("10"), null, BigDecimal.ZERO, 1, 5, 10, 0, 1, true);
            when(repository.findByIdAndDeletedAtIsNull("1")).thenReturn(Optional.of(v));

            VoucherUsageResponse usage = new VoucherUsageResponse(
                    "u-1", "1", "0901234567", "inv-1", "HV-2026-INV-1", new BigDecimal("10000"), LocalDateTime.now()
            );
            when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("1")))
                    .thenReturn(List.of(usage));

            List<VoucherUsageResponse> list = voucherService.getUsages("1");
            assertEquals(1, list.size());
            assertEquals("HV-2026-INV-1", list.get(0).invoiceCode());
        }
    }
}
