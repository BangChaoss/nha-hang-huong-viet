package com.huongViet.voucher;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "voucher_usages")
public class VoucherUsage {
    @Id @JdbcTypeCode(SqlTypes.CHAR) @Column(length = 36, nullable = false, columnDefinition = "CHAR(36)") private String id;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "voucher_id", length = 36, nullable = false, columnDefinition = "CHAR(36)") private String voucherId;
    @Column(name = "customer_phone", length = 20, nullable = false) private String customerPhone;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "invoice_id", length = 36, columnDefinition = "CHAR(36)") private String invoiceId;
    @Column(name = "discount_applied", nullable = false, precision = 12, scale = 2) private BigDecimal discountApplied;
    @Column(name = "used_at", insertable = false, updatable = false) private LocalDateTime usedAt;

    protected VoucherUsage() {}
    public VoucherUsage(String id, String voucherId, String customerPhone, BigDecimal discountApplied) {
        this.id = id;
        this.voucherId = voucherId;
        this.customerPhone = customerPhone;
        this.discountApplied = discountApplied;
    }
}
