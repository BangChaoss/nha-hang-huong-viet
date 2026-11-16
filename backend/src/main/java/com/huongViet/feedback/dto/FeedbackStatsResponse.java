package com.huongViet.feedback.dto;

import java.util.Map;

public record FeedbackStatsResponse(
    long totalReviews,
    double averageRating,
    Map<Integer, Long> ratingCounts,
    long unrepliedCount
) {}

