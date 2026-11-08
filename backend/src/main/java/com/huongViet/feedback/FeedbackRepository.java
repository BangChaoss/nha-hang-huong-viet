package com.huongViet.feedback;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, String> {

    /**
     * Kiểm tra xem invoiceId đã có feedback nào chưa (dùng cho logic chống spam BR-45).
     */
    boolean existsByInvoiceIdAndDeletedAtIsNull(String invoiceId);

    /**
     * Lấy feedback theo invoiceId.
     */
    Optional<Feedback> findByInvoiceIdAndDeletedAtIsNull(String invoiceId);

    /**
     * Lấy feedback gần nhất của một customer (dùng cho logic kiểm tra cooldown rate limiting).
     */
    Optional<Feedback> findTopByCustomerIdAndDeletedAtIsNullOrderByCreatedAtDesc(String customerId);

    /**
     * Lấy danh sách feedback của một customer.
     */
    List<Feedback> findByCustomerIdAndDeletedAtIsNullOrderByCreatedAtDesc(String customerId);

    /**
     * Lấy danh sách feedback công khai cho khách hàng xem trên web (UC-07, BR-47).
     */
    Page<Feedback> findByIsPublicTrueAndDeletedAtIsNullOrderByCreatedAtDesc(Pageable pageable);

    /**
     * Tìm kiếm và lọc feedback cho Quản lý (UC25/UC26).
     */
    @Query("""
        SELECT f FROM Feedback f
        WHERE f.deletedAt IS NULL
          AND (:rating IS NULL OR f.rating = :rating)
          AND (:isPublic IS NULL OR f.isPublic = :isPublic)
          AND (:hasResponse IS NULL OR (:hasResponse = true AND f.staffResponse IS NOT NULL AND f.staffResponse != '') OR (:hasResponse = false AND (f.staffResponse IS NULL OR f.staffResponse = '')))
          AND (:keyword IS NULL OR LOWER(f.guestName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR f.guestPhone LIKE CONCAT('%', :keyword, '%') OR LOWER(f.comment) LIKE LOWER(CONCAT('%', :keyword, '%')))
        ORDER BY f.createdAt DESC
        """)
    Page<Feedback> findAdminFeedbacks(
        @Param("rating") Integer rating,
        @Param("isPublic") Boolean isPublic,
        @Param("hasResponse") Boolean hasResponse,
        @Param("keyword") String keyword,
        Pageable pageable
    );

    /**
     * Đếm số lượng feedback theo số sao.
     */
    long countByRatingAndDeletedAtIsNull(Integer rating);

    /**
     * Đếm số lượng feedback chưa được phản hồi.
     */
    @Query("SELECT COUNT(f) FROM Feedback f WHERE f.deletedAt IS NULL AND (f.staffResponse IS NULL OR f.staffResponse = '')")
    long countUnrepliedFeedbacks();
}

