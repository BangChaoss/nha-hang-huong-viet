package com.huongViet.payment;
import java.math.BigDecimal;
import java.time.LocalDateTime;
public record LoyaltyRateResponse(BigDecimal earnRatePercent, LocalDateTime updatedAt) {
    public static LoyaltyRateResponse from(LoyaltySettings s){return new LoyaltyRateResponse(s.getEarnRatePercent(), s.getUpdatedAt());}
}
