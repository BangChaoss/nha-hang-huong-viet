package com.huongViet.report;

import com.huongViet.report.dto.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Controller cung cấp API thống kê doanh thu, đặt bàn và món ăn bán chạy (UC24 - View Revenue Report).
 *
 * <p>Quy tắc bảo mật:
 * - BR-63: Chỉ người dùng có vai trò MANAGER hoặc ADMIN mới có quyền truy cập dữ liệu tài chính.
 *   Nhân viên phục vụ (STAFF) và Đầu bếp (CHEF) bị chặn truy cập.</p>
 */
@RestController
@RequestMapping("/api/admin/reports")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})
@PreAuthorize("hasAnyAuthority('MANAGER', 'ROLE_MANAGER', 'ADMIN', 'ROLE_ADMIN')")
public class ReportAdminController {

    private final ReportService reportService;
    private final ReportExportService reportExportService;

    public ReportAdminController(ReportService reportService, ReportExportService reportExportService) {
        this.reportService = reportService;
        this.reportExportService = reportExportService;
    }

    /**
     * Lấy báo cáo tổng hợp hợp nhất (doanh thu, biểu đồ timeline, đặt bàn, top món ăn).
     */
    @GetMapping("/overview")
    public ResponseEntity<ComprehensiveReportResponse> getOverviewReport(
        @RequestParam(value = "filterType", required = false, defaultValue = "DAILY") ReportFilterType filterType,
        @RequestParam(value = "from", required = false) String from,
        @RequestParam(value = "to", required = false) String to,
        @RequestParam(value = "dishLimit", required = false, defaultValue = "10") Integer dishLimit
    ) {
        ComprehensiveReportResponse report = reportService.getComprehensiveReport(filterType, from, to, dishLimit);
        return ResponseEntity.ok(report);
    }

    /**
     * Lấy riêng tóm tắt doanh thu thuần, gross, giảm giá, VAT và AOV.
     */
    @GetMapping("/revenue")
    public ResponseEntity<RevenueSummaryResponse> getRevenueSummary(
        @RequestParam(value = "filterType", required = false, defaultValue = "DAILY") ReportFilterType filterType,
        @RequestParam(value = "from", required = false) String from,
        @RequestParam(value = "to", required = false) String to
    ) {
        RevenueSummaryResponse summary = reportService.getRevenueSummary(filterType, from, to);
        return ResponseEntity.ok(summary);
    }

    /**
     * Lấy danh sách điểm dữ liệu trục thời gian phục vụ vẽ biểu đồ (giờ/ngày/tháng).
     */
    @GetMapping("/timeline")
    public ResponseEntity<List<RevenueTimelinePoint>> getRevenueTimeline(
        @RequestParam(value = "filterType", required = false, defaultValue = "DAILY") ReportFilterType filterType,
        @RequestParam(value = "from", required = false) String from,
        @RequestParam(value = "to", required = false) String to
    ) {
        List<RevenueTimelinePoint> timeline = reportService.getRevenueTimeline(filterType, from, to);
        return ResponseEntity.ok(timeline);
    }

    /**
     * Lấy thống kê số lượng đặt bàn theo trạng thái (ARRIVED, CONFIRMED, CANCELLED, NO_SHOW).
     */
    @GetMapping("/reservations")
    public ResponseEntity<ReservationStatsResponse> getReservationStats(
        @RequestParam(value = "filterType", required = false, defaultValue = "DAILY") ReportFilterType filterType,
        @RequestParam(value = "from", required = false) String from,
        @RequestParam(value = "to", required = false) String to
    ) {
        ReservationStatsResponse stats = reportService.getReservationStats(filterType, from, to);
        return ResponseEntity.ok(stats);
    }

    /**
     * Lấy thống kê hiệu suất món ăn bán chạy xếp theo số lượng và doanh thu (Daily Dish Sales Summary).
     */
    @GetMapping("/dish-sales")
    public ResponseEntity<List<DishSalesItemResponse>> getDishSalesPerformance(
        @RequestParam(value = "filterType", required = false, defaultValue = "DAILY") ReportFilterType filterType,
        @RequestParam(value = "from", required = false) String from,
        @RequestParam(value = "to", required = false) String to,
        @RequestParam(value = "limit", required = false, defaultValue = "10") Integer limit
    ) {
        List<DishSalesItemResponse> dishSales = reportService.getDishSalesPerformance(filterType, from, to, limit);
        return ResponseEntity.ok(dishSales);
    }

    /**
     * Xuất toàn bộ báo cáo doanh thu, đặt bàn, và món ăn ra file CSV chuẩn UTF-8 Excel (BR-66).
     */
    @GetMapping("/export/csv")
    public ResponseEntity<byte[]> exportComprehensiveReportCsv(
        @RequestParam(value = "filterType", required = false, defaultValue = "DAILY") ReportFilterType filterType,
        @RequestParam(value = "from", required = false) String from,
        @RequestParam(value = "to", required = false) String to,
        @RequestParam(value = "dishLimit", required = false, defaultValue = "100") Integer dishLimit
    ) {
        ComprehensiveReportResponse report = reportService.getComprehensiveReport(filterType, from, to, dishLimit);
        byte[] csvBytes = reportExportService.exportComprehensiveReportToCsv(report);

        String rawFilename = "Bao-cao-Huong-Viet-" + report.filterType() + ".csv";
        String encodedFilename = URLEncoder.encode(rawFilename, StandardCharsets.UTF_8).replace("+", "%20");

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + rawFilename + "\"; filename*=UTF-8''" + encodedFilename)
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .body(csvBytes);
    }
}
