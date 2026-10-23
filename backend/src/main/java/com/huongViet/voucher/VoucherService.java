package com.huongViet.voucher;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class VoucherService {
    private final VoucherRepository repository;
    private final VoucherUsageRepository usageRepository;

    public VoucherService(
            VoucherRepository repository,
            VoucherUsageRepository usageRepository
    ) {
        this.repository = repository;
        this.usageRepository = usageRepository;
    }

    @Transactional(readOnly = true)
    public List<VoucherResponse> list() {
        return repository
                .findAllByDeletedAtIsNullOrderByCreatedAtDesc()
                .stream()
                .map(VoucherResponse::from)
                .toList();
    }

    @Transactional
    public synchronized VoucherResponse create(VoucherRequest r) {
        String code = r.voucherCode().trim().toUpperCase();

        if (repository.existsByVoucherCodeIgnoreCaseAndDeletedAtIsNull(code)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Voucher Code đã tồn tại."
            );
        }

        if (r.endDate().toLocalDate()
                .isBefore(r.startDate().toLocalDate())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Ngày kết thúc không được trước ngày bắt đầu."
            );
        }

        if (r.discountType() == Voucher.DiscountType.PERCENTAGE
                && r.discountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Mức giảm theo phần trăm không được vượt quá 100%."
            );
        }

        if (r.discountType() == Voucher.DiscountType.FIXED_AMOUNT
                && r.maxDiscountAmount() != null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Mức giảm tối đa chỉ áp dụng cho voucher giảm theo %."
            );
        }

        // Tạo ID voucher dạng số: "1", "2", "3", ...
        String voucherId = nextNumericVoucherId();

        Voucher v = new Voucher(
                voucherId,
                code,
                r.title().trim(),
                r.description() == null ? null : r.description().trim(),
                r.discountType(),
                r.discountValue(),
                r.maxDiscountAmount(),
                r.minOrderAmount(),
                r.startDate().toLocalDate().atStartOfDay(),
                r.endDate().toLocalDate().atTime(23, 59, 59),
                r.totalQuantity(),
                r.maxUsesPerCustomer()
        );

        return VoucherResponse.from(repository.save(v));
    }

    /**
     * Lấy ID số lớn nhất hiện có rồi tăng thêm 1.
     * Không thay đổi cấu trúc database.
     * Các ID UUID của voucher cũ vẫn được giữ nguyên.
     */
    private String nextNumericVoucherId() {
        long maxId = 0L;

        for (Voucher existing : repository.findAll()) {
            String id = existing.getId();

            if (id == null || !id.matches("\\d+")) {
                continue;
            }

            try {
                maxId = Math.max(maxId, Long.parseLong(id));
            } catch (NumberFormatException ignored) {
                // Bỏ qua ID số vượt quá giới hạn long.
            }
        }

        if (maxId == Long.MAX_VALUE) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Đã hết dãy ID số cho voucher."
            );
        }

        return Long.toString(maxId + 1);
    }

    @Transactional(readOnly = true)
    public VoucherValidationResponse validate(
            VoucherValidationRequest request
    ) {
        String code = request.voucherCode().trim().toUpperCase();
        String phone = normalizePhone(request.phoneNumber());

        Voucher v = repository
                .findByVoucherCodeIgnoreCaseAndDeletedAtIsNull(code)
                .orElse(null);

        return validateForVoucher(
                v,
                code,
                phone,
                request.orderAmount()
        );
    }

    @Transactional
    public VoucherValidationResponse redeem(
            VoucherValidationRequest request
    ) {
        String code = request.voucherCode().trim().toUpperCase();
        String phone = normalizePhone(request.phoneNumber());

        Voucher v = repository.lockByCode(code).orElse(null);

        VoucherValidationResponse check = validateForVoucher(
                v,
                code,
                phone,
                request.orderAmount()
        );

        if (!check.valid()) {
            return check;
        }

        // ID của lịch sử sử dụng voucher vẫn giữ UUID như trước.
        usageRepository.save(
                new VoucherUsage(
                        UUID.randomUUID().toString(),
                        v.getId(),
                        phone,
                        check.discountAmount()
                )
        );

        v.setUsedQuantity(v.getUsedQuantity() + 1);

        return new VoucherValidationResponse(
                true,
                "Đã ghi nhận lượt sử dụng voucher.",
                check.discountAmount(),
                code
        );
    }

    private VoucherValidationResponse validateForVoucher(
            Voucher v,
            String code,
            String phone,
            BigDecimal orderAmount
    ) {
        if (v == null || !Boolean.TRUE.equals(v.getActive())) {
            return invalid(
                    "Voucher không tồn tại hoặc đã bị tắt.",
                    code
            );
        }

        // Voucher có hiệu lực theo ngày lịch.
        java.time.LocalDate today = java.time.LocalDate.now();

        if (today.isBefore(v.getStartDate().toLocalDate())
                || today.isAfter(v.getEndDate().toLocalDate())) {
            return invalid(
                    "Voucher chưa đến thời gian áp dụng hoặc đã hết hạn.",
                    code
            );
        }

        if (v.getUsedQuantity() >= v.getTotalQuantity()) {
            return invalid("Voucher đã hết lượt phát hành.", code);
        }

        if (orderAmount.compareTo(v.getMinOrderAmount()) < 0) {
            return invalid("Đơn hàng chưa đạt giá trị tối thiểu.", code);
        }

        if (usageRepository.countByVoucherAndPhone(v.getId(), phone)
                >= v.getMaxUsesPerCustomer()) {
            return invalid(
                    "Số điện thoại này đã sử dụng hết lượt voucher cho phép.",
                    code
            );
        }

        BigDecimal discount =
                v.getDiscountType() == Voucher.DiscountType.PERCENTAGE
                        ? orderAmount
                        .multiply(v.getDiscountValue())
                        .divide(
                                BigDecimal.valueOf(100),
                                2,
                                RoundingMode.HALF_UP
                        )
                        : v.getDiscountValue();

        if (v.getMaxDiscountAmount() != null
                && discount.compareTo(v.getMaxDiscountAmount()) > 0) {
            discount = v.getMaxDiscountAmount();
        }

        if (discount.compareTo(orderAmount) > 0) {
            discount = orderAmount;
        }

        return new VoucherValidationResponse(
                true,
                "Voucher hợp lệ.",
                discount,
                code
        );
    }

    private VoucherValidationResponse invalid(
            String message,
            String code
    ) {
        return new VoucherValidationResponse(
                false,
                message,
                BigDecimal.ZERO,
                code
        );
    }

    private String normalizePhone(String phone) {
        return phone.replaceAll("[\\s-]", "");
    }

    @Transactional
    public VoucherResponse setActive(String id, boolean active) {
        Voucher v = repository
                .findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy voucher."
                ));

        v.setActive(active);

        return VoucherResponse.from(v);
    }

    @Transactional
    public void delete(String id) {
        Voucher v = repository
                .findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy voucher."
                ));

        v.setActive(false);
        v.setDeletedAt(LocalDateTime.now());
    }
}
