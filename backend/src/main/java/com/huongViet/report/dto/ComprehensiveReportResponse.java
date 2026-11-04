package com.huongViet.report.dto;

import java.util.List;

public record ComprehensiveReportResponse(
    ReportFilterType filterType,
    String startDate,
    String endDate,
    boolean hasData,
    RevenueSummaryResponse revenueSummary,
    List<RevenueTimelinePoint> timeline,
    ReservationStatsResponse reservationStats,
    List<DishSalesItemResponse> topDishes
) {
    public static ComprehensiveReportResponse empty(
        ReportFilterType filterType,
        String startDate,
        String endDate
    ) {
        return new ComprehensiveReportResponse(
            filterType,
            startDate,
            endDate,
            false,
            RevenueSummaryResponse.empty(),
            List.of(),
            ReservationStatsResponse.empty(),
            List.of()
        );
    }
}

