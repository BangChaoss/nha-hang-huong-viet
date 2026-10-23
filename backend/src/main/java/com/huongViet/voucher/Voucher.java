package com.huongViet.voucher;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "vouchers")
public class Voucher {
    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36, nullable = false, columnDefinition = "CHAR(36)")
    private String id;
    @Column(name = "voucher_code", nullable = false, length = 50)
    private String voucherCode;
    @Column(nullable = false, length = 150)
    private String title;
    @Column(columnDefinition = "TEXT")
    private String description;
    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false)
    private DiscountType discountType;
    @Column(name = "discount_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountValue;
    @Column(name = "max_discount_amount", precision = 12, scale = 2)
    private BigDecimal maxDiscountAmount;
    @Column(name = "min_order_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal minOrderAmount = BigDecimal.ZERO;
    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;
    @Column(name = "end_date", nullable = false)
    private LocalDateTime endDate;
    @Column(name = "total_quantity", nullable = false)
    private Integer totalQuantity;
    @Column(name = "used_quantity", nullable = false)
    private Integer usedQuantity = 0;
    @Column(name = "max_uses_per_customer", nullable = false)
    private Integer maxUsesPerCustomer = 1;
    @Column(name = "is_active", nullable = false)
    private Boolean active = true;
    @Column(name = "version", nullable = false)
    private Long version = 0L;
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public enum DiscountType { PERCENTAGE, FIXED_AMOUNT }

    protected Voucher() {}
    public Voucher(String id, String voucherCode, String title, String description, DiscountType discountType,
                   BigDecimal discountValue, BigDecimal maxDiscountAmount, BigDecimal minOrderAmount,
                   LocalDateTime startDate, LocalDateTime endDate, Integer totalQuantity,
                   Integer maxUsesPerCustomer) {
        this.id=id; this.voucherCode=voucherCode; this.title=title; this.description=description;
        this.discountType=discountType; this.discountValue=discountValue; this.maxDiscountAmount=maxDiscountAmount;
        this.minOrderAmount=minOrderAmount; this.startDate=startDate; this.endDate=endDate;
        this.totalQuantity=totalQuantity; this.maxUsesPerCustomer=maxUsesPerCustomer;
        this.usedQuantity=0; this.active=true; this.version=0L;
    }
    public String getId(){return id;} public String getVoucherCode(){return voucherCode;}
    public String getTitle(){return title;} public String getDescription(){return description;}
    public DiscountType getDiscountType(){return discountType;} public BigDecimal getDiscountValue(){return discountValue;}
    public BigDecimal getMaxDiscountAmount(){return maxDiscountAmount;} public BigDecimal getMinOrderAmount(){return minOrderAmount;}
    public LocalDateTime getStartDate(){return startDate;} public LocalDateTime getEndDate(){return endDate;}
    public Integer getTotalQuantity(){return totalQuantity;} public Integer getUsedQuantity(){return usedQuantity;}
    public Integer getMaxUsesPerCustomer(){return maxUsesPerCustomer;} public Boolean getActive(){return active;}
    public LocalDateTime getCreatedAt(){return createdAt;} public LocalDateTime getUpdatedAt(){return updatedAt;}
    public LocalDateTime getDeletedAt(){return deletedAt;}
    public void setActive(Boolean active){this.active=active;} public void setDeletedAt(LocalDateTime deletedAt){this.deletedAt=deletedAt;} public void setUsedQuantity(Integer usedQuantity){this.usedQuantity=usedQuantity;}
}
