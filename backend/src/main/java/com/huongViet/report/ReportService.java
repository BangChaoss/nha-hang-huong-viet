package com.huongViet.report;

import com.huongViet.report.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Service xử lý nghiệp vụ thống kê báo cáo doanh thu, đặt bàn và món ăn bán chạy (UC24).
 *
 * <p>Quy tắc nghiệp vụ:
 * - BR-64: Doanh thu tính chuẩn xác từ các hóa đơn đã thanh toán thành công (invoices status = PAID).
 * - BR-65: Tính toán tổng hợp thời gian thực trực tiếp từ cơ sở dữ liệu.
 * - E1: Khi không có dữ liệu trong khoảng thời gian chọn, trả về payload chuẩn chỉ số 0 và hasData = false.</p>
 */
@Service
@Transactional(readOnly = true)
public class ReportService {

    private static final Logger logger = LoggerFactory.getLogger(ReportService.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ReportRepository reportRepository;

    public ReportService(ReportRepository reportRepository) {
        this.reportRepository = reportRepository;
    }

    /**
     * Lấy báo cáo tổng hợp hợp nhất bao gồm doanh thu, timeline, đặt bàn và top món ăn.
     */
    public ComprehensiveReportResponse getComprehensiveReport(
        ReportFilterType filterType,
        String fromDateStr,
        String toDateStr,
        Integer dishLimit
    ) {
        TimeRange range = resolveTimeRange(filterType, fromDateStr, toDateStr);
        logger.info("Generating comprehensive report [type={}, from={}, to={}]",
            range.type(), range.start(), range.end());

        RevenueSummaryResponse revenueSummary = reportRepository.getRevenueSummary(range.start(), range.end());
        List<RevenueTimelinePoint> timeline = reportRepository.getRevenueTimeline(range.start(), range.end(), range.type());
        ReservationStatsResponse reservationStats = reportRepository.getReservationStats(range.startDate(), range.endDate());
        List<DishSalesItemResponse> topDishes = reportRepository.getDishSalesPerformance(range.start(), range.end(), dishLimit != null ? dishLimit : 10);

        boolean hasData = revenueSummary.totalInvoices() > 0 
            || reservationStats.totalBookings() > 0 
            || !topDishes.isEmpty();

        return new ComprehensiveReportResponse(
            range.type(),
            range.start().format(DATE_TIME_FORMATTER),
            range.end().format(DATE_TIME_FORMATTER),
            hasData,
            revenueSummary,
            timeline,
            reservationStats,
            topDishes
        );
    }

    /**
     * Lấy riêng phần tóm tắt doanh thu.
     */
    public RevenueSummaryResponse getRevenueSummary(ReportFilterType filterType, String fromDateStr, String toDateStr) {
        TimeRange range = resolveTimeRange(filterType, fromDateStr, toDateStr);
        return reportRepository.getRevenueSummary(range.start(), range.end());
    }

    /**
     * Lấy danh sách điểm dữ liệu trục thời gian để vẽ biểu đồ.
     */
    public List<RevenueTimelinePoint> getRevenueTimeline(ReportFilterType filterType, String fromDateStr, String toDateStr) {
        TimeRange range = resolveTimeRange(filterType, fromDateStr, toDateStr);
        return reportRepository.getRevenueTimeline(range.start(), range.end(), range.type());
    }

    /**
     * Lấy thống kê số lượng và trạng thái đặt bàn.
     */
    public ReservationStatsResponse getReservationStats(ReportFilterType filterType, String fromDateStr, String toDateStr) {
        TimeRange range = resolveTimeRange(filterType, fromDateStr, toDateStr);
        return reportRepository.getReservationStats(range.startDate(), range.endDate());
    }

    /**
     * Lấy báo cáo hiệu suất món ăn bán chạy.
     */
    public List<DishSalesItemResponse> getDishSalesPerformance(
        ReportFilterType filterType,
        String fromDateStr,
        String toDateStr,
        Integer limit
    ) {
        TimeRange range = resolveTimeRange(filterType, fromDateStr, toDateStr);
        return reportRepository.getDishSalesPerformance(range.start(), range.end(), limit);
    }

    /**
     * Giải quyết khung thời gian [start, end] dựa trên loại bộ lọc và chuỗi ngày truyền vào.
     */
    public TimeRange resolveTimeRange(ReportFilterType filterType, String fromDateStr, String toDateStr) {
        ReportFilterType type = filterType != null ? filterType : ReportFilterType.DAILY;

        LocalDate startDate;
        LocalDate endDate;

        switch (type) {
            case DAILY -> {
                startDate = parseLocalDateOrFallback(fromDateStr, LocalDate.now());
                endDate = startDate;
            }
            case MONTHLY -> {
                YearMonth ym = parseYearMonthOrFallback(fromDateStr, YearMonth.now());
                startDate = ym.atDay(1);
                endDate = ym.atEndOfMonth();
            }
            case YEARLY -> {
                int year = parseYearOrFallback(fromDateStr, Year.now().getValue());
                startDate = LocalDate.of(year, 1, 1);
                endDate = LocalDate.of(year, 12, 31);
            }
            case CUSTOM -> {
                startDate = parseLocalDateOrFallback(fromDateStr, LocalDate.now().minusDays(30));
                endDate = parseLocalDateOrFallback(toDateStr, LocalDate.now());
                if (startDate.isAfter(endDate)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Từ ngày không được lớn hơn đến ngày.");
                }
            }
            default -> {
                startDate = LocalDate.now();
                endDate = startDate;
            }
        }

        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        return new TimeRange(start, end, startDate, endDate, type);
    }

    private LocalDate parseLocalDateOrFallback(String value, LocalDate fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        try {
            String trimmed = value.trim();
            if (trimmed.length() >= 10) {
                return LocalDate.parse(trimmed.substring(0, 10));
            }
            return LocalDate.parse(trimmed);
        } catch (DateTimeParseException e) {
            logger.warn("Could not parse date string '{}', fallback to {}", value, fallback);
            return fallback;
        }
    }

    private YearMonth parseYearMonthOrFallback(String value, YearMonth fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        try {
            String trimmed = value.trim();
            if (trimmed.length() >= 7) {
                return YearMonth.parse(trimmed.substring(0, 7));
            }
            return YearMonth.parse(trimmed);
        } catch (DateTimeParseException e) {
            logger.warn("Could not parse YearMonth string '{}', fallback to {}", value, fallback);
            return fallback;
        }
    }

    private int parseYearOrFallback(String value, int fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        try {
            String trimmed = value.trim();
            if (trimmed.length() >= 4) {
                return Integer.parseInt(trimmed.substring(0, 4));
            }
            return Integer.parseInt(trimmed);
        } catch (NumberFormatException e) {
            logger.warn("Could not parse Year string '{}', fallback to {}", value, fallback);
            return fallback;
        }
    }

    public record TimeRange(
        LocalDateTime start,
        LocalDateTime end,
        LocalDate startDate,
        LocalDate endDate,
        ReportFilterType type
    ) {}
}

