package com.huongViet.payment;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_orders", indexes = {
    @Index(name = "idx_payment_orders_txn", columnList = "vnp_transaction_no"),
    @Index(name = "idx_payment_orders_phone", columnList = "customer_phone")
})
public class PaymentOrder {
    @Id
    @Column(name = "id", length = 36, nullable = false, columnDefinition = "varchar(36)")
    private String id;
    @Column(name = "table_id", length = 36)
    private String tableId;
    @Column(name = "customer_phone", nullable = false, length = 20)
    private String customerPhone;
    @Column(name = "subtotal", nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal;
    @Column(name = "discount_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal discountAmount;
    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;
    @Column(name = "voucher_code", length = 50)
    private String voucherCode;
    @Column(name = "status", nullable = false, length = 20)
    private String status;
    @Column(name = "vnp_transaction_no", length = 100)
    private String vnpTransactionNo;
    @Column(name = "vnp_response_code", length = 10)
    private String vnpResponseCode;
    @Column(name = "points_awarded", nullable = false, precision = 14, scale = 2)
    private BigDecimal pointsAwarded = BigDecimal.ZERO;
    @Column(name = "points_used", nullable = false, precision = 14, scale = 2)
    private BigDecimal pointsUsed = BigDecimal.ZERO;
    @Column(name = "points_deducted", nullable = false)
    private boolean pointsDeducted = false;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    protected PaymentOrder() {}
    public PaymentOrder(String id, String phone, BigDecimal subtotal, BigDecimal discount,
                        BigDecimal amount, String voucherCode) {
        this(id, phone, subtotal, discount, amount, voucherCode, BigDecimal.ZERO);
    }
    public PaymentOrder(String id, String phone, BigDecimal subtotal, BigDecimal discount,
                        BigDecimal amount, String voucherCode, BigDecimal pointsUsed) {
        this(id, null, phone, subtotal, discount, amount, voucherCode, pointsUsed);
    }
    public PaymentOrder(String id, String tableId, String phone, BigDecimal subtotal, BigDecimal discount,
                        BigDecimal amount, String voucherCode, BigDecimal pointsUsed) {
        this.id=id; this.tableId=tableId; this.customerPhone=phone; this.subtotal=subtotal; this.discountAmount=discount;
        this.amount=amount; this.voucherCode=voucherCode; this.status="PENDING"; this.pointsUsed=pointsUsed == null ? BigDecimal.ZERO : pointsUsed;
        this.pointsAwarded=BigDecimal.ZERO; this.createdAt=LocalDateTime.now();
    }
    public String getId(){return id;} public String getTableId(){return tableId;} public String getCustomerPhone(){return customerPhone;}
    public BigDecimal getSubtotal(){return subtotal;} public BigDecimal getDiscountAmount(){return discountAmount;}
    public BigDecimal getAmount(){return amount;} public String getVoucherCode(){return voucherCode;}
    public String getStatus(){return status;} public String getVnpTransactionNo(){return vnpTransactionNo;}
    public String getVnpResponseCode(){return vnpResponseCode;} public BigDecimal getPointsAwarded(){return pointsAwarded;}
    public BigDecimal getPointsUsed(){return pointsUsed;} public boolean isPointsDeducted(){return pointsDeducted;}
    public void markPointsDeducted(){this.pointsDeducted=true;} public void markPointsRefunded(){this.pointsDeducted=false;}
    public LocalDateTime getCreatedAt(){return createdAt;} public LocalDateTime getPaidAt(){return paidAt;}
    public void markPaid(String transactionNo, String responseCode, BigDecimal points) {
        this.status="PAID"; this.vnpTransactionNo=transactionNo; this.vnpResponseCode=responseCode;
        this.pointsAwarded=points; this.paidAt=LocalDateTime.now();
    }
    public void markFailed(String transactionNo, String responseCode) {
        if (!"PAID".equals(this.status)) this.status="FAILED";
        this.vnpTransactionNo=transactionNo; this.vnpResponseCode=responseCode;
    }
}
