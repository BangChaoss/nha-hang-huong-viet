package com.huongViet.payment;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name="loyalty_settings")
public class LoyaltySettings {
    @Id @Column(length=36, nullable=false) private String id;
    @Column(name="earn_rate_percent", nullable=false, precision=7, scale=3) private BigDecimal earnRatePercent;
    @Column(name="updated_at", nullable=false) private LocalDateTime updatedAt;
    protected LoyaltySettings() {}
    public LoyaltySettings(String id, BigDecimal rate){this.id=id; this.earnRatePercent=rate; this.updatedAt=LocalDateTime.now();}
    public String getId(){return id;} public BigDecimal getEarnRatePercent(){return earnRatePercent;}
    public LocalDateTime getUpdatedAt(){return updatedAt;}
    public void updateRate(BigDecimal rate){this.earnRatePercent=rate; this.updatedAt=LocalDateTime.now();}
}
