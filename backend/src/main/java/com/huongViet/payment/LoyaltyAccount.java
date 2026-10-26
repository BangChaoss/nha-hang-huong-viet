package com.huongViet.payment;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "loyalty_accounts", uniqueConstraints = @UniqueConstraint(name="uk_loyalty_phone", columnNames="customer_phone"))
public class LoyaltyAccount {
    @Id @Column(name="id", length=36, nullable=false, columnDefinition="varchar(36)") private String id;
    @Column(name="customer_phone", nullable=false, length=20) private String customerPhone;
    @Column(name="points_balance", nullable=false, precision=14, scale=2) private BigDecimal pointsBalance = BigDecimal.ZERO;
    @Column(name="updated_at", nullable=false) private LocalDateTime updatedAt = LocalDateTime.now();
    protected LoyaltyAccount() {}
    public LoyaltyAccount(String id, String phone) { this.id=id; this.customerPhone=phone; this.pointsBalance=BigDecimal.ZERO; this.updatedAt=LocalDateTime.now(); }
    public String getId(){return id;} public String getCustomerPhone(){return customerPhone;}
    public BigDecimal getPointsBalance(){return pointsBalance;}
    public void addPoints(BigDecimal points){this.pointsBalance=this.pointsBalance.add(points); this.updatedAt=LocalDateTime.now();}
    public void redeemPoints(BigDecimal points){
        if (points == null || points.signum() < 0 || this.pointsBalance.compareTo(points) < 0) {
            throw new IllegalArgumentException("Số dư điểm không đủ.");
        }
        this.pointsBalance=this.pointsBalance.subtract(points); this.updatedAt=LocalDateTime.now();
    }
}
