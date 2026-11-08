package com.huongViet.feedback;

import com.huongViet.auth.User;
import com.huongViet.auth.UserRepository;
import com.huongViet.auth.UserRole;
import com.huongViet.feedback.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceTest {

    @Mock
    private FeedbackRepository feedbackRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private FeedbackService feedbackService;
    private User customer;

    @BeforeEach
    void setUp() {
        feedbackService = new FeedbackService(feedbackRepository, userRepository, jdbcTemplate);
        customer = new User("user-1", "nguyenvana", "vana@example.com", "0912345678", "hashedPassword", UserRole.CUSTOMER);
    }

    @Test
    @DisplayName("submitFeedback: thành công khi đánh giá hóa đơn PAID hợp lệ")
    void testSubmitFeedbackSuccess() {
        CreateFeedbackRequest request = new CreateFeedbackRequest("inv-100", 5, "Món ăn rất ngon, phục vụ chu đáo!");

        when(feedbackRepository.findTopByCustomerIdAndDeletedAtIsNullOrderByCreatedAtDesc("user-1"))
            .thenReturn(Optional.empty());
        when(feedbackRepository.existsByInvoiceIdAndDeletedAtIsNull("inv-100"))
            .thenReturn(false);

        Map<String, Object> invoiceRow = Map.of(
            "id", "inv-100",
            "customer_id", "user-1",
            "status", "PAID"
        );
        when(jdbcTemplate.queryForList(anyString(), eq("inv-100")))
            .thenReturn(List.of(invoiceRow));

        Feedback savedFeedback = new Feedback("user-1", "nguyenvana", "0912345678", "inv-100", 5, "Món ăn rất ngon, phục vụ chu đáo!");
        savedFeedback.setId("fb-1");
        when(feedbackRepository.save(any(Feedback.class))).thenReturn(savedFeedback);

        FeedbackResponse response = feedbackService.submitFeedback(customer, request);

        assertNotNull(response);
        assertEquals(5, response.rating());
        assertEquals("inv-100", response.invoiceId());
        assertEquals("Món ăn rất ngon, phục vụ chu đáo!", response.comment());
        assertTrue(response.isPublic());
    }

    @Test
    @DisplayName("Anti-spam: ném 429 TOO_MANY_REQUESTS khi gửi 2 review trong vòng dưới 60 giây")
    void testAntiSpamCooldown() {
        Feedback recentFeedback = new Feedback("user-1", "nguyenvana", "0912345678", "inv-99", 4, "Ngon");
        recentFeedback.setCreatedAt(LocalDateTime.now().minusSeconds(20)); // Vừa gửi 20s trước

        when(feedbackRepository.findTopByCustomerIdAndDeletedAtIsNullOrderByCreatedAtDesc("user-1"))
            .thenReturn(Optional.of(recentFeedback));

        CreateFeedbackRequest request = new CreateFeedbackRequest("inv-100", 5, "Ngon tiếp");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            feedbackService.submitFeedback(customer, request)
        );

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatusCode());
        assertTrue(ex.getReason().contains("thao tác quá nhanh"));
    }

    @Test
    @DisplayName("Anti-spam BR-45 & E2: ném 409 CONFLICT khi hóa đơn đã từng được gửi đánh giá")
    void testAntiSpamDuplicateInvoice() {
        CreateFeedbackRequest request = new CreateFeedbackRequest("inv-100", 5, "Đánh giá lần 2");

        when(feedbackRepository.findTopByCustomerIdAndDeletedAtIsNullOrderByCreatedAtDesc("user-1"))
            .thenReturn(Optional.empty());
        when(feedbackRepository.existsByInvoiceIdAndDeletedAtIsNull("inv-100"))
            .thenReturn(true); // Đã tồn tại review cho invoice này

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            feedbackService.submitFeedback(customer, request)
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("đã được gửi đánh giá trước đó"));
    }

    @Test
    @DisplayName("E1: ném 400 BAD_REQUEST khi hóa đơn chưa hoàn tất thanh toán (chưa PAID)")
    void testInvoiceNotPaid() {
        CreateFeedbackRequest request = new CreateFeedbackRequest("inv-100", 5, "Nhận xét");

        when(feedbackRepository.findTopByCustomerIdAndDeletedAtIsNullOrderByCreatedAtDesc("user-1"))
            .thenReturn(Optional.empty());
        when(feedbackRepository.existsByInvoiceIdAndDeletedAtIsNull("inv-100"))
            .thenReturn(false);

        Map<String, Object> unpaidRow = Map.of(
            "id", "inv-100",
            "customer_id", "user-1",
            "status", "UNPAID"
        );
        when(jdbcTemplate.queryForList(anyString(), eq("inv-100")))
            .thenReturn(List.of(unpaidRow));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            feedbackService.submitFeedback(customer, request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("PAID"));
    }

    @Test
    @DisplayName("Security: ném 403 FORBIDDEN khi cố tình đánh giá hóa đơn của khách hàng khác")
    void testOtherCustomerInvoiceForbidden() {
        CreateFeedbackRequest request = new CreateFeedbackRequest("inv-100", 5, "Nhận xét");

        when(feedbackRepository.findTopByCustomerIdAndDeletedAtIsNullOrderByCreatedAtDesc("user-1"))
            .thenReturn(Optional.empty());
        when(feedbackRepository.existsByInvoiceIdAndDeletedAtIsNull("inv-100"))
            .thenReturn(false);

        Map<String, Object> otherCustomerRow = Map.of(
            "id", "inv-100",
            "customer_id", "user-other",
            "status", "PAID"
        );
        when(jdbcTemplate.queryForList(anyString(), eq("inv-100")))
            .thenReturn(List.of(otherCustomerRow));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            feedbackService.submitFeedback(customer, request)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    @DisplayName("UC25/UC26: Quản lý phản hồi đánh giá thành công")
    void testManagerReplyFeedback() {
        Feedback fb = new Feedback("user-1", "nguyenvana", "0912345678", "inv-100", 5, "Món ngon!");
        fb.setId("fb-1");

        when(feedbackRepository.findById("fb-1")).thenReturn(Optional.of(fb));
        when(feedbackRepository.save(any(Feedback.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReplyFeedbackRequest replyReq = new ReplyFeedbackRequest("Cảm ơn bạn đã ghé thăm nhà hàng!");
        FeedbackResponse res = feedbackService.replyFeedback("fb-1", replyReq);

        assertNotNull(res);
        assertEquals("Cảm ơn bạn đã ghé thăm nhà hàng!", res.staffResponse());
    }

    @Test
    @DisplayName("UC25/UC26 & BR-47: Quản lý ẩn đánh giá vi phạm tiêu chuẩn")
    void testManagerUpdateVisibility() {
        Feedback fb = new Feedback("user-1", "nguyenvana", "0912345678", "inv-100", 1, "Spam");
        fb.setId("fb-1");
        fb.setIsPublic(true);

        when(feedbackRepository.findById("fb-1")).thenReturn(Optional.of(fb));
        when(feedbackRepository.save(any(Feedback.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateFeedbackVisibilityRequest visReq = new UpdateFeedbackVisibilityRequest(false);
        FeedbackResponse res = feedbackService.updateVisibility("fb-1", visReq);

        assertNotNull(res);
        assertFalse(res.isPublic());
    }

    @Test
    @DisplayName("Thống kê feedback tính đúng điểm trung bình và phân bổ sao")
    void testGetFeedbackStats() {
        when(feedbackRepository.count()).thenReturn(3L);
        when(feedbackRepository.countByRatingAndDeletedAtIsNull(5)).thenReturn(2L);
        when(feedbackRepository.countByRatingAndDeletedAtIsNull(4)).thenReturn(1L);
        when(feedbackRepository.countByRatingAndDeletedAtIsNull(3)).thenReturn(0L);
        when(feedbackRepository.countByRatingAndDeletedAtIsNull(2)).thenReturn(0L);
        when(feedbackRepository.countByRatingAndDeletedAtIsNull(1)).thenReturn(0L);
        when(feedbackRepository.countUnrepliedFeedbacks()).thenReturn(1L);

        FeedbackStatsResponse stats = feedbackService.getFeedbackStats();

        assertNotNull(stats);
        assertEquals(3L, stats.totalReviews());
        // 2 * 5 + 1 * 4 = 14 / 3 = 4.666 -> 4.7
        assertEquals(4.7, stats.averageRating());
        assertEquals(2L, stats.ratingCounts().get(5));
        assertEquals(1L, stats.ratingCounts().get(4));
        assertEquals(1L, stats.unrepliedCount());
    }
}
