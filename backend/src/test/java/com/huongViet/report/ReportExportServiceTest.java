package com.huongViet.report;

import com.huongViet.report.dto.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReportExportServiceTest {

    private final ReportExportService exportService = new ReportExportService();

    @Test
    @DisplayName("exportComprehensiveReportToCsv: throws 400 when report has no data (E1)")
    void testExportNoDataThrowsException() {
        ComprehensiveReportResponse emptyReport = ComprehensiveReportResponse.empty(
            ReportFilterType.DAILY, "2026-10-26 00:00:00", "2026-10-26 23:59:59"
        );

        assertThrows(ResponseStatusException.class, () ->
            exportService.exportComprehensiveReportToCsv(emptyReport)
        );
    }

    @Test
    @DisplayName("exportComprehensiveReportToCsv: generates valid CSV with UTF-8 BOM when data exists (BR-66)")
    void testExportValidCsv() {
        ComprehensiveReportResponse report = new ComprehensiveReportResponse(
            ReportFilterType.DAILY,
            "2026-10-26 00:00:00",
            "2026-10-26 23:59:59",
            true,
            new RevenueSummaryResponse(
                BigDecimal.valueOf(1250000),
                BigDecimal.valueOf(1300000),
                BigDecimal.valueOf(50000),
                BigDecimal.valueOf(50000),
                BigDecimal.ZERO,
                4L,
                BigDecimal.valueOf(312500),
                BigDecimal.valueOf(1000000),
                BigDecimal.valueOf(250000),
                BigDecimal.ZERO
            ),
            List.of(new RevenueTimelinePoint("12:00", BigDecimal.valueOf(1250000), 4L)),
            new ReservationStatsResponse(5L, 4L, 1L, 0L, 0L, 0L, 80.0, 0.0),
            List.of(new DishSalesItemResponse("dish-1", "Phở Bò Đặc Biệt", "Món chính", 8L, BigDecimal.valueOf(720000), 57.6))
        );

        byte[] bytes = exportService.exportComprehensiveReportToCsv(report);

        assertNotNull(bytes);
        assertTrue(bytes.length > 3);

        // Check UTF-8 BOM: EF BB BF
        assertEquals((byte) 0xEF, bytes[0]);
        assertEquals((byte) 0xBB, bytes[1]);
        assertEquals((byte) 0xBF, bytes[2]);

        String content = new String(bytes, StandardCharsets.UTF_8);
        assertTrue(content.contains("NHÀ HÀNG HƯƠNG VIỆT"));
        assertTrue(content.contains("Phở Bò Đặc Biệt"));
        assertTrue(content.contains("1,250,000"));
        assertTrue(content.contains("VNPay"));
    }
}

