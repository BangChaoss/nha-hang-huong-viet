package com.huongViet.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "loyalty_settings")
public class LoyaltySettings {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(name = "earn_rate_percent", nullable = false, precision = 7, scale = 3)
    private BigDecimal earnRatePercent;

    @Column(name = "point_value_vnd", nullable = false, precision = 12, scale = 2)
    private BigDecimal pointValueVnd = BigDecimal.ONE;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected LoyaltySettings() {}

    public LoyaltySettings(String id, BigDecimal rate) {
        this(id, rate, BigDecimal.ONE);
    }

    public LoyaltySettings(String id, BigDecimal rate, BigDecimal pointValueVnd) {
        this.id = id;
        this.earnRatePercent = rate;
        this.pointValueVnd = (pointValueVnd != null && pointValueVnd.compareTo(BigDecimal.ZERO) > 0)
                ? pointValueVnd : BigDecimal.ONE;
        this.updatedAt = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public BigDecimal getEarnRatePercent() {
        return earnRatePercent;
    }

    public BigDecimal getPointValueVnd() {
        return pointValueVnd != null ? pointValueVnd : BigDecimal.ONE;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void update(BigDecimal rate, BigDecimal pointValue) {
        this.earnRatePercent = rate;
        if (pointValue != null && pointValue.compareTo(BigDecimal.ZERO) > 0) {
            this.pointValueVnd = pointValue;
        }
        this.updatedAt = LocalDateTime.now();
    }
}
