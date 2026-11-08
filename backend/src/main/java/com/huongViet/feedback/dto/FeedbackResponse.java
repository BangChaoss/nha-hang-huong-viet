package com.huongViet.feedback.dto;

import com.huongViet.feedback.Feedback;

import java.time.LocalDateTime;

public record FeedbackResponse(
    String id,
    String customerId,
    String guestName,
    String guestPhone,
    String invoiceId,
    Integer rating,
    String comment,
    String staffResponse,
    Boolean isPublic,
    LocalDateTime createdAt
) {
    public static FeedbackResponse from(Feedback feedback) {
        return new FeedbackResponse(
            feedback.getId(),
            feedback.getCustomerId(),
            feedback.getGuestName(),
            feedback.getGuestPhone(),
            feedback.getInvoiceId(),
            feedback.getRating(),
            feedback.getComment(),
            feedback.getStaffResponse(),
            feedback.getIsPublic(),
            feedback.getCreatedAt()
        );
    }
}

