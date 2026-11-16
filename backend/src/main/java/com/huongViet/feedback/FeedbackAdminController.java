package com.huongViet.feedback;

import com.huongViet.common.PageResponse;
import com.huongViet.feedback.dto.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Controller dành cho Quản lý & Admin quản lý đánh giá khách hàng (UC25/UC26).
 */
@RestController
@RequestMapping("/api/admin/feedbacks")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})
@PreAuthorize("hasAnyAuthority('MANAGER', 'ROLE_MANAGER', 'ADMIN', 'ROLE_ADMIN')")
public class FeedbackAdminController {

    private final FeedbackService feedbackService;

    public FeedbackAdminController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    /**
     * Danh sách đánh giá phân trang kèm bộ lọc đa năng.
     */
    @GetMapping
    public ResponseEntity<PageResponse<FeedbackResponse>> listFeedbacks(
        @RequestParam(value = "rating", required = false) Integer rating,
        @RequestParam(value = "isPublic", required = false) Boolean isPublic,
        @RequestParam(value = "hasResponse", required = false) Boolean hasResponse,
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "size", defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100));
        Page<FeedbackResponse> result = feedbackService.getAdminFeedbacks(rating, isPublic, hasResponse, keyword, pageable);
        return ResponseEntity.ok(PageResponse.from(result));
    }

    /**
     * Thống kê tổng số đánh giá, điểm trung bình và phân bổ số sao.
     */
    @GetMapping("/stats")
    public ResponseEntity<FeedbackStatsResponse> getStats() {
        FeedbackStatsResponse stats = feedbackService.getFeedbackStats();
        return ResponseEntity.ok(stats);
    }

    /**
     * Quản lý gửi/sửa phản hồi cho khách hàng.
     */
    @PatchMapping("/{id}/reply")
    public ResponseEntity<FeedbackResponse> replyFeedback(
        @PathVariable String id,
        @Valid @RequestBody ReplyFeedbackRequest request
    ) {
        FeedbackResponse updated = feedbackService.replyFeedback(id, request);
        return ResponseEntity.ok(updated);
    }

    /**
     * Quản lý ẩn/hiện đánh giá vi phạm (BR-47).
     */
    @PatchMapping("/{id}/visibility")
    public ResponseEntity<FeedbackResponse> updateVisibility(
        @PathVariable String id,
        @Valid @RequestBody UpdateFeedbackVisibilityRequest request
    ) {
        FeedbackResponse updated = feedbackService.updateVisibility(id, request);
        return ResponseEntity.ok(updated);
    }
}

