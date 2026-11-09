package com.huongViet.feedback;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity ánh xạ bảng `feedbacks` sẵn có trong cơ sở dữ liệu.
 * Phục vụ lưu trữ đánh giá của khách hàng (UC19/UC20) và quản lý phản hồi (UC25/UC26).
 */
@Entity
@Table(name = "feedbacks")
public class Feedback {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36, nullable = false, columnDefinition = "CHAR(36)")
    private String id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "customer_id", length = 36, columnDefinition = "CHAR(36)")
    private String customerId;

    @Column(name = "guest_name", length = 100)
    private String guestName;

    @Column(name = "guest_phone", length = 20)
    private String guestPhone;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "invoice_id", length = 36, columnDefinition = "CHAR(36)")
    private String invoiceId;

    @Column(nullable = false)
    private Integer rating;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "staff_response", columnDefinition = "TEXT")
    private String staffResponse;

    @Column(name = "is_public", nullable = false)
    private Boolean isPublic = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public Feedback() {}

    public Feedback(String customerId, String guestName, String guestPhone, String invoiceId, Integer rating, String comment) {
        this.id = UUID.randomUUID().toString();
        this.customerId = customerId;
        this.guestName = guestName;
        this.guestPhone = guestPhone;
        this.invoiceId = invoiceId;
        this.rating = rating;
        this.comment = comment;
        this.isPublic = true;
        this.createdAt = LocalDateTime.now();
    }

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.isPublic == null) {
            this.isPublic = true;
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getGuestName() {
        return guestName;
    }

    public void setGuestName(String guestName) {
        this.guestName = guestName;
    }

    public String getGuestPhone() {
        return guestPhone;
    }

    public void setGuestPhone(String guestPhone) {
        this.guestPhone = guestPhone;
    }

    public String getInvoiceId() {
        return invoiceId;
    }

    public void setInvoiceId(String invoiceId) {
        this.invoiceId = invoiceId;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getStaffResponse() {
        return staffResponse;
    }

    public void setStaffResponse(String staffResponse) {
        this.staffResponse = staffResponse;
    }

    public Boolean getIsPublic() {
        return isPublic;
    }

    public void setIsPublic(Boolean isPublic) {
        this.isPublic = isPublic;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }
}

