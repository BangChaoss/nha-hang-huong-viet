package com.huongViet.order;

import com.huongViet.table.RestaurantTable;
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
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * A dining order placed for one table (UC-15 Create Food Order).
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36, nullable = false, columnDefinition = "CHAR(36)")
    private String id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "table_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String tableId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "staff_id", length = 36, columnDefinition = "CHAR(36)")
    private String staffId;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, length = 20)
    private OrderType orderType = OrderType.DINE_IN;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.IN_PROGRESS;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "table_id", insertable = false, updatable = false, columnDefinition = "CHAR(36)")
    private RestaurantTable table;

    protected Order() {}

    public Order(String id, String tableId, String staffId, OrderType orderType) {
        this.id = id;
        this.tableId = tableId;
        this.staffId = staffId;
        this.orderType = orderType != null ? orderType : OrderType.DINE_IN;
        this.status = OrderStatus.IN_PROGRESS;
        this.version = 0L;
    }

    public String getId() {
        return id;
    }

    public String getTableId() {
        return tableId;
    }

    public void setTableId(String tableId) {
        this.tableId = tableId;
    }

    public String getStaffId() {
        return staffId;
    }

    public void setStaffId(String staffId) {
        this.staffId = staffId;
    }

    public OrderType getOrderType() {
        return orderType;
    }

    public void setOrderType(OrderType orderType) {
        this.orderType = orderType;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public Long getVersion() {
        return version;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(LocalDateTime closedAt) {
        this.closedAt = closedAt;
    }

    public RestaurantTable getTable() {
        return table;
    }

    public void setTable(RestaurantTable table) {
        this.table = table;
    }

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
