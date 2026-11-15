package com.huongViet.report.dto;

public record ReservationStatsResponse(
    long totalBookings,
    long arrivedCount,
    long confirmedCount,
    long cancelledCount,
    long noShowCount,
    long pendingCount,
    long paidCount,
    double completionRate,
    double cancellationRate,
    double paidRate
) {
    public static ReservationStatsResponse empty() {
        return new ReservationStatsResponse(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0.0, 0.0, 0.0);
    }
}
