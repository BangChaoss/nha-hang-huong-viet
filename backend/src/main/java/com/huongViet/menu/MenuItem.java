package com.huongViet.menu;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A dish on the restaurant menu (UC-20 Manage Food).
 * Never hard-deleted (BR-48): rows referenced by historical orders/invoices
 * are only soft-deleted via {@code deleted_at}.
 */
@Entity
@Table(name = "menu_items")
public class MenuItem {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36, nullable = false, columnDefinition = "CHAR(36)")
    private String id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "category_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String categoryId;

    @Column(name = "item_name", nullable = false, length = 100)
    private String itemName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "current_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal currentPrice;

    @Column(name = "image_url", length = 255)
    private String imageUrl;

    @Column(name = "is_available", nullable = false)
    private Boolean isAvailable = true;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", insertable = false, updatable = false, columnDefinition = "CHAR(36)")
    private Category category;

    protected MenuItem() {}

    public MenuItem(String id,
                    String categoryId,
                    String itemName,
                    String description,
                    BigDecimal currentPrice,
                    String imageUrl,
                    Boolean isAvailable) {
        this.id = id;
        this.categoryId = categoryId;
        this.itemName = itemName;
        this.description = description;
        this.currentPrice = currentPrice;
        this.imageUrl = imageUrl;
        this.isAvailable = isAvailable != null ? isAvailable : true;
        this.version = 0L;
    }

    public String getId() {
        return id;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(BigDecimal currentPrice) {
        this.currentPrice = currentPrice;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public Boolean getIsAvailable() {
        return isAvailable;
    }

    public void setIsAvailable(Boolean isAvailable) {
        this.isAvailable = isAvailable != null ? isAvailable : true;
    }

    public Long getVersion() {
        return version;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
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
