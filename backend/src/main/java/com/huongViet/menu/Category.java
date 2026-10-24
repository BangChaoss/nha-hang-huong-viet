package com.huongViet.menu;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Menu category (UC-21 Manage Menu).
 * Soft-deleted via {@code deleted_at} + generated column unique key.
 */
@Entity
@Table(name = "categories")
public class Category {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36, nullable = false, columnDefinition = "CHAR(36)")
    private String id;

    @Column(name = "category_name", nullable = false, length = 50)
    private String categoryName;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    protected Category() {}

    public Category(String id, String categoryName, Integer displayOrder) {
        this.id = id;
        this.categoryName = categoryName;
        this.displayOrder = displayOrder != null ? displayOrder : 0;
    }

    public String getId() {
        return id;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder != null ? displayOrder : 0;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }
}
