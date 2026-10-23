package com.huongViet.voucher;
import java.math.BigDecimal;
import java.time.LocalDateTime;
public record VoucherResponse(String id, String voucherCode, String title, String description,
 Voucher.DiscountType discountType, BigDecimal discountValue, BigDecimal maxDiscountAmount,
 BigDecimal minOrderAmount, LocalDateTime startDate, LocalDateTime endDate, Integer totalQuantity,
 Integer usedQuantity, Integer maxUsesPerCustomer, Boolean active, LocalDateTime createdAt,
 LocalDateTime updatedAt) {
 public static VoucherResponse from(Voucher v) {
  return new VoucherResponse(v.getId(),v.getVoucherCode(),v.getTitle(),v.getDescription(),v.getDiscountType(),
   v.getDiscountValue(),v.getMaxDiscountAmount(),v.getMinOrderAmount(),v.getStartDate(),v.getEndDate(),
   v.getTotalQuantity(),v.getUsedQuantity(),v.getMaxUsesPerCustomer(),v.getActive(),v.getCreatedAt(),v.getUpdatedAt());
 }
}
