package com.huongViet.order;

import com.huongViet.menu.MenuItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A single dish line inside an {@link Order}.
 * The unit price is captured at ordering time so later menu price changes
 * never rewrite order history.
 */
@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36, nullable = false, columnDefinition = "CHAR(36)")
    private String id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "order_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String orderId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "menu_item_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String menuItemId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price_snapshot", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPriceSnapshot = BigDecimal.ZERO;

    @Column(length = 255)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(name = "cooking_status", nullable = false, length = 20)
    private CookingStatus cookingStatus = CookingStatus.QUEUED;

    @Column(name = "cancel_reason", columnDefinition = "TEXT")
    private String cancelReason;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_item_id", insertable = false, updatable = false, columnDefinition = "CHAR(36)")
    private MenuItem menuItem;

    protected OrderItem() {}

    public OrderItem(String id,
                     String orderId,
                     String menuItemId,
                     Integer quantity,
                     BigDecimal unitPriceSnapshot,
                     String note) {
        this.id = id;
        this.orderId = orderId;
        this.menuItemId = menuItemId;
        this.quantity = quantity;
        this.unitPriceSnapshot = unitPriceSnapshot != null ? unitPriceSnapshot : BigDecimal.ZERO;
        this.note = note;
        this.cookingStatus = CookingStatus.QUEUED;
    }

    public String getId() {
        return id;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getMenuItemId() {
        return menuItemId;
    }

    public void setMenuItemId(String menuItemId) {
        this.menuItemId = menuItemId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitPriceSnapshot() {
        return unitPriceSnapshot;
    }

    public void setUnitPriceSnapshot(BigDecimal unitPriceSnapshot) {
        this.unitPriceSnapshot = unitPriceSnapshot;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public CookingStatus getCookingStatus() {
        return cookingStatus;
    }

    public void setCookingStatus(CookingStatus cookingStatus) {
        this.cookingStatus = cookingStatus;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public MenuItem getMenuItem() {
        return menuItem;
    }

    public void setMenuItem(MenuItem menuItem) {
        this.menuItem = menuItem;
    }

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
    }
}
