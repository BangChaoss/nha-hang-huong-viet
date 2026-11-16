package com.huongViet.order;

import com.huongViet.order.dto.InvoiceItemResponse;
import com.huongViet.order.dto.InvoiceResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private InvoiceService invoiceService;

    @BeforeEach
    void setUp() {
        invoiceService = new InvoiceService(jdbcTemplate);
    }

    @Test
    @DisplayName("listByDate - truyền ngày cụ thể thì lọc hóa đơn trong ngày đó")
    void listByDate_WithSpecificDate_ReturnsInvoices() throws SQLException {
        LocalDate queryDate = LocalDate.of(2026, 11, 13);
        Timestamp paidAtTs = Timestamp.valueOf(LocalDateTime.of(2026, 11, 13, 12, 30, 0));

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Timestamp.class), any(Timestamp.class)))
                .thenAnswer(inv -> {
                    RowMapper<?> mapper = inv.getArgument(1);
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getString("id")).thenReturn("inv-1");
                    when(rs.getString("invoice_code")).thenReturn("HV-20261113-001");
                    when(rs.getString("order_id")).thenReturn("order-1");
                    when(rs.getString("table_number")).thenReturn("Bàn 01");
                    when(rs.getString("staff_id")).thenReturn("staff-1");
                    when(rs.getString("customer_phone")).thenReturn("0901234567");
                    when(rs.getString("voucher_code_snapshot")).thenReturn("GIAM10");
                    when(rs.getBigDecimal("voucher_discount_amount")).thenReturn(new BigDecimal("10000"));
                    when(rs.getBigDecimal("subtotal_amount")).thenReturn(new BigDecimal("100000"));
                    when(rs.getBigDecimal("discount_amount")).thenReturn(new BigDecimal("10000"));
                    when(rs.getBigDecimal("tax_amount")).thenReturn(new BigDecimal("10000"));
                    when(rs.getBigDecimal("final_amount")).thenReturn(new BigDecimal("100000"));
                    when(rs.getInt("loyalty_points_earned")).thenReturn(500);
                    when(rs.getString("status")).thenReturn("PAID");
                    when(rs.getTimestamp("paid_at")).thenReturn(paidAtTs);

                    return List.of(mapper.mapRow(rs, 1));
                });

        doAnswer(inv -> {
            RowCallbackHandler rch = inv.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("invoice_id")).thenReturn("inv-1");
            when(rs.getString("id")).thenReturn("item-1");
            when(rs.getString("dish_id")).thenReturn("dish-1");
            when(rs.getString("dish_name_snapshot")).thenReturn("Phở Bò");
            when(rs.getBigDecimal("unit_price_snapshot")).thenReturn(new BigDecimal("50000"));
            when(rs.getInt("quantity")).thenReturn(2);
            when(rs.getBigDecimal("total_price")).thenReturn(new BigDecimal("100000"));

            rch.processRow(rs);
            return null;
        }).when(jdbcTemplate).query(anyString(), any(RowCallbackHandler.class), any(Object[].class));

        List<InvoiceResponse> result = invoiceService.listByDate(queryDate);

        assertNotNull(result);
        assertEquals(1, result.size());
        InvoiceResponse inv = result.get(0);
        assertEquals("inv-1", inv.id());
        assertEquals("HV-20261113-001", inv.invoiceCode());
        assertEquals("Bàn 01", inv.tableNumber());
        assertEquals("0901234567", inv.customerPhone());
        assertEquals("GIAM10", inv.voucherCode());
        assertEquals(1, inv.items().size());
        assertEquals("Phở Bò", inv.items().get(0).dishName());
    }

    @Test
    @DisplayName("listByDate - date là null thì mặc định dùng ngày hiện tại")
    void listByDate_NullDate_DefaultsToToday() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Timestamp.class), any(Timestamp.class)))
                .thenReturn(List.of());

        List<InvoiceResponse> result = invoiceService.listByDate(null);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(jdbcTemplate).query(anyString(), any(RowMapper.class), any(Timestamp.class), any(Timestamp.class));
    }

    @Test
    @DisplayName("listByDate - không có hóa đơn thì trả về danh sách rỗng")
    void listByDate_NoInvoices_ReturnsEmptyList() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Timestamp.class), any(Timestamp.class)))
                .thenReturn(List.of());

        List<InvoiceResponse> result = invoiceService.listByDate(LocalDate.now());

        assertTrue(result.isEmpty());
        verify(jdbcTemplate, never()).query(anyString(), any(RowCallbackHandler.class), any(Object[].class));
    }

    @Test
    @DisplayName("getById - tìm thấy hóa đơn thì trả về chi tiết kèm danh sách món")
    void getById_Found_ReturnsInvoiceWithItems() throws SQLException {
        Timestamp paidAtTs = Timestamp.valueOf(LocalDateTime.of(2026, 11, 13, 14, 0, 0));

        when(jdbcTemplate.query(argThat(sql -> sql != null && sql.contains("FROM invoices")), any(RowMapper.class), eq("inv-123")))
                .thenAnswer(inv -> {
                    RowMapper<?> mapper = inv.getArgument(1);
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getString("id")).thenReturn("inv-123");
                    when(rs.getString("invoice_code")).thenReturn("HV-20261113-123");
                    when(rs.getString("order_id")).thenReturn("order-123");
                    when(rs.getString("table_number")).thenReturn("Bàn 05");
                    when(rs.getString("staff_id")).thenReturn("staff-1");
                    when(rs.getString("customer_phone")).thenReturn("0912345678");
                    when(rs.getString("voucher_code_snapshot")).thenReturn(null);
                    when(rs.getBigDecimal("voucher_discount_amount")).thenReturn(BigDecimal.ZERO);
                    when(rs.getBigDecimal("subtotal_amount")).thenReturn(new BigDecimal("150000"));
                    when(rs.getBigDecimal("discount_amount")).thenReturn(BigDecimal.ZERO);
                    when(rs.getBigDecimal("tax_amount")).thenReturn(new BigDecimal("15000"));
                    when(rs.getBigDecimal("final_amount")).thenReturn(new BigDecimal("165000"));
                    when(rs.getInt("loyalty_points_earned")).thenReturn(825);
                    when(rs.getString("status")).thenReturn("PAID");
                    when(rs.getTimestamp("paid_at")).thenReturn(paidAtTs);

                    return List.of(mapper.mapRow(rs, 1));
                });

        InvoiceItemResponse item1 = new InvoiceItemResponse("item-1", "dish-1", "Bún Chả", new BigDecimal("75000"), 2, new BigDecimal("150000"));
        when(jdbcTemplate.query(argThat(sql -> sql != null && sql.contains("FROM invoice_items")), any(RowMapper.class), eq("inv-123")))
                .thenReturn(List.of(item1));

        InvoiceResponse response = invoiceService.getById("inv-123");

        assertNotNull(response);
        assertEquals("inv-123", response.id());
        assertEquals("HV-20261113-123", response.invoiceCode());
        assertEquals("Bàn 05", response.tableNumber());
        assertEquals(0, new BigDecimal("165000").compareTo(response.finalAmount()));
        assertEquals(1, response.items().size());
        assertEquals("Bún Chả", response.items().get(0).dishName());
    }

    @Test
    @DisplayName("getById - không tìm thấy hóa đơn thì ném ngoại lệ 404 NOT_FOUND")
    void getById_NotFound_ThrowsNotFound() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("inv-nonexist")))
                .thenReturn(List.of());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> invoiceService.getById("inv-nonexist"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("Không tìm thấy hóa đơn.", ex.getReason());
    }
}
