package com.huongViet.payment;

import com.huongViet.auth.User;
import com.huongViet.voucher.Voucher;
import com.huongViet.voucher.VoucherRepository;
import com.huongViet.voucher.VoucherUsageRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class LoyaltyProfileService {
    private final LoyaltyAccountRepository accounts;
    private final VoucherRepository vouchers;
    private final VoucherUsageRepository voucherUsages;

    public LoyaltyProfileService(LoyaltyAccountRepository accounts,
                                 VoucherRepository vouchers,
                                 VoucherUsageRepository voucherUsages) {
        this.accounts = accounts;
        this.vouchers = vouchers;
        this.voucherUsages = voucherUsages;
    }

    @Transactional(readOnly = true)
    public LoyaltyProfileResponse getProfile(User user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập.");
        }
        String phone = normalizePhone(user.getPhoneNumber());
        BigDecimal balance = accounts.findByCustomerPhone(phone)
                .map(LoyaltyAccount::getPointsBalance)
                .orElse(BigDecimal.ZERO);

        LocalDate today = LocalDate.now();
        List<LoyaltyProfileResponse.AvailableVoucherResponse> available = vouchers
                .findAllByDeletedAtIsNullOrderByCreatedAtDesc()
                .stream()
                .filter(v -> Boolean.TRUE.equals(v.getActive()))
                .filter(v -> !today.isBefore(v.getStartDate().toLocalDate())
                        && !today.isAfter(v.getEndDate().toLocalDate()))
                .filter(v -> v.getTotalQuantity() == null || v.getUsedQuantity() < v.getTotalQuantity())
                .filter(v -> voucherUsages.countByVoucherAndPhone(v.getId(), phone) < v.getMaxUsesPerCustomer())
                .sorted(Comparator.comparing(Voucher::getEndDate))
                .map(v -> new LoyaltyProfileResponse.AvailableVoucherResponse(
                        v.getId(), v.getVoucherCode(), v.getTitle(), v.getDescription(),
                        v.getDiscountType().name(), v.getDiscountValue(), v.getMaxDiscountAmount(),
                        v.getMinOrderAmount(), v.getStartDate().toString(), v.getEndDate().toString()))
                .toList();

        return new LoyaltyProfileResponse(phone, balance, available);
    }

    private String normalizePhone(String phone) {
        return phone == null ? "" : phone.replaceAll("[\\s()-]", "").trim();
    }
}
