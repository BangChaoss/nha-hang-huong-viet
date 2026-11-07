package com.huongViet.order;

import com.huongViet.order.dto.InvoiceItemResponse;
import com.huongViet.order.dto.InvoiceResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class InvoiceService {

    private final JdbcTemplate jdbcTemplate;

    public InvoiceService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<InvoiceResponse> listByDate(LocalDate date) {
        LocalDate queryDate = (date != null) ? date : LocalDate.now();
        Timestamp start = Timestamp.valueOf(queryDate.atStartOfDay());
        Timestamp end = Timestamp.valueOf(queryDate.atTime(LocalTime.MAX));

        String sql = """
            SELECT i.id, i.invoice_code, i.order_id, i.staff_id, i.voucher_code_snapshot,
                   i.voucher_discount_amount, i.subtotal_amount, i.discount_amount,
                   i.tax_amount, i.final_amount, i.loyalty_points_earned, i.status,
                   COALESCE(i.paid_at, i.created_at) AS paid_at,
                   COALESCE(t.table_number, 'Bàn thanh toán') AS table_number
            FROM invoices i
            LEFT JOIN orders o ON i.order_id = o.id
            LEFT JOIN restaurant_tables t ON o.table_id = t.id
            WHERE i.status = 'PAID'
              AND COALESCE(i.paid_at, i.created_at) >= ?
              AND COALESCE(i.paid_at, i.created_at) <= ?
            ORDER BY COALESCE(i.paid_at, i.created_at) DESC
        """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            String invoiceId = rs.getString("id");
            List<InvoiceItemResponse> items = getItemsByInvoiceId(invoiceId);

            Timestamp paidAtTs = rs.getTimestamp("paid_at");
            LocalDateTime paidAt = (paidAtTs != null) ? paidAtTs.toLocalDateTime() : null;

            return new InvoiceResponse(
                invoiceId,
                rs.getString("invoice_code"),
                rs.getString("order_id"),
                rs.getString("table_number"),
                rs.getString("staff_id"),
                rs.getString("voucher_code_snapshot"),
                rs.getBigDecimal("voucher_discount_amount"),
                rs.getBigDecimal("subtotal_amount"),
                rs.getBigDecimal("discount_amount"),
                rs.getBigDecimal("tax_amount"),
                rs.getBigDecimal("final_amount"),
                rs.getInt("loyalty_points_earned"),
                rs.getString("status"),
                paidAt,
                items
            );
        }, start, end);
    }

    public InvoiceResponse getById(String id) {
        String sql = """
            SELECT i.id, i.invoice_code, i.order_id, i.staff_id, i.voucher_code_snapshot,
                   i.voucher_discount_amount, i.subtotal_amount, i.discount_amount,
                   i.tax_amount, i.final_amount, i.loyalty_points_earned, i.status,
                   COALESCE(i.paid_at, i.created_at) AS paid_at,
                   COALESCE(t.table_number, 'Bàn thanh toán') AS table_number
            FROM invoices i
            LEFT JOIN orders o ON i.order_id = o.id
            LEFT JOIN restaurant_tables t ON o.table_id = t.id
            WHERE i.id = ?
        """;

        List<InvoiceResponse> list = jdbcTemplate.query(sql, (rs, rowNum) -> {
            String invoiceId = rs.getString("id");
            List<InvoiceItemResponse> items = getItemsByInvoiceId(invoiceId);

            Timestamp paidAtTs = rs.getTimestamp("paid_at");
            LocalDateTime paidAt = (paidAtTs != null) ? paidAtTs.toLocalDateTime() : null;

            return new InvoiceResponse(
                invoiceId,
                rs.getString("invoice_code"),
                rs.getString("order_id"),
                rs.getString("table_number"),
                rs.getString("staff_id"),
                rs.getString("voucher_code_snapshot"),
                rs.getBigDecimal("voucher_discount_amount"),
                rs.getBigDecimal("subtotal_amount"),
                rs.getBigDecimal("discount_amount"),
                rs.getBigDecimal("tax_amount"),
                rs.getBigDecimal("final_amount"),
                rs.getInt("loyalty_points_earned"),
                rs.getString("status"),
                paidAt,
                items
            );
        }, id);

        if (list.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hóa đơn.");
        }
        return list.get(0);
    }

    private List<InvoiceItemResponse> getItemsByInvoiceId(String invoiceId) {
        String sql = """
            SELECT id, dish_id, dish_name_snapshot, unit_price_snapshot, quantity, total_price
            FROM invoice_items
            WHERE invoice_id = ?
        """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new InvoiceItemResponse(
            rs.getString("id"),
            rs.getString("dish_id"),
            rs.getString("dish_name_snapshot"),
            rs.getBigDecimal("unit_price_snapshot"),
            rs.getInt("quantity"),
            rs.getBigDecimal("total_price")
        ), invoiceId);
    }
}