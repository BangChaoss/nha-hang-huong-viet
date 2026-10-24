package com.huongViet.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * A kitchen production ticket — one row per dish line pushed to the KDS.
 * The dish name and table number are snapshotted so the kitchen screen stays
 * readable even after the menu changes.
 */
@Entity
@Table(name = "kitchen_tickets")
public class KitchenTicket {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36, nullable = false, columnDefinition = "CHAR(36)")
    private String id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "order_item_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String orderItemId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "order_id", length = 36, columnDefinition = "CHAR(36)")
    private String orderId;

    @Column(name = "table_number", nullable = false, length = 20)
    private String tableNumber;

    @Column(name = "item_name", nullable = false, length = 100)
    private String itemName;

    @Column(nullable = false)
    private Integer quantity;

    @Column(length = 255)
    private String note;

    @Column(nullable = false)
    private Integer priority = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CookingStatus status = CookingStatus.QUEUED;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    protected KitchenTicket() {}

    public KitchenTicket(String id,
                         String orderItemId,
                         String orderId,
                         String tableNumber,
                         String itemName,
                         Integer quantity,
                         String note) {
        this.id = id;
        this.orderItemId = orderItemId;
        this.orderId = orderId;
        this.tableNumber = tableNumber;
        this.itemName = itemName;
        this.quantity = quantity;
        this.note = note;
        this.priority = 1;
        this.status = CookingStatus.QUEUED;
    }

    public String getId() {
        return id;
    }

    public String getOrderItemId() {
        return orderItemId;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getTableNumber() {
        return tableNumber;
    }

    public String getItemName() {
        return itemName;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public CookingStatus getStatus() {
        return status;
    }

    public void setStatus(CookingStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
