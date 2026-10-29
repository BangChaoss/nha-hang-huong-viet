package com.huongViet.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Nhật ký mọi thay đổi trên một dòng món trong đơn (UC-17, STT 86).
 *
 * <p>Ghi lại số lượng và trạng thái trước/sau để truy vết được vì sao
 * bếp nhận được phiếu khác với lúc nhân viên bấm "Gửi Bếp".</p>
 */
@Entity
@Table(name = "order_item_audits")
public class OrderItemAudit {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36, nullable = false, columnDefinition = "CHAR(36)")
    private String id;

    @Column(name = "order_item_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String orderItemId;

    @Column(name = "changed_by", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String changedBy;

    @Column(nullable = false, length = 30)
    private String action;

    @Column(name = "old_quantity", nullable = false)
    private Integer oldQuantity;

    @Column(name = "new_quantity", nullable = false)
    private Integer newQuantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_status", nullable = false, length = 20)
    private CookingStatus oldStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 20)
    private CookingStatus newStatus;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    protected OrderItemAudit() {}

    public OrderItemAudit(String id,
                           String orderItemId,
                           String changedBy,
                           String action,
                           Integer oldQuantity,
                           Integer newQuantity,
                           CookingStatus oldStatus,
                           CookingStatus newStatus,
                           String reason) {
        this.id = id;
        this.orderItemId = orderItemId;
        this.changedBy = changedBy;
        this.action = action;
        this.oldQuantity = oldQuantity;
        this.newQuantity = newQuantity;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.reason = reason;
        this.changedAt = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public String getOrderItemId() {
        return orderItemId;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public String getAction() {
        return action;
    }

    public Integer getOldQuantity() {
        return oldQuantity;
    }

    public Integer getNewQuantity() {
        return newQuantity;
    }

    public CookingStatus getOldStatus() {
        return oldStatus;
    }

    public CookingStatus getNewStatus() {
        return newStatus;
    }

    public String getReason() {
        return reason;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }
}
