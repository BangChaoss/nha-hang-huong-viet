package com.huongViet.feedback;

import com.huongViet.auth.User;
import com.huongViet.common.PageResponse;
import com.huongViet.feedback.dto.CreateFeedbackRequest;
import com.huongViet.feedback.dto.EligibleInvoiceResponse;
import com.huongViet.feedback.dto.FeedbackResponse;
import com.huongViet.feedback.dto.ReservationInvoiceInfoResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/feedbacks")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})
public class FeedbackCustomerController {

    private final FeedbackService feedbackService;

    public FeedbackCustomerController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    /**
     * Khách hàng gửi đánh giá & rating (UC19/UC20).
     */
    @PostMapping
    public ResponseEntity<FeedbackResponse> submitFeedback(
        @AuthenticationPrincipal User currentUser,
        @Valid @RequestBody CreateFeedbackRequest request
    ) {
        FeedbackResponse response = feedbackService.submitFeedback(currentUser, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Khách hàng xem lịch sử đánh giá của chính mình.
     */
    @GetMapping("/my-reviews")
    public ResponseEntity<List<FeedbackResponse>> getMyReviews(@AuthenticationPrincipal User currentUser) {
        List<FeedbackResponse> reviews = feedbackService.getMyFeedbacks(currentUser);
        return ResponseEntity.ok(reviews);
    }

    /**
     * Kiểm tra và xem đánh giá của một hóa đơn cụ thể.
     */
    @GetMapping("/invoice/{invoiceId}")
    public ResponseEntity<FeedbackResponse> getFeedbackByInvoice(@PathVariable String invoiceId) {
        return feedbackService.getFeedbackByInvoice(invoiceId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * Lấy thông tin hóa đơn gắn với đơn đặt bàn để phục vụ gửi đánh giá (UC19/UC20).
     */
    @GetMapping("/reservation/{reservationId}/invoice")
    public ResponseEntity<ReservationInvoiceInfoResponse> getInvoiceForReservation(
        @AuthenticationPrincipal User currentUser,
        @PathVariable String reservationId
    ) {
        return ResponseEntity.ok(feedbackService.getInvoiceForReservation(reservationId, currentUser));
    }

    /**
     * Lấy danh sách các hóa đơn đã thanh toán của khách hàng mà chưa được gửi đánh giá.
     */
    @GetMapping("/eligible-invoices")
    public ResponseEntity<List<EligibleInvoiceResponse>> getEligibleInvoices(
        @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(feedbackService.getEligibleInvoices(currentUser));
    }

    /**
     * Lấy danh sách đánh giá công khai hiển thị trên trang chủ / trang giới thiệu (UC-07).
     */
    @GetMapping("/public")
    public ResponseEntity<PageResponse<FeedbackResponse>> getPublicFeedbacks(
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "size", defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 50));
        Page<FeedbackResponse> result = feedbackService.getPublicFeedbacks(pageable);
        return ResponseEntity.ok(PageResponse.from(result));
    }
}

