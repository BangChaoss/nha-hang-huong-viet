package com.huongViet.report;

import com.huongViet.report.dto.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Repository
public class ReportRepository {

    private final JdbcTemplate jdbcTemplate;

    public ReportRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Aggregates financial revenue from completed invoices (UC18/BR-64).
     */
    public RevenueSummaryResponse getRevenueSummary(LocalDateTime start, LocalDateTime end) {
        Timestamp startTs = Timestamp.valueOf(start);
        Timestamp endTs = Timestamp.valueOf(end);

        String invoiceSql = """
            SELECT 
                COALESCE(SUM(final_amount), 0) AS net_revenue,
                COALESCE(SUM(subtotal_amount), 0) AS gross_revenue,
                COALESCE(SUM(voucher_discount_amount), 0) AS voucher_discount,
                COALESCE(SUM(discount_amount), 0) AS total_discount,
                COALESCE(SUM(tax_amount), 0) AS total_tax,
                COUNT(*) AS total_invoices
            FROM invoices
            WHERE status = 'PAID'
              AND COALESCE(paid_at, created_at) >= ?
              AND COALESCE(paid_at, created_at) <= ?
            """;

        RevenueRaw raw = jdbcTemplate.query(invoiceSql, rs -> {
            if (rs.next()) {
                return new RevenueRaw(
                    rs.getBigDecimal("net_revenue"),
                    rs.getBigDecimal("gross_revenue"),
                    rs.getBigDecimal("voucher_discount"),
                    rs.getBigDecimal("total_discount"),
                    rs.getBigDecimal("total_tax"),
                    rs.getLong("total_invoices")
                );
            }
            return new RevenueRaw(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0L);
        }, startTs, endTs);

        if (raw == null || raw.totalInvoices == 0L) {
            return RevenueSummaryResponse.empty();
        }

        // Query VNPay revenue from payment_orders table
        String vnpaySql = """
            SELECT COALESCE(SUM(amount), 0) AS vnpay_total
            FROM payment_orders
            WHERE status IN ('PAID', 'SUCCESS')
              AND COALESCE(paid_at, created_at) >= ?
              AND COALESCE(paid_at, created_at) <= ?
            """;

        BigDecimal vnpayRevenue = jdbcTemplate.query(vnpaySql, rs -> {
            if (rs.next()) {
                BigDecimal val = rs.getBigDecimal("vnpay_total");
                return val != null ? val : BigDecimal.ZERO;
            }
            return BigDecimal.ZERO;
        }, startTs, endTs);

        if (vnpayRevenue == null) {
            vnpayRevenue = BigDecimal.ZERO;
        }

        // Cash revenue is remaining net revenue
        BigDecimal cashRevenue = raw.netRevenue.subtract(vnpayRevenue);
        if (cashRevenue.compareTo(BigDecimal.ZERO) < 0) {
            cashRevenue = BigDecimal.ZERO;
        }

        BigDecimal aov = raw.totalInvoices > 0
            ? raw.netRevenue.divide(BigDecimal.valueOf(raw.totalInvoices), 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        return new RevenueSummaryResponse(
            raw.netRevenue,
            raw.grossRevenue,
            raw.voucherDiscount,
            raw.totalDiscount,
            raw.totalTax,
            raw.totalInvoices,
            aov,
            cashRevenue,
            vnpayRevenue,
            BigDecimal.ZERO
        );
    }

    /**
     * Aggregates revenue timeline data points based on filter type.
     */
    public List<RevenueTimelinePoint> getRevenueTimeline(LocalDateTime start, LocalDateTime end, ReportFilterType filterType) {
        Timestamp startTs = Timestamp.valueOf(start);
        Timestamp endTs = Timestamp.valueOf(end);

        String formatPattern = switch (filterType) {
            case DAILY -> "%H:00";
            case YEARLY -> "%Y-%m";
            case MONTHLY, CUSTOM -> "%Y-%m-%d";
        };

        String sql = """
            SELECT 
                DATE_FORMAT(COALESCE(paid_at, created_at), '""" + formatPattern + """
            ') AS time_label,
                COALESCE(SUM(final_amount), 0) AS revenue,
                COUNT(*) AS order_count
            FROM invoices
            WHERE status = 'PAID'
              AND COALESCE(paid_at, created_at) >= ?
              AND COALESCE(paid_at, created_at) <= ?
            GROUP BY time_label
            ORDER BY time_label ASC
            """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new RevenueTimelinePoint(
            rs.getString("time_label"),
            rs.getBigDecimal("revenue"),
            rs.getLong("order_count")
        ), startTs, endTs);
    }

    /**
     * Aggregates booking volume by reservation status (BR-64).
     */
    public ReservationStatsResponse getReservationStats(LocalDate startDate, LocalDate endDate) {
        String sql = """
            SELECT status, COUNT(*) AS count_val
            FROM reservations
            WHERE deleted_at IS NULL
              AND reservation_date >= ?
              AND reservation_date <= ?
            GROUP BY status
            """;

        Map<String, Long> statusCounts = new HashMap<>();
        jdbcTemplate.query(sql, rs -> {
            statusCounts.put(rs.getString("status"), rs.getLong("count_val"));
        }, startDate, endDate);

        long arrived = statusCounts.getOrDefault("ARRIVED", 0L);
        long confirmed = statusCounts.getOrDefault("CONFIRMED", 0L);
        long cancelled = statusCounts.getOrDefault("CANCELLED", 0L);
        long noShow = statusCounts.getOrDefault("NO_SHOW", 0L);
        long pending = statusCounts.getOrDefault("PENDING_HOLD", 0L) + statusCounts.getOrDefault("PENDING_DEPOSIT", 0L);
        long paid = statusCounts.getOrDefault("PAID", 0L) + statusCounts.getOrDefault("REVIEWED", 0L);

        long total = arrived + confirmed + cancelled + noShow + pending + paid;
        if (total == 0L) {
            return ReservationStatsResponse.empty();
        }

        double completionRate = Math.round(((arrived + paid) * 100.0 / total) * 10.0) / 10.0;
        double cancellationRate = Math.round((cancelled * 100.0 / total) * 10.0) / 10.0;
        double paidRate = Math.round((paid * 100.0 / total) * 10.0) / 10.0;

        return new ReservationStatsResponse(
            total,
            arrived,
            confirmed,
            cancelled,
            noShow,
            pending,
            paid,
            completionRate,
            cancellationRate,
            paidRate
        );
    }

    /**
     * Aggregates dish sales performance ranking by quantity sold and revenue (Daily Dish Sales Summary).
     */
    public List<DishSalesItemResponse> getDishSalesPerformance(LocalDateTime start, LocalDateTime end, Integer limit) {
        Timestamp startTs = Timestamp.valueOf(start);
        Timestamp endTs = Timestamp.valueOf(end);

        String limitClause = (limit != null && limit > 0) ? " LIMIT " + limit : "";

        String sql = """
            SELECT 
                ii.dish_id,
                COALESCE(m.item_name, ii.dish_name_snapshot) AS dish_name,
                COALESCE(c.category_name, 'Món khác') AS category_name,
                COALESCE(SUM(ii.quantity), 0) AS total_quantity,
                COALESCE(SUM(ii.total_price), 0) AS total_sales
            FROM invoice_items ii
            JOIN invoices i ON ii.invoice_id = i.id
            LEFT JOIN menu_items m ON ii.dish_id = m.id
            LEFT JOIN categories c ON m.category_id = c.id
            WHERE i.status = 'PAID'
              AND COALESCE(i.paid_at, i.created_at) >= ?
              AND COALESCE(i.paid_at, i.created_at) <= ?
            GROUP BY ii.dish_id, dish_name, category_name
            ORDER BY total_quantity DESC, total_sales DESC
            """ + limitClause;

        List<DishSalesItemRaw> items = jdbcTemplate.query(sql, (rs, rowNum) -> new DishSalesItemRaw(
            rs.getString("dish_id"),
            rs.getString("dish_name"),
            rs.getString("category_name"),
            rs.getLong("total_quantity"),
            rs.getBigDecimal("total_sales")
        ), startTs, endTs);

        if (items.isEmpty()) {
            return Collections.emptyList();
        }

        BigDecimal grandTotal = items.stream()
            .map(item -> item.totalSales)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<DishSalesItemResponse> response = new ArrayList<>();
        for (DishSalesItemRaw item : items) {
            double percentage = 0.0;
            if (grandTotal.compareTo(BigDecimal.ZERO) > 0) {
                percentage = item.totalSales
                    .multiply(BigDecimal.valueOf(100))
                    .divide(grandTotal, 2, RoundingMode.HALF_UP)
                    .doubleValue();
            }
            response.add(new DishSalesItemResponse(
                item.dishId,
                item.dishName,
                item.categoryName,
                item.totalQuantity,
                item.totalSales,
                percentage
            ));
        }

        return response;
    }

    private record RevenueRaw(
        BigDecimal netRevenue,
        BigDecimal grossRevenue,
        BigDecimal voucherDiscount,
        BigDecimal totalDiscount,
        BigDecimal totalTax,
        long totalInvoices
    ) {}

    private record DishSalesItemRaw(
        String dishId,
        String dishName,
        String categoryName,
        long totalQuantity,
        BigDecimal totalSales
    ) {}
}

