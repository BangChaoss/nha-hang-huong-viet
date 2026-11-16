
package com.huongViet.voucher;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "voucher_usages")
public class VoucherUsage {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36, nullable = false, columnDefinition = "CHAR(36)")
    private String id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(
            name = "voucher_id",
            length = 36,
            nullable = false,
            columnDefinition = "CHAR(36)"
    )
    private String voucherId;

    @Column(name = "customer_phone", length = 20, nullable = false)
    private String customerPhone;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(
            name = "invoice_id",
            length = 36,
            nullable = false,
            columnDefinition = "CHAR(36)"
    )
    private String invoiceId;

    @Column(
            name = "discount_applied",
            nullable = false,
            precision = 12,
            scale = 2
    )
    private BigDecimal discountApplied;

    @Column(name = "used_at", insertable = false, updatable = false)
    private LocalDateTime usedAt;

    protected VoucherUsage() {
        // Constructor mặc định dành cho JPA.
    }

    /**
     * Constructor tạo lịch sử sử dụng voucher gắn với hóa đơn thật.
     */
    public VoucherUsage(
            String id,
            String voucherId,
            String customerPhone,
            String invoiceId,
            BigDecimal discountApplied
    ) {
        this.id = id;
        this.voucherId = voucherId;
        this.customerPhone = customerPhone;
        this.invoiceId = invoiceId;
        this.discountApplied = discountApplied;
    }

    public String getId() {
        return id;
    }

    public String getVoucherId() {
        return voucherId;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public String getInvoiceId() {
        return invoiceId;
    }

    public BigDecimal getDiscountApplied() {
        return discountApplied;
    }

    public LocalDateTime getUsedAt() {
        return usedAt;
    }
}
