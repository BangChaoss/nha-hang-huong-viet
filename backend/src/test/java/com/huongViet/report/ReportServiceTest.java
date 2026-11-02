package com.huongViet.report;

import com.huongViet.report.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private ReportRepository reportRepository;

    private ReportService reportService;

    @BeforeEach
    void setUp() {
        reportService = new ReportService(reportRepository);
    }

    @Test
    @DisplayName("resolveTimeRange: DAILY correctly resolves today or specified date")
    void testResolveTimeRangeDaily() {
        ReportService.TimeRange range = reportService.resolveTimeRange(ReportFilterType.DAILY, "2026-10-26", null);
        assertEquals(LocalDate.of(2026, 10, 26), range.startDate());
        assertEquals(LocalDate.of(2026, 10, 26), range.endDate());
        assertEquals(LocalDateTime.of(2026, 10, 26, 0, 0, 0), range.start());
    }

    @Test
    @DisplayName("resolveTimeRange: MONTHLY correctly resolves start and end of month")
    void testResolveTimeRangeMonthly() {
        ReportService.TimeRange range = reportService.resolveTimeRange(ReportFilterType.MONTHLY, "2026-10", null);
        assertEquals(LocalDate.of(2026, 10, 1), range.startDate());
        assertEquals(LocalDate.of(2026, 10, 31), range.endDate());
    }

    @Test
    @DisplayName("resolveTimeRange: YEARLY correctly resolves start and end of year")
    void testResolveTimeRangeYearly() {
        ReportService.TimeRange range = reportService.resolveTimeRange(ReportFilterType.YEARLY, "2026", null);
        assertEquals(LocalDate.of(2026, 1, 1), range.startDate());
        assertEquals(LocalDate.of(2026, 12, 31), range.endDate());
    }

    @Test
    @DisplayName("resolveTimeRange: CUSTOM throws BAD_REQUEST when startDate > endDate")
    void testResolveTimeRangeCustomInvalid() {
        assertThrows(ResponseStatusException.class, () ->
            reportService.resolveTimeRange(ReportFilterType.CUSTOM, "2026-11-10", "2026-11-01")
        );
    }

    @Test
    @DisplayName("getComprehensiveReport: returns hasData = false when no invoices or bookings (E1)")
    void testGetComprehensiveReportNoData() {
        when(reportRepository.getRevenueSummary(any(), any())).thenReturn(RevenueSummaryResponse.empty());
        when(reportRepository.getRevenueTimeline(any(), any(), any())).thenReturn(Collections.emptyList());
        when(reportRepository.getReservationStats(any(), any())).thenReturn(ReservationStatsResponse.empty());
        when(reportRepository.getDishSalesPerformance(any(), any(), any())).thenReturn(Collections.emptyList());

        ComprehensiveReportResponse response = reportService.getComprehensiveReport(
            ReportFilterType.DAILY, "2026-10-26", null, 10
        );

        assertNotNull(response);
        assertFalse(response.hasData(), "E1: hasData should be false when no data exists");
        assertEquals(0L, response.revenueSummary().totalInvoices());
        assertEquals(0L, response.reservationStats().totalBookings());
        assertTrue(response.topDishes().isEmpty());
    }

    @Test
    @DisplayName("getComprehensiveReport: returns hasData = true when data exists")
    void testGetComprehensiveReportWithData() {
        RevenueSummaryResponse summary = new RevenueSummaryResponse(
            BigDecimal.valueOf(500000),
            BigDecimal.valueOf(500000),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            2L,
            BigDecimal.valueOf(250000),
            BigDecimal.valueOf(500000),
            BigDecimal.ZERO,
            BigDecimal.ZERO
        );

        when(reportRepository.getRevenueSummary(any(), any())).thenReturn(summary);
        when(reportRepository.getRevenueTimeline(any(), any(), any())).thenReturn(List.of(
            new RevenueTimelinePoint("12:00", BigDecimal.valueOf(500000), 2L)
        ));
        when(reportRepository.getReservationStats(any(), any())).thenReturn(new ReservationStatsResponse(
            5L, 3L, 1L, 1L, 0L, 0L, 60.0, 20.0
        ));
        when(reportRepository.getDishSalesPerformance(any(), any(), any())).thenReturn(List.of(
            new DishSalesItemResponse("dish-1", "Phở Bò", "Món chính", 4L, BigDecimal.valueOf(320000), 64.0)
        ));

        ComprehensiveReportResponse response = reportService.getComprehensiveReport(
            ReportFilterType.DAILY, "2026-10-26", null, 10
        );

        assertNotNull(response);
        assertTrue(response.hasData());
        assertEquals(2L, response.revenueSummary().totalInvoices());
        assertEquals(BigDecimal.valueOf(500000), response.revenueSummary().netRevenue());
        assertEquals(5L, response.reservationStats().totalBookings());
        assertEquals(1, response.topDishes().size());
        assertEquals("Phở Bò", response.topDishes().get(0).dishName());
    }
}

