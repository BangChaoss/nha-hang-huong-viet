package com.huongViet.feedback;

import com.huongViet.auth.User;
import com.huongViet.auth.UserRepository;
import com.huongViet.feedback.dto.*;
import com.huongViet.reservation.Reservation;
import com.huongViet.reservation.ReservationRepository;
import com.huongViet.reservation.ReservationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Transactional
public class FeedbackService {

    private static final Logger logger = LoggerFactory.getLogger(FeedbackService.class);
    private static final long COOLDOWN_SECONDS = 60; // Cooldown 60s giữa 2 lần gửi review

    private final FeedbackRepository feedbackRepository;
    private final UserRepository userRepository;
    private final ReservationRepository reservationRepository;
    private final JdbcTemplate jdbcTemplate;

    public FeedbackService(
        FeedbackRepository feedbackRepository,
        UserRepository userRepository,
        ReservationRepository reservationRepository,
        JdbcTemplate jdbcTemplate
    ) {
        this.feedbackRepository = feedbackRepository;
        this.userRepository = userRepository;
        this.reservationRepository = reservationRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Khách hàng gửi đánh giá (UC19/UC20).
     */
    public FeedbackResponse submitFeedback(User currentCustomer, CreateFeedbackRequest request) {
        if (currentCustomer == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập để gửi đánh giá.");
        }

        // 1. Kiểm tra Cooldown Anti-spam: 60s giữa các lần gửi review
        feedbackRepository.findTopByCustomerIdAndDeletedAtIsNullOrderByCreatedAtDesc(currentCustomer.getId())
            .ifPresent(lastFeedback -> {
                long secondsSinceLast = Duration.between(lastFeedback.getCreatedAt(), LocalDateTime.now()).getSeconds();
                if (secondsSinceLast < COOLDOWN_SECONDS) {
                    throw new ResponseStatusException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Bạn thao tác quá nhanh. Vui lòng chờ " + (COOLDOWN_SECONDS - secondsSinceLast) + " giây trước khi gửi tiếp."
                    );
                }
            });

        String invoiceRef = request.invoiceId() != null && !request.invoiceId().trim().isEmpty()
            ? request.invoiceId().trim()
            : null;
        String invoiceId = null;

        if (invoiceRef != null) {
            // 2. Tìm hóa đơn theo id (UUID) hoặc mã in trên hóa đơn (HV-...)
            String sql = "SELECT id, customer_id, status FROM invoices WHERE id = ? OR invoice_code = ? LIMIT 1";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, invoiceRef, invoiceRef);

            // Nếu không tìm thấy trong bảng invoices, kiểm tra xem invoiceRef có phải là reservationId không
            if (rows.isEmpty()) {
                Optional<Reservation> resOpt = reservationRepository.findByIdAndDeletedAtIsNull(invoiceRef);
                if (resOpt.isPresent()) {
                    Reservation res = resOpt.get();
                    ReservationInvoiceInfoResponse invInfo = getInvoiceForReservation(res.getId(), currentCustomer);
                    if (invInfo != null && invInfo.invoiceId() != null) {
                        rows = jdbcTemplate.queryForList(sql, invInfo.invoiceId(), invInfo.invoiceId());
                    }
                }
            }

            if (rows.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hóa đơn cần đánh giá.");
            }

            Map<String, Object> invoiceRow = rows.get(0);
            invoiceId = (String) invoiceRow.get("id");

            // 3. Chặn spam trùng lặp: Mỗi hóa đơn chỉ được đánh giá duy nhất 1 lần (BR-45, E2)
            if (feedbackRepository.existsByInvoiceIdAndDeletedAtIsNull(invoiceId)) {
                throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Hóa đơn này đã được gửi đánh giá trước đó."
                );
            }

            // 4. Hóa đơn phải đã thanh toán hoàn thành (PAID) (E1)
            String status = (String) invoiceRow.get("status");
            if (!"PAID".equalsIgnoreCase(status)) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Chỉ có thể đánh giá hóa đơn/đặt bàn đã thanh toán hoàn tất (PAID)."
                );
            }

            // 5. Kiểm tra quyền sở hữu hóa đơn nếu có gắn mã tài khoản khác
            String invoiceCustomerId = (String) invoiceRow.get("customer_id");
            if (invoiceCustomerId != null && !invoiceCustomerId.equals(currentCustomer.getId())) {
                throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Bạn không có quyền đánh giá hóa đơn của khách hàng khác."
                );
            }
        }

        String comment = request.comment() != null ? request.comment().trim() : "";
        String guestName = currentCustomer.getUsername();
        String guestPhone = currentCustomer.getPhoneNumber();

        Feedback feedback = new Feedback(
            currentCustomer.getId(),
            guestName,
            guestPhone,
            invoiceId,
            request.rating(),
            comment
        );

        Feedback saved = feedbackRepository.save(feedback);
        logger.info("Feedback submitted successfully [id={}, customerId={}, rating={}]", 
            saved.getId(), currentCustomer.getId(), saved.getRating());

        // 6. Tự động cập nhật trạng thái đơn đặt bàn liên quan sang REVIEWED (Đã đánh giá)
        try {
            int updated = 0;
            if (invoiceRef != null) {
                updated = jdbcTemplate.update(
                    "UPDATE reservations SET status = 'REVIEWED', updated_at = CURRENT_TIMESTAMP(6) WHERE id = ? AND status IN ('PAID', 'ARRIVED')",
                    invoiceRef
                );
            }
            if (invoiceId != null && updated == 0) {
                String findReservationSql = """
                    SELECT r.id
                    FROM reservations r
                    LEFT JOIN orders o ON (o.table_id = r.table_id OR (r.merged_table_ids IS NOT NULL AND r.merged_table_ids LIKE CONCAT('%', o.table_id, '%')))
                    LEFT JOIN invoices i ON i.order_id = o.id
                    WHERE (i.id = ? OR r.customer_id = ? OR r.guest_phone = ?) AND r.status IN ('PAID', 'ARRIVED')
                    ORDER BY (i.id = ?) DESC, r.reservation_date DESC, r.reservation_time DESC
                    LIMIT 1
                """;
                List<String> resIds = jdbcTemplate.query(
                    findReservationSql,
                    (rs, rowNum) -> rs.getString("id"),
                    invoiceId, currentCustomer.getId(), currentCustomer.getPhoneNumber(), invoiceId
                );
                if (!resIds.isEmpty()) {
                    String resId = resIds.get(0);
                    jdbcTemplate.update("UPDATE reservations SET status = 'REVIEWED', updated_at = CURRENT_TIMESTAMP(6) WHERE id = ?", resId);
                    logger.info("Reservation {} automatically updated to REVIEWED after feedback", resId);
                }
            }
        } catch (Exception ex) {
            logger.warn("Could not automatically update reservation status to REVIEWED: {}", ex.getMessage());
        }

        return FeedbackResponse.from(saved);
    }

    /**
     * Lấy đánh giá của một hóa đơn cụ thể.
     */
    @Transactional(readOnly = true)
    public Optional<FeedbackResponse> getFeedbackByInvoice(String invoiceId) {
        if (invoiceId == null || invoiceId.trim().isEmpty()) {
            return Optional.empty();
        }
        return feedbackRepository.findByInvoiceIdAndDeletedAtIsNull(invoiceId.trim())
            .map(FeedbackResponse::from);
    }

    /**
     * Khách hàng xem lịch sử đánh giá của chính mình.
     */
    @Transactional(readOnly = true)
    public List<FeedbackResponse> getMyFeedbacks(User currentCustomer) {
        if (currentCustomer == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập.");
        }
        return feedbackRepository.findByCustomerIdAndDeletedAtIsNullOrderByCreatedAtDesc(currentCustomer.getId())
            .stream()
            .map(FeedbackResponse::from)
            .toList();
    }

    /**
     * Lấy danh sách đánh giá công khai cho khách hàng xem trên trang chủ.
     */
    @Transactional(readOnly = true)
    public Page<FeedbackResponse> getPublicFeedbacks(Pageable pageable) {
        return feedbackRepository.findByIsPublicTrueAndDeletedAtIsNullOrderByCreatedAtDesc(pageable)
            .map(FeedbackResponse::forPublic);
    }

    /**
     * Quản lý xem danh sách đánh giá kèm bộ lọc.
     */
    @Transactional(readOnly = true)
    public Page<FeedbackResponse> getAdminFeedbacks(
        Integer rating,
        Boolean isPublic,
        Boolean hasResponse,
        String keyword,
        Pageable pageable
    ) {
        String trimmedKw = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;
        return feedbackRepository.findAdminFeedbacks(rating, isPublic, hasResponse, trimmedKw, pageable)
            .map(FeedbackResponse::from);
    }

    /**
     * Quản lý phản hồi đánh giá của khách.
     */
    public FeedbackResponse replyFeedback(String feedbackId, ReplyFeedbackRequest request) {
        Feedback feedback = feedbackRepository.findById(feedbackId)
            .filter(f -> f.getDeletedAt() == null)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đánh giá."));

        feedback.setStaffResponse(request.staffResponse().trim());
        Feedback updated = feedbackRepository.save(feedback);
        return FeedbackResponse.from(updated);
    }

    /**
     * Quản lý ẩn/hiện đánh giá vi phạm tiêu chuẩn.
     */
    public FeedbackResponse updateVisibility(String feedbackId, UpdateFeedbackVisibilityRequest request) {
        Feedback feedback = feedbackRepository.findById(feedbackId)
            .filter(f -> f.getDeletedAt() == null)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đánh giá."));

        feedback.setIsPublic(request.isPublic());
        Feedback updated = feedbackRepository.save(feedback);
        return FeedbackResponse.from(updated);
    }

    /**
     * Thống kê đánh giá dành cho Quản lý.
     */
    @Transactional(readOnly = true)
    public FeedbackStatsResponse getFeedbackStats() {
        long total = feedbackRepository.countByDeletedAtIsNull();
        if (total == 0L) {
            return new FeedbackStatsResponse(0L, 0.0, Map.of(1, 0L, 2, 0L, 3, 0L, 4, 0L, 5, 0L), 0L);
        }

        Map<Integer, Long> counts = new HashMap<>();
        long weightedSum = 0;
        for (int star = 1; star <= 5; star++) {
            long count = feedbackRepository.countByRatingAndDeletedAtIsNull(star);
            counts.put(star, count);
            weightedSum += star * count;
        }

        double average = total > 0 
            ? BigDecimal.valueOf((double) weightedSum / total).setScale(1, RoundingMode.HALF_UP).doubleValue()
            : 0.0;

        long unreplied = feedbackRepository.countUnrepliedFeedbacks();

        return new FeedbackStatsResponse(total, average, counts, unreplied);
    }

    /**
     * Lấy thông tin hóa đơn gắn với đơn đặt bàn của khách hàng để phục vụ đánh giá (UC19/UC20).
     */
    @Transactional
    public ReservationInvoiceInfoResponse getInvoiceForReservation(String reservationId, User currentUser) {
        if (reservationId == null || reservationId.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thiếu mã đặt bàn.");
        }

        Reservation reservation = reservationRepository.findByIdAndDeletedAtIsNull(reservationId.trim())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn đặt bàn."));

        String resStatus = reservation.getStatus() != null ? reservation.getStatus().name() : "";
        boolean isEligibleStatus = "PAID".equalsIgnoreCase(resStatus) || "ARRIVED".equalsIgnoreCase(resStatus) || "REVIEWED".equalsIgnoreCase(resStatus);

        // 1. Thử tìm hóa đơn qua bàn ăn (hỗ trợ cả bàn gộp)
        String sqlByTable = """
            SELECT i.id AS invoice_id, i.invoice_code, i.status AS invoice_status, i.final_amount, i.paid_at
            FROM orders o
            JOIN invoices i ON i.order_id = o.id
            WHERE (o.table_id = ? OR (? IS NOT NULL AND ? LIKE CONCAT('%', o.table_id, '%')))
            ORDER BY (CASE WHEN i.status = 'PAID' THEN 1 ELSE 0 END) DESC, i.created_at DESC
            LIMIT 1
        """;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            sqlByTable,
            reservation.getTableId(),
            reservation.getMergedTableIds(),
            reservation.getMergedTableIds() != null ? reservation.getMergedTableIds() : ""
        );

        // 2. Nếu chưa thấy, thử tìm hóa đơn qua số điện thoại hoặc customer_id của khách
        if (rows.isEmpty() || !"PAID".equalsIgnoreCase((String) rows.get(0).get("invoice_status"))) {
            String sqlByPhone = """
                SELECT i.id AS invoice_id, i.invoice_code, i.status AS invoice_status, i.final_amount, i.paid_at
                FROM invoices i
                WHERE (i.customer_phone = ? OR (i.customer_id IS NOT NULL AND i.customer_id = ?))
                  AND i.status = 'PAID'
                ORDER BY i.created_at DESC
                LIMIT 1
            """;
            List<Map<String, Object>> phoneRows = jdbcTemplate.queryForList(
                sqlByPhone,
                reservation.getGuestPhone(),
                reservation.getCustomerId()
            );
            if (!phoneRows.isEmpty()) {
                rows = phoneRows;
            }
        }

        String invoiceId = null;
        String invoiceCode = null;
        String invoiceStatus = null;
        BigDecimal finalAmount = BigDecimal.ZERO;

        if (!rows.isEmpty()) {
            Map<String, Object> row = rows.get(0);
            invoiceId = (String) row.get("invoice_id");
            invoiceCode = (String) row.get("invoice_code");
            invoiceStatus = (String) row.get("invoice_status");
            finalAmount = (BigDecimal) row.get("final_amount");
        }

        // 3. Nếu chưa có hóa đơn trong DB nhưng đơn đặt bàn đã ở trạng thái PAID / ARRIVED / REVIEWED:
        // Tự động tạo 1 bản ghi hóa đơn PAID hợp lệ gắn với mã đặt bàn để khách hàng có thể đánh giá ngay
        if ((invoiceId == null || !"PAID".equalsIgnoreCase(invoiceStatus)) && isEligibleStatus) {
            String genInvoiceCode = "HV-" + reservation.getBookingCode();
            List<Map<String, Object>> existingGen = jdbcTemplate.queryForList(
                "SELECT id, invoice_code, status, final_amount FROM invoices WHERE invoice_code = ? LIMIT 1",
                genInvoiceCode
            );
            if (!existingGen.isEmpty()) {
                Map<String, Object> genRow = existingGen.get(0);
                invoiceId = (String) genRow.get("id");
                invoiceCode = (String) genRow.get("invoice_code");
                invoiceStatus = (String) genRow.get("status");
                finalAmount = (BigDecimal) genRow.get("final_amount");
            } else {
                invoiceId = UUID.randomUUID().toString();
                invoiceCode = genInvoiceCode;
                invoiceStatus = "PAID";
                finalAmount = reservation.getDepositAmount() != null ? reservation.getDepositAmount() : BigDecimal.ZERO;
                try {
                    jdbcTemplate.update("""
                        INSERT INTO invoices (id, invoice_code, customer_id, customer_phone, subtotal_amount,
                                             discount_amount, tax_amount, final_amount, deposit_deducted,
                                             loyalty_points_earned, status, paid_at, created_at)
                        VALUES (?, ?, ?, ?, ?, 0, 0, ?, 0, 0, 'PAID', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                    """, invoiceId, invoiceCode, reservation.getCustomerId(), reservation.getGuestPhone(),
                         finalAmount, finalAmount);
                    logger.info("Auto-generated PAID invoice {} for reservation {}", invoiceCode, reservation.getId());
                } catch (Exception ex) {
                    logger.warn("Could not auto-insert invoice for reservation {}: {}", reservation.getId(), ex.getMessage());
                }
            }
        }

        boolean isPaid = "PAID".equalsIgnoreCase(invoiceStatus) || isEligibleStatus;
        boolean hasFeedback = (invoiceId != null && feedbackRepository.existsByInvoiceIdAndDeletedAtIsNull(invoiceId))
            || reservation.getStatus() == ReservationStatus.REVIEWED;

        return new ReservationInvoiceInfoResponse(
            reservationId,
            invoiceId,
            invoiceCode,
            invoiceStatus != null ? invoiceStatus : (isPaid ? "PAID" : "UNPAID"),
            finalAmount != null ? finalAmount : BigDecimal.ZERO,
            isPaid,
            hasFeedback
        );
    }

    /**
     * Lấy danh sách các hóa đơn đã thanh toán của khách hàng mà chưa được gửi đánh giá.
     */
    @Transactional(readOnly = true)
    public List<EligibleInvoiceResponse> getEligibleInvoices(User currentUser) {
        if (currentUser == null) {
            return Collections.emptyList();
        }

        String sql = """
            SELECT i.id, i.invoice_code, i.final_amount,
                   COALESCE(i.paid_at, i.created_at) AS paid_at,
                   COALESCE(t.table_number, 'Bàn ăn') AS table_number
            FROM invoices i
            LEFT JOIN orders o ON i.order_id = o.id
            LEFT JOIN restaurant_tables t ON o.table_id = t.id
            WHERE i.customer_id = ? AND i.status = 'PAID'
              AND NOT EXISTS (SELECT 1 FROM feedbacks f WHERE f.invoice_id = i.id AND f.deleted_at IS NULL)
            ORDER BY COALESCE(i.paid_at, i.created_at) DESC
        """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            java.sql.Timestamp paidTs = rs.getTimestamp("paid_at");
            LocalDateTime paidAt = (paidTs != null) ? paidTs.toLocalDateTime() : null;
            return new EligibleInvoiceResponse(
                rs.getString("id"),
                rs.getString("invoice_code"),
                rs.getBigDecimal("final_amount"),
                rs.getString("table_number"),
                paidAt
            );
        }, currentUser.getId());
    }
}