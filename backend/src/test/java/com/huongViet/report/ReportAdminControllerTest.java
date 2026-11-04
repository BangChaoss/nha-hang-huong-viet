package com.huongViet.report;

import com.huongViet.config.GlobalExceptionHandler;
import com.huongViet.report.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ReportAdminControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ReportService reportService;

    @Mock
    private ReportExportService reportExportService;

    @InjectMocks
    private ReportAdminController reportAdminController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(reportAdminController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    @DisplayName("GET /api/admin/reports/overview returns 200 and comprehensive report")
    void testGetOverviewReport() throws Exception {
        ComprehensiveReportResponse mockResponse = new ComprehensiveReportResponse(
            ReportFilterType.DAILY,
            "2026-10-26 00:00:00",
            "2026-10-26 23:59:59",
            true,
            new RevenueSummaryResponse(
                BigDecimal.valueOf(1000000),
                BigDecimal.valueOf(1000000),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                5L,
                BigDecimal.valueOf(200000),
                BigDecimal.valueOf(1000000),
                BigDecimal.ZERO,
                BigDecimal.ZERO
            ),
            List.of(new RevenueTimelinePoint("12:00", BigDecimal.valueOf(500000), 2L)),
            new ReservationStatsResponse(10L, 8L, 2L, 0L, 0L, 0L, 80.0, 0.0),
            List.of(new DishSalesItemResponse("d1", "Phở Bò", "Món chính", 10L, BigDecimal.valueOf(500000), 50.0))
        );

        when(reportService.getComprehensiveReport(any(), any(), any(), any()))
            .thenReturn(mockResponse);

        mockMvc.perform(get("/api/admin/reports/overview")
                .param("filterType", "DAILY")
                .param("from", "2026-10-26")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.filterType").value("DAILY"))
            .andExpect(jsonPath("$.hasData").value(true))
            .andExpect(jsonPath("$.revenueSummary.totalInvoices").value(5))
            .andExpect(jsonPath("$.revenueSummary.netRevenue").value(1000000))
            .andExpect(jsonPath("$.reservationStats.totalBookings").value(10))
            .andExpect(jsonPath("$.topDishes[0].dishName").value("Phở Bò"));
    }

    @Test
    @DisplayName("GET /api/admin/reports/revenue returns 200 and revenue summary")
    void testGetRevenueSummary() throws Exception {
        RevenueSummaryResponse summary = new RevenueSummaryResponse(
            BigDecimal.valueOf(2500000),
            BigDecimal.valueOf(2600000),
            BigDecimal.valueOf(100000),
            BigDecimal.valueOf(100000),
            BigDecimal.ZERO,
            12L,
            BigDecimal.valueOf(208333.33),
            BigDecimal.valueOf(1500000),
            BigDecimal.valueOf(1000000),
            BigDecimal.ZERO
        );

        when(reportService.getRevenueSummary(any(), any(), any())).thenReturn(summary);

        mockMvc.perform(get("/api/admin/reports/revenue")
                .param("filterType", "MONTHLY")
                .param("from", "2026-10")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.netRevenue").value(2500000))
            .andExpect(jsonPath("$.cashRevenue").value(1500000))
            .andExpect(jsonPath("$.vnpayRevenue").value(1000000));
    }

    @Test
    @DisplayName("GET /api/admin/reports/timeline returns 200 and list of timeline points")
    void testGetTimeline() throws Exception {
        List<RevenueTimelinePoint> points = List.of(
            new RevenueTimelinePoint("10:00", BigDecimal.valueOf(300000), 1L),
            new RevenueTimelinePoint("11:00", BigDecimal.valueOf(700000), 3L)
        );

        when(reportService.getRevenueTimeline(any(), any(), any())).thenReturn(points);

        mockMvc.perform(get("/api/admin/reports/timeline")
                .param("filterType", "DAILY")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].timeLabel").value("10:00"))
            .andExpect(jsonPath("$[1].timeLabel").value("11:00"));
    }

    @Test
    @DisplayName("GET /api/admin/reports/reservations returns 200 and booking stats")
    void testGetReservations() throws Exception {
        ReservationStatsResponse stats = new ReservationStatsResponse(
            20L, 15L, 3L, 2L, 0L, 0L, 75.0, 10.0
        );

        when(reportService.getReservationStats(any(), any(), any())).thenReturn(stats);

        mockMvc.perform(get("/api/admin/reports/reservations")
                .param("filterType", "MONTHLY")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalBookings").value(20))
            .andExpect(jsonPath("$.arrivedCount").value(15))
            .andExpect(jsonPath("$.completionRate").value(75.0));
    }

    @Test
    @DisplayName("GET /api/admin/reports/dish-sales returns 200 and dish sales list")
    void testGetDishSales() throws Exception {
        List<DishSalesItemResponse> dishes = List.of(
            new DishSalesItemResponse("d1", "Bún Bò Huế", "Món chính", 15L, BigDecimal.valueOf(900000), 60.0),
            new DishSalesItemResponse("d2", "Trà Sen", "Đồ uống", 20L, BigDecimal.valueOf(600000), 40.0)
        );

        when(reportService.getDishSalesPerformance(any(), any(), any(), any())).thenReturn(dishes);

        mockMvc.perform(get("/api/admin/reports/dish-sales")
                .param("limit", "5")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].dishName").value("Bún Bò Huế"))
            .andExpect(jsonPath("$[1].dishName").value("Trà Sen"));
    }

    @Test
    @DisplayName("GET /api/admin/reports/export/csv returns 200 and attachment header")
    void testExportCsv() throws Exception {
        ComprehensiveReportResponse mockReport = ComprehensiveReportResponse.empty(
            ReportFilterType.DAILY, "2026-10-26 00:00:00", "2026-10-26 23:59:59"
        );
        byte[] csvContent = "fake,csv,data\n".getBytes(StandardCharsets.UTF_8);

        when(reportService.getComprehensiveReport(any(), any(), any(), any())).thenReturn(mockReport);
        when(reportExportService.exportComprehensiveReportToCsv(any())).thenReturn(csvContent);

        mockMvc.perform(get("/api/admin/reports/export/csv")
                .param("filterType", "DAILY")
                .param("from", "2026-10-26"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString("attachment; filename=\"Bao-cao-Huong-Viet-DAILY.csv\"")))
            .andExpect(content().contentType("text/csv;charset=UTF-8"))
            .andExpect(content().bytes(csvContent));
    }
}
