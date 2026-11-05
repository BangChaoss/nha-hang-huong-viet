package com.huongViet.report;

import com.huongViet.report.dto.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Service xuất dữ liệu báo cáo ra file CSV/Excel chuẩn UTF-8 (BR-66: Export Accuracy).
 *
 * <p>Quy tắc nghiệp vụ:
 * - BR-66: Dữ liệu xuất phải khớp chính xác 100% với dữ liệu lọc trên màn hình tại thời điểm xuất.
 * - E1: Khi không có dữ liệu (hasData = false), ném lỗi 400 Bad Request để ngăn xuất file rỗng.</p>
 */
@Service
public class ReportExportService {

    private static final DateTimeFormatter EXPORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DecimalFormat CURRENCY_FORMAT = new DecimalFormat("#,##0");
    private static final DecimalFormat PERCENT_FORMAT = new DecimalFormat("0.0");

    /**
     * Xuất báo cáo tổng hợp ra mảng byte CSV có gắn UTF-8 BOM (\uFEFF)
     * để Microsoft Excel trên Windows tự động nhận diện tiếng Việt có dấu.
     */
    public byte[] exportComprehensiveReportToCsv(ComprehensiveReportResponse report) {
        if (report == null || !report.hasData()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không có dữ liệu để xuất báo cáo cho khoảng thời gian đã chọn.");
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            // Ghi UTF-8 BOM
            baos.write(0xEF);
            baos.write(0xBB);
            baos.write(0xBF);

            try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8))) {
                // Header báo cáo
                writer.println("NHÀ HÀNG HƯƠNG VIỆT - BÁO CÁO DOANH THU & HIỆU SUẤT KINH DOANH");
                writer.println("Thời gian xuất:," + escapeCsv(LocalDateTime.now().format(EXPORT_TIME_FORMATTER)));
                writer.println("Khoảng thời gian:," + escapeCsv(report.startDate() + " đến " + report.endDate()));
                writer.println("Loại bộ lọc:," + escapeCsv(report.filterType().name()));
                writer.println();

                // 1. TỔNG QUAN DOANH THU & ĐẶT BÀN
                writer.println("=== 1. TỔNG QUAN DOANH THU VÀ ĐẶT BÀN ===");
                RevenueSummaryResponse rev = report.revenueSummary();
                ReservationStatsResponse res = report.reservationStats();

                writer.println("Chỉ số,Giá trị,Đơn vị");
                writer.println("Doanh thu thuần (Net Revenue)," + formatNumber(rev.netRevenue()) + ",VNĐ");
                writer.println("Doanh thu trước giảm giá (Gross)," + formatNumber(rev.grossRevenue()) + ",VNĐ");
                writer.println("Giảm giá Voucher," + formatNumber(rev.totalVoucherDiscount()) + ",VNĐ");
                writer.println("Giảm giá khác / Điểm thưởng," + formatNumber(rev.totalDiscount()) + ",VNĐ");
                writer.println("Thuế VAT," + formatNumber(rev.totalTax()) + ",VNĐ");
                writer.println("Doanh thu Tiền mặt," + formatNumber(rev.cashRevenue()) + ",VNĐ");
                writer.println("Doanh thu VNPay," + formatNumber(rev.vnpayRevenue()) + ",VNĐ");
                writer.println("Tổng số hóa đơn hoàn tất," + rev.totalInvoices() + ",Hóa đơn");
                writer.println("Giá trị trung bình đơn (AOV)," + formatNumber(rev.averageOrderValue()) + ",VNĐ/đơn");
                writer.println("Tổng số lượt đặt bàn," + res.totalBookings() + ",Lượt");
                writer.println("Khách đã đến dùng bữa (ARRIVED)," + res.arrivedCount() + ",Lượt (" + PERCENT_FORMAT.format(res.completionRate()) + "%)");
                writer.println("Lượt đặt bàn đã xác nhận (CONFIRMED)," + res.confirmedCount() + ",Lượt");
                writer.println("Lượt hủy bàn (CANCELLED)," + res.cancelledCount() + ",Lượt (" + PERCENT_FORMAT.format(res.cancellationRate()) + "%)");
                writer.println("Khách không đến (NO SHOW)," + res.noShowCount() + ",Lượt");
                writer.println();

                // 2. DIỄN BIẾN DOANH THU THEO MỐC THỜI GIAN
                writer.println("=== 2. DIỄN BIẾN DOANH THU THEO MỐC THỜI GIAN ===");
                writer.println("Mốc thời gian,Số đơn hàng,Doanh thu (VNĐ)");
                List<RevenueTimelinePoint> timeline = report.timeline();
                if (timeline != null && !timeline.isEmpty()) {
                    for (RevenueTimelinePoint pt : timeline) {
                        writer.println(
                            escapeCsv(pt.timeLabel()) + "," +
                            pt.orderCount() + "," +
                            formatNumber(pt.revenue())
                        );
                    }
                } else {
                    writer.println("Không có dữ liệu theo mốc thời gian,,");
                }
                writer.println();

                // 3. HIỆU SUẤT BÁN MÓN ĂN (DISH SALES PERFORMANCE)
                writer.println("=== 3. HIỆU SUẤT BÁN MÓN ĂN ===");
                writer.println("STT,Mã món,Tên món,Danh mục,Số lượng bán,Doanh thu (VNĐ),Tỷ trọng doanh thu (%)");
                List<DishSalesItemResponse> dishes = report.topDishes();
                if (dishes != null && !dishes.isEmpty()) {
                    int index = 1;
                    for (DishSalesItemResponse dish : dishes) {
                        writer.println(
                            index++ + "," +
                            escapeCsv(dish.dishId()) + "," +
                            escapeCsv(dish.dishName()) + "," +
                            escapeCsv(dish.categoryName()) + "," +
                            dish.quantitySold() + "," +
                            formatNumber(dish.totalSales()) + "," +
                            PERCENT_FORMAT.format(dish.percentageOfTotal()) + "%"
                        );
                    }
                } else {
                    writer.println("Không có món ăn nào bán ra trong khoảng thời gian này,,,,,,");
                }

                writer.flush();
            }

            return baos.toByteArray();
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi khi xuất file báo cáo: " + e.getMessage(), e);
        }
    }

    private String formatNumber(BigDecimal number) {
        if (number == null) {
            return "0";
        }
        return CURRENCY_FORMAT.format(number);
    }

    private String escapeCsv(String input) {
        if (input == null) {
            return "";
        }
        if (input.contains(",") || input.contains("\"") || input.contains("\n") || input.contains("\r")) {
            return "\"" + input.replace("\"", "\"\"") + "\"";
        }
        return input;
    }
}

