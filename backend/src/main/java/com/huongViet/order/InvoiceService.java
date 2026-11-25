package com.huongViet.order;

import com.huongViet.order.dto.InvoiceItemResponse;
import com.huongViet.order.dto.InvoiceResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class InvoiceService {

    private final JdbcTemplate jdbcTemplate;

    public InvoiceService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<InvoiceResponse> listByDate(LocalDate date) {
        LocalDate queryDate = (date != null) ? date : LocalDate.now();
        // Dùng nửa khoảng [start, end) để tránh lỗi làm tròn microsecond của Timestamp sang ngày hôm sau
        Timestamp start = Timestamp.valueOf(queryDate.atStartOfDay());
        Timestamp end = Timestamp.valueOf(queryDate.plusDays(1).atStartOfDay());

        String sql = """
            SELECT i.id, i.invoice_code, i.order_id, i.staff_id,
                   COALESCE(i.customer_phone, u.phone_number, '') AS customer_phone,
                   COALESCE(i.loyalty_phone, '') AS loyalty_phone,
                   i.voucher_code_snapshot, i.voucher_discount_amount, i.subtotal_amount, i.discount_amount,
                   i.tax_amount, i.final_amount,
                   COALESCE(i.vip_fee_amount, 0) AS vip_fee_amount,
                   COALESCE(i.vip_deposit_paid, 0) AS vip_deposit_paid,
                   i.loyalty_points_earned, i.status,
                   COALESCE(i.paid_at, i.created_at) AS paid_at,
                   COALESCE(t.table_number, 'Bàn thanh toán') AS table_number
            FROM invoices i
            LEFT JOIN orders o ON i.order_id = o.id
            LEFT JOIN restaurant_tables t ON o.table_id = t.id
            LEFT JOIN users u ON i.customer_id = u.id
            WHERE i.status = 'PAID'
              AND COALESCE(i.paid_at, i.created_at) >= ?
              AND COALESCE(i.paid_at, i.created_at) < ?
            ORDER BY COALESCE(i.paid_at, i.created_at) DESC
        """;

        List<InvoiceDraft> drafts = queryDrafts(sql, start, end);

        if (drafts.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> invoiceIds = drafts.stream().map(InvoiceDraft::id).toList();
        Map<String, List<InvoiceItemResponse>> itemsMap = loadItemsForInvoices(invoiceIds);

        return drafts.stream().map(d -> toInvoiceResponse(d, itemsMap.getOrDefault(d.id(), Collections.emptyList()))).toList();
    }

    public InvoiceResponse getById(String id) {
        String sql = """
            SELECT i.id, i.invoice_code, i.order_id, i.staff_id,
                   COALESCE(i.customer_phone, u.phone_number, '') AS customer_phone,
                   COALESCE(i.loyalty_phone, '') AS loyalty_phone,
                   i.voucher_code_snapshot, i.voucher_discount_amount, i.subtotal_amount, i.discount_amount,
                   i.tax_amount, i.final_amount,
                   COALESCE(i.vip_fee_amount, 0) AS vip_fee_amount,
                   COALESCE(i.vip_deposit_paid, 0) AS vip_deposit_paid,
                   i.loyalty_points_earned, i.status,
                   COALESCE(i.paid_at, i.created_at) AS paid_at,
                   COALESCE(t.table_number, 'Bàn thanh toán') AS table_number
            FROM invoices i
            LEFT JOIN orders o ON i.order_id = o.id
            LEFT JOIN restaurant_tables t ON o.table_id = t.id
            LEFT JOIN users u ON i.customer_id = u.id
            WHERE i.id = ?
        """;

        List<InvoiceDraft> list = queryDrafts(sql, id);

        if (list.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hóa đơn.");
        }

        InvoiceDraft d = list.get(0);
        List<InvoiceItemResponse> items = getItemsByInvoiceId(d.id());

        return toInvoiceResponse(d, items);
    }

    private List<InvoiceDraft> queryDrafts(String sql, Object... args) {
        try {
            return jdbcTemplate.query(sql, (rs, rowNum) -> mapDraft(rs, true), args);
        } catch (Exception ex) {
            String legacySql = sql
                .replace("COALESCE(i.vip_fee_amount, 0) AS vip_fee_amount,", "")
                .replace("COALESCE(i.vip_deposit_paid, 0) AS vip_deposit_paid,", "");
            return jdbcTemplate.query(legacySql, (rs, rowNum) -> mapDraft(rs, false), args);
        }
    }

    private InvoiceDraft mapDraft(java.sql.ResultSet rs, boolean includeVipColumns) throws java.sql.SQLException {
        Timestamp paidAtTs = rs.getTimestamp("paid_at");
        LocalDateTime paidAt = (paidAtTs != null) ? paidAtTs.toLocalDateTime() : null;
        BigDecimal vipFee = includeVipColumns ? rs.getBigDecimal("vip_fee_amount") : BigDecimal.ZERO;
        BigDecimal vipDeposit = includeVipColumns ? rs.getBigDecimal("vip_deposit_paid") : BigDecimal.ZERO;

        return new InvoiceDraft(
            rs.getString("id"),
            rs.getString("invoice_code"),
            rs.getString("order_id"),
            rs.getString("table_number"),
            rs.getString("staff_id"),
            rs.getString("customer_phone"),
            rs.getString("loyalty_phone"),
            rs.getString("voucher_code_snapshot"),
            rs.getBigDecimal("voucher_discount_amount"),
            rs.getBigDecimal("subtotal_amount"),
            rs.getBigDecimal("discount_amount"),
            rs.getBigDecimal("tax_amount"),
            rs.getBigDecimal("final_amount"),
            vipFee != null ? vipFee : BigDecimal.ZERO,
            vipDeposit != null ? vipDeposit : BigDecimal.ZERO,
            rs.getInt("loyalty_points_earned"),
            rs.getString("status"),
            paidAt
        );
    }

    private InvoiceResponse toInvoiceResponse(InvoiceDraft d, List<InvoiceItemResponse> items) {
        return new InvoiceResponse(
            d.id(),
            d.invoiceCode(),
            d.orderId(),
            d.tableNumber(),
            d.staffId(),
            d.customerPhone(),
            d.loyaltyPhone(),
            d.voucherCode(),
            d.voucherDiscountAmount(),
            d.subtotalAmount(),
            d.discountAmount(),
            d.taxAmount(),
            d.finalAmount(),
            d.loyaltyPointsEarned(),
            d.status(),
            d.paidAt(),
            items,
            d.vipFeeAmount(),
            d.vipDepositPaid()
        );
    }

    private Map<String, List<InvoiceItemResponse>> loadItemsForInvoices(List<String> invoiceIds) {
        if (invoiceIds == null || invoiceIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, List<InvoiceItemResponse>> map = new HashMap<>();
        String inSql = String.join(",", Collections.nCopies(invoiceIds.size(), "?"));
        String sql = "SELECT id, invoice_id, dish_id, dish_name_snapshot, unit_price_snapshot, quantity, total_price " +
                     "FROM invoice_items WHERE invoice_id IN (" + inSql + ")";

        jdbcTemplate.query(sql, rs -> {
            String invId = rs.getString("invoice_id");
            map.computeIfAbsent(invId, k -> new ArrayList<>()).add(new InvoiceItemResponse(
                rs.getString("id"),
                rs.getString("dish_id"),
                rs.getString("dish_name_snapshot"),
                rs.getBigDecimal("unit_price_snapshot"),
                rs.getInt("quantity"),
                rs.getBigDecimal("total_price")
            ));
        }, invoiceIds.toArray());

        return map;
    }

    private List<InvoiceItemResponse> getItemsByInvoiceId(String invoiceId) {
        String sql = "SELECT id, dish_id, dish_name_snapshot, unit_price_snapshot, quantity, total_price " +
                     "FROM invoice_items WHERE invoice_id = ?";
        return jdbcTemplate.query(sql, (rs, rowNum) -> new InvoiceItemResponse(
            rs.getString("id"),
            rs.getString("dish_id"),
            rs.getString("dish_name_snapshot"),
            rs.getBigDecimal("unit_price_snapshot"),
            rs.getInt("quantity"),
            rs.getBigDecimal("total_price")
        ), invoiceId);
    }

    private record InvoiceDraft(
        String id,
        String invoiceCode,
        String orderId,
        String tableNumber,
        String staffId,
        String customerPhone,
        String loyaltyPhone,
        String voucherCode,
        BigDecimal voucherDiscountAmount,
        BigDecimal subtotalAmount,
        BigDecimal discountAmount,
        BigDecimal taxAmount,
        BigDecimal finalAmount,
        BigDecimal vipFeeAmount,
        BigDecimal vipDepositPaid,
        int loyaltyPointsEarned,
        String status,
        LocalDateTime paidAt
    ) {}
}