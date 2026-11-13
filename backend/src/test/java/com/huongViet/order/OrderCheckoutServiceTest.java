package com.huongViet.order;

import com.huongViet.menu.MenuItem;
import com.huongViet.menu.MenuItemRepository;
import com.huongViet.payment.LoyaltyAccount;
import com.huongViet.payment.LoyaltyAccountRepository;
import com.huongViet.payment.LoyaltySettings;
import com.huongViet.payment.LoyaltySettingsRepository;
import com.huongViet.reservation.Reservation;
import com.huongViet.reservation.ReservationRepository;
import com.huongViet.reservation.ReservationStatus;
import com.huongViet.table.RestaurantTable;
import com.huongViet.table.TableRepository;
import com.huongViet.table.TableStatus;
import com.huongViet.table.TableStatusMessage;
import com.huongViet.voucher.VoucherService;
import com.huongViet.voucher.VoucherValidationRequest;
import com.huongViet.voucher.VoucherValidationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kiểm thử thanh toán tại bàn: tạm tính, VAT, voucher, điểm thưởng và đóng bàn.
 */
@ExtendWith(MockitoExtension.class)
class OrderCheckoutServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private KitchenTicketRepository kitchenTicketRepository;
    @Mock
    private OrderItemAuditRepository orderItemAuditRepository;
    @Mock
    private MenuItemRepository menuItemRepository;
    @Mock
    private TableRepository tableRepository;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private LoyaltyAccountRepository loyaltyAccounts;
    @Mock
    private LoyaltySettingsRepository loyaltySettings;
    @Mock
    private VoucherService voucherService;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private OrderService orderService;

    private RestaurantTable occupiedTable;
    private MenuItem pho;
    private MenuItem bunCha;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(
            orderRepository, orderItemRepository, kitchenTicketRepository, orderItemAuditRepository,
            menuItemRepository, tableRepository, messagingTemplate, loyaltyAccounts, loyaltySettings,
            voucherService, jdbcTemplate);

        occupiedTable = new RestaurantTable("table-1", "zone-1", "Bàn 1", 4);
        occupiedTable.setStatus(TableStatus.OCCUPIED);

        pho = new MenuItem("menu-1", "cat-1", "Phở Bò", null,
            new BigDecimal("55000"), null, true);
        bunCha = new MenuItem("menu-2", "cat-1", "Bún Chả", null,
            new BigDecimal("45000"), null, true);

        lenient().when(tableRepository.findById("table-1")).thenReturn(Optional.of(occupiedTable));
        lenient().when(reservationRepository.findActiveReservationsForDate(any())).thenReturn(List.of());
    }

    private Order openOrder(String id) {
        Order order = new Order(id, "table-1", "staff-1", OrderType.DINE_IN);
        order.setTable(occupiedTable);
        return order;
    }

    private OrderItem item(String id, String orderId, MenuItem menu, int quantity, CookingStatus status) {
        OrderItem item = new OrderItem(id, orderId, menu.getId(), quantity,
            menu.getCurrentPrice(), null);
        item.setMenuItem(menu);
        item.setCookingStatus(status);
        return item;
    }

    private LoyaltySettings settings(String earnRate, String pointValue) {
        return new LoyaltySettings("default", new BigDecimal(earnRate), new BigDecimal(pointValue));
    }

    private void stubSingleOrder(LoyaltySettings loyaltySettingsValue) {
        when(tableRepository.findByIdAndDeletedAtIsNull("table-1")).thenReturn(Optional.of(occupiedTable));
        when(orderRepository.findByTableIdOrderByCreatedAtDesc("table-1"))
            .thenReturn(List.of(openOrder("order-1")));
        when(orderItemRepository.findByOrderIdOrderByCreatedAtAsc("order-1"))
            .thenReturn(List.of(item("item-1", "order-1", pho, 2, CookingStatus.QUEUED)));
        when(loyaltySettings.findById("default")).thenReturn(Optional.of(loyaltySettingsValue));
    }

    @Test
    @DisplayName("checkoutCash - bàn không tồn tại trả về 404")
    void checkout_TableNotFound_ThrowsNotFound() {
        when(tableRepository.findByIdAndDeletedAtIsNull("table-1")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> orderService.checkoutCash("table-1", new CashCheckoutRequest(null, null, false)));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    @DisplayName("checkoutCash - bàn chưa có đơn đang mở trả về 409")
    void checkout_NoOpenOrders_ThrowsConflict() {
        when(tableRepository.findByIdAndDeletedAtIsNull("table-1")).thenReturn(Optional.of(occupiedTable));
        // Đơn cũ đã hoàn tất nên không còn đơn nào để thanh toán.
        Order completed = openOrder("order-old");
        completed.setStatus(OrderStatus.COMPLETED);
        when(orderRepository.findByTableIdOrderByCreatedAtDesc("table-1")).thenReturn(List.of(completed));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> orderService.checkoutCash("table-1", new CashCheckoutRequest(null, null, false)));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals("Bàn chưa có món đã gửi bếp để thanh toán.", ex.getReason());
        verify(tableRepository, never()).save(any(RestaurantTable.class));
    }

    @Test
    @DisplayName("checkoutCash - tính tạm tính, VAT 10%, đóng đơn và trả bàn về AVAILABLE")
    void checkout_ComputesSubtotalVatAndClosesTable() {
        stubSingleOrder(settings("5.000", "1"));

        CashCheckoutResponse response = orderService.checkoutCash(
            "table-1", new CashCheckoutRequest(null, null, false));

        assertEquals(0, new BigDecimal("110000").compareTo(response.subtotal()));
        assertEquals(0, new BigDecimal("11000").compareTo(response.vat()));
        assertEquals(0, BigDecimal.ZERO.compareTo(response.discountAmount()));
        assertEquals(0, new BigDecimal("121000").compareTo(response.total()));
        assertEquals(0, BigDecimal.ZERO.compareTo(response.pointsAwarded()));
        assertEquals("Bàn 1", response.tableNumber());

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertEquals(OrderStatus.COMPLETED, orderCaptor.getValue().getStatus());
        assertNotNull(orderCaptor.getValue().getClosedAt());

        verify(tableRepository).save(occupiedTable);
        assertEquals(TableStatus.AVAILABLE, occupiedTable.getStatus());
        verify(messagingTemplate).convertAndSend(eq("/topic/table-status"), any(TableStatusMessage.class));
    }

    @Test
    @DisplayName("checkoutCash - món đã hủy không được tính vào hóa đơn")
    void checkout_CancelledItems_ExcludedFromSubtotal() {
        when(tableRepository.findByIdAndDeletedAtIsNull("table-1")).thenReturn(Optional.of(occupiedTable));
        when(orderRepository.findByTableIdOrderByCreatedAtDesc("table-1"))
            .thenReturn(List.of(openOrder("order-1")));
        when(orderItemRepository.findByOrderIdOrderByCreatedAtAsc("order-1")).thenReturn(List.of(
            item("item-1", "order-1", pho, 2, CookingStatus.QUEUED),
            item("item-2", "order-1", bunCha, 1, CookingStatus.CANCELLED)));
        when(loyaltySettings.findById("default")).thenReturn(Optional.of(settings("5.000", "1")));

        CashCheckoutResponse response = orderService.checkoutCash(
            "table-1", new CashCheckoutRequest(null, null, false));

        assertEquals(0, new BigDecimal("110000").compareTo(response.subtotal()));
        assertEquals(0, new BigDecimal("11000").compareTo(response.vat()));
    }

    @Test
    @DisplayName("checkoutCash - số điện thoại hợp lệ được tích điểm theo % hóa đơn")
    void checkout_WithPhone_AwardsLoyaltyPoints() {
        stubSingleOrder(settings("10.000", "1000"));
        LoyaltyAccount account = new LoyaltyAccount("loyalty-1", "0901234567");
        when(loyaltyAccounts.lockByCustomerPhone("0901234567")).thenReturn(Optional.of(account));

        CashCheckoutResponse response = orderService.checkoutCash(
            "table-1", new CashCheckoutRequest("0901234567", null, false));

        assertEquals(0, new BigDecimal("12100").compareTo(response.pointsAwarded()));
        assertEquals(0, new BigDecimal("12100").compareTo(account.getPointsBalance()));
        verify(loyaltyAccounts).save(account);
    }

    @Test
    @DisplayName("checkoutCash - khách chưa có tài khoản tích điểm thì tạo mới")
    void checkout_NewPhone_CreatesLoyaltyAccount() {
        stubSingleOrder(settings("5.000", "1"));
        when(loyaltyAccounts.lockByCustomerPhone("0901234567")).thenReturn(Optional.empty());

        CashCheckoutResponse response = orderService.checkoutCash(
            "table-1", new CashCheckoutRequest("0901234567", null, false));

        ArgumentCaptor<LoyaltyAccount> captor = ArgumentCaptor.forClass(LoyaltyAccount.class);
        verify(loyaltyAccounts).save(captor.capture());
        assertEquals("0901234567", captor.getValue().getCustomerPhone());
        assertEquals(0, new BigDecimal("6050").compareTo(captor.getValue().getPointsBalance()));
        assertEquals(0, new BigDecimal("6050").compareTo(response.pointsAwarded()));
    }

    @Test
    @DisplayName("checkoutCash - dùng điểm nhưng thiếu số điện thoại trả về 400")
    void checkout_UsePointsWithoutPhone_ThrowsBadRequest() {
        stubSingleOrder(settings("5.000", "1000"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> orderService.checkoutCash("table-1", new CashCheckoutRequest(null, null, true)));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Nhập số điện thoại để sử dụng điểm.", ex.getReason());
    }

    @Test
    @DisplayName("checkoutCash - số điện thoại sai định dạng trả về 400")
    void checkout_InvalidPhone_ThrowsBadRequest() {
        stubSingleOrder(settings("5.000", "1000"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> orderService.checkoutCash("table-1", new CashCheckoutRequest("abc-xyz", null, false)));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Số điện thoại không hợp lệ.", ex.getReason());
        verify(loyaltyAccounts, never()).lockByCustomerPhone(anyString());
    }

    @Test
    @DisplayName("checkoutCash - điểm quy đổi bị giới hạn bởi số dư và giá trị hóa đơn")
    void checkout_PointsRedeemedCappedByBalanceAndBill() {
        stubSingleOrder(settings("5.000", "1000"));
        LoyaltyAccount account = new LoyaltyAccount("loyalty-1", "0901234567");
        account.addPoints(new BigDecimal("5000"));
        when(loyaltyAccounts.lockByCustomerPhone("0901234567")).thenReturn(Optional.of(account));

        CashCheckoutResponse response = orderService.checkoutCash(
            "table-1", new CashCheckoutRequest("0901234567", null, true));

        // 121.000đ / 1.000đ mỗi điểm = tối đa 121 điểm, ít hơn số dư 5.000 điểm.
        assertEquals(0, new BigDecimal("121").compareTo(response.pointsRedeemed()));
        assertEquals(0, BigDecimal.ZERO.compareTo(response.total()));
        assertEquals(0, new BigDecimal("4879").compareTo(account.getPointsBalance()));
    }

    @Test
    @DisplayName("checkoutCash - khách chọn đúng số điểm muốn dùng")
    void checkout_PointsToUse_IsRespected() {
        stubSingleOrder(settings("5.000", "1000"));
        LoyaltyAccount account = new LoyaltyAccount("loyalty-1", "0901234567");
        account.addPoints(new BigDecimal("500"));
        when(loyaltyAccounts.lockByCustomerPhone("0901234567")).thenReturn(Optional.of(account));

        CashCheckoutResponse response = orderService.checkoutCash(
            "table-1", new CashCheckoutRequest("0901234567", null, true, new BigDecimal("50")));

        assertEquals(0, new BigDecimal("50").compareTo(response.pointsRedeemed()));
        assertEquals(0, new BigDecimal("71000").compareTo(response.total()));
        // 450 điểm còn lại + 3.550 điểm thưởng của hóa đơn 71.000đ.
        assertEquals(0, new BigDecimal("4000").compareTo(account.getPointsBalance()));
    }

    @Test
    @DisplayName("checkoutCash - dùng voucher nhưng thiếu số điện thoại trả về 400")
    void checkout_VoucherWithoutPhone_ThrowsBadRequest() {
        when(tableRepository.findByIdAndDeletedAtIsNull("table-1")).thenReturn(Optional.of(occupiedTable));
        when(orderRepository.findByTableIdOrderByCreatedAtDesc("table-1"))
            .thenReturn(List.of(openOrder("order-1")));
        when(orderItemRepository.findByOrderIdOrderByCreatedAtAsc("order-1"))
            .thenReturn(List.of(item("item-1", "order-1", pho, 2, CookingStatus.QUEUED)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> orderService.checkoutCash("table-1", new CashCheckoutRequest(null, "GIAM10", false)));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Nhập số điện thoại để sử dụng voucher.", ex.getReason());
        verify(voucherService, never()).validate(any());
    }

    @Test
    @DisplayName("checkoutCash - voucher không hợp lệ trả về 400 kèm lý do từ VoucherService")
    void checkout_InvalidVoucher_ThrowsBadRequest() {
        when(tableRepository.findByIdAndDeletedAtIsNull("table-1")).thenReturn(Optional.of(occupiedTable));
        when(orderRepository.findByTableIdOrderByCreatedAtDesc("table-1"))
            .thenReturn(List.of(openOrder("order-1")));
        when(orderItemRepository.findByOrderIdOrderByCreatedAtAsc("order-1"))
            .thenReturn(List.of(item("item-1", "order-1", pho, 2, CookingStatus.QUEUED)));
        when(voucherService.validate(any(VoucherValidationRequest.class)))
            .thenReturn(new VoucherValidationResponse(false, "Voucher đã hết hạn.", BigDecimal.ZERO, "GIAM10"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> orderService.checkoutCash("table-1", new CashCheckoutRequest("0901234567", "GIAM10", false)));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Voucher đã hết hạn.", ex.getReason());
        verify(tableRepository, never()).save(any(RestaurantTable.class));
    }

    @Test
    @DisplayName("checkoutCash - voucher hợp lệ được trừ vào hóa đơn và ghi nhận sử dụng")
    void checkout_ValidVoucher_AppliesDiscountAndRedeems() {
        stubSingleOrder(settings("5.000", "1"));
        when(loyaltyAccounts.lockByCustomerPhone("0901234567")).thenReturn(Optional.empty());
        when(voucherService.validate(any(VoucherValidationRequest.class)))
            .thenReturn(new VoucherValidationResponse(true, "OK", new BigDecimal("10000"), "GIAM10"));
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<String>>any(), eq("GIAM10")))
            .thenReturn(List.of("voucher-1"));
        when(voucherService.redeemForInvoice(any(VoucherValidationRequest.class), anyString()))
            .thenReturn(new VoucherValidationResponse(true, "OK", new BigDecimal("10000"), "GIAM10"));

        CashCheckoutResponse response = orderService.checkoutCash(
            "table-1", new CashCheckoutRequest("0901234567", "giam10", false));

        assertEquals(0, new BigDecimal("10000").compareTo(response.discountAmount()));
        assertEquals(0, new BigDecimal("111000").compareTo(response.total()));
        verify(voucherService).redeemForInvoice(any(VoucherValidationRequest.class), anyString());
    }

    @Test
    @DisplayName("checkoutCash - voucher hợp lệ nhưng ghi nhận sử dụng thất bại trả về 409")
    void checkout_VoucherRedeemConflict_ThrowsConflict() {
        stubSingleOrder(settings("5.000", "1"));
        when(loyaltyAccounts.lockByCustomerPhone("0901234567")).thenReturn(Optional.empty());
        when(voucherService.validate(any(VoucherValidationRequest.class)))
            .thenReturn(new VoucherValidationResponse(true, "OK", new BigDecimal("10000"), "GIAM10"));
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<String>>any(), eq("GIAM10")))
            .thenReturn(List.of("voucher-1"));
        when(voucherService.redeemForInvoice(any(VoucherValidationRequest.class), anyString()))
            .thenReturn(new VoucherValidationResponse(false, "Voucher đã được sử dụng.", BigDecimal.ZERO, "GIAM10"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> orderService.checkoutCash("table-1",
                new CashCheckoutRequest("0901234567", "GIAM10", false)));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals("Voucher đã được sử dụng.", ex.getReason());
        verify(loyaltyAccounts, never()).save(any(LoyaltyAccount.class));
    }

    @Test
    @DisplayName("checkoutCash - chuẩn hóa số điện thoại có khoảng trắng, gạch nối, ngoặc")
    void checkout_NormalizesPhoneNumber() {
        stubSingleOrder(settings("5.000", "1"));
        when(loyaltyAccounts.lockByCustomerPhone("0901234567")).thenReturn(Optional.empty());

        orderService.checkoutCash("table-1",
            new CashCheckoutRequest(" (0901) 234-567 ", null, false));

        verify(loyaltyAccounts).lockByCustomerPhone("0901234567");
    }

    @Test
    @DisplayName("checkoutCash - chưa có cấu hình tích điểm thì tạo giá trị mặc định")
    void checkout_MissingLoyaltySettings_CreatesDefault() {
        when(tableRepository.findByIdAndDeletedAtIsNull("table-1")).thenReturn(Optional.of(occupiedTable));
        when(orderRepository.findByTableIdOrderByCreatedAtDesc("table-1"))
            .thenReturn(List.of(openOrder("order-1")));
        when(orderItemRepository.findByOrderIdOrderByCreatedAtAsc("order-1"))
            .thenReturn(List.of(item("item-1", "order-1", pho, 2, CookingStatus.QUEUED)));
        when(loyaltySettings.findById("default")).thenReturn(Optional.empty());
        when(loyaltySettings.save(any(LoyaltySettings.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        CashCheckoutResponse response = orderService.checkoutCash(
            "table-1", new CashCheckoutRequest(null, null, false));

        assertEquals(0, new BigDecimal("121000").compareTo(response.total()));
        verify(loyaltySettings).save(any(LoyaltySettings.class));
    }

    @Test
    @DisplayName("checkoutCash - lỗi WebSocket trạng thái bàn không chặn thanh toán")
    void checkout_WebSocketFailure_DoesNotBreakCheckout() {
        stubSingleOrder(settings("5.000", "1"));
        doThrow(new RuntimeException("broker down"))
            .when(messagingTemplate).convertAndSend(eq("/topic/table-status"), any(TableStatusMessage.class));

        CashCheckoutResponse response = orderService.checkoutCash(
            "table-1", new CashCheckoutRequest(null, null, false));

        assertEquals(0, new BigDecimal("121000").compareTo(response.total()));
        assertEquals(TableStatus.AVAILABLE, occupiedTable.getStatus());
    }

    @Test
    @DisplayName("checkoutCash - nhiều đơn đang mở của cùng bàn đều được đóng và cộng vào hóa đơn")
    void checkout_MultipleOpenOrders_ClosesAll() {
        when(tableRepository.findByIdAndDeletedAtIsNull("table-1")).thenReturn(Optional.of(occupiedTable));
        when(orderRepository.findByTableIdOrderByCreatedAtDesc("table-1"))
            .thenReturn(List.of(openOrder("order-1"), openOrder("order-2")));
        when(orderItemRepository.findByOrderIdOrderByCreatedAtAsc("order-1"))
            .thenReturn(List.of(item("item-1", "order-1", pho, 2, CookingStatus.QUEUED)));
        when(orderItemRepository.findByOrderIdOrderByCreatedAtAsc("order-2"))
            .thenReturn(List.of(item("item-2", "order-2", bunCha, 1, CookingStatus.SERVED)));
        when(loyaltySettings.findById("default")).thenReturn(Optional.of(settings("5.000", "1")));

        CashCheckoutResponse response = orderService.checkoutCash(
            "table-1", new CashCheckoutRequest(null, null, false));

        assertEquals(0, new BigDecimal("155000").compareTo(response.subtotal()));
        verify(orderRepository, times(2)).save(any(Order.class));
    }

    @Test
    @DisplayName("checkoutCash - có staffId thì lưu thông tin nhân viên vào hóa đơn")
    void checkout_WithStaffId_RecordsStaffInInvoice() {
        stubSingleOrder(settings("5.000", "1"));

        CashCheckoutResponse response = orderService.checkoutCash(
            "table-1", new CashCheckoutRequest(null, null, false), "staff-custom");

        assertEquals(0, new BigDecimal("121000").compareTo(response.total()));
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, org.mockito.Mockito.atLeastOnce()).update(sqlCaptor.capture(), any(Object[].class));
    }

    @Test
    @DisplayName("checkoutCash - bàn có lịch đặt ARRIVED thì chuyển sang PAID khi thanh toán")
    void checkout_ActiveReservation_TransitionsToPaid() {
        stubSingleOrder(settings("5.000", "1"));

        Reservation reservation = new Reservation(
            "res-1", null, "Khách A", "0901234567", null, 4,
            java.time.LocalDate.now(), java.time.LocalTime.of(12, 0), null
        );
        reservation.setTableId("table-1");
        reservation.setStatus(ReservationStatus.ARRIVED);

        when(reservationRepository.findActiveReservationsForDate(any())).thenReturn(List.of(reservation));

        CashCheckoutResponse response = orderService.checkoutCash(
            "table-1", new CashCheckoutRequest(null, null, false));

        assertEquals(0, new BigDecimal("121000").compareTo(response.total()));
        assertEquals(ReservationStatus.PAID, reservation.getStatus());
        verify(reservationRepository).save(reservation);
    }

    @Test
    @DisplayName("checkoutCash - kết hợp cả voucher giảm giá và điểm thưởng trên cùng hóa đơn")
    void checkout_CombinedVoucherAndLoyaltyPoints() {
        stubSingleOrder(settings("5.000", "1000"));

        LoyaltyAccount account = new LoyaltyAccount("loyalty-1", "0901234567");
        account.addPoints(new BigDecimal("500"));
        when(loyaltyAccounts.lockByCustomerPhone("0901234567")).thenReturn(Optional.of(account));

        // Subtotal = 110.000đ, VAT 10% = 11.000đ -> Tổng trước giảm = 121.000đ
        // Voucher giảm 20.000đ -> Sau voucher còn 101.000đ
        // Dùng 30 điểm (30 * 1.000đ = 30.000đ) -> Còn 71.000đ
        when(voucherService.validate(any(VoucherValidationRequest.class)))
            .thenReturn(new VoucherValidationResponse(true, "OK", new BigDecimal("20000"), "COMBO20"));
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<String>>any(), eq("COMBO20")))
            .thenReturn(List.of("voucher-combo"));
        when(voucherService.redeemForInvoice(any(VoucherValidationRequest.class), anyString()))
            .thenReturn(new VoucherValidationResponse(true, "OK", new BigDecimal("20000"), "COMBO20"));

        CashCheckoutResponse response = orderService.checkoutCash(
            "table-1", new CashCheckoutRequest("0901234567", "COMBO20", true, new BigDecimal("30")));

        assertEquals(0, new BigDecimal("20000").compareTo(response.discountAmount()));
        assertEquals(0, new BigDecimal("30").compareTo(response.pointsRedeemed()));
        assertEquals(0, new BigDecimal("71000").compareTo(response.total()));
        verify(voucherService).redeemForInvoice(any(VoucherValidationRequest.class), anyString());
        verify(loyaltyAccounts).save(account);
    }

    @Test
    @DisplayName("checkoutCash - ghi nhận chèn hóa đơn và chi tiết món vào database")
    void checkout_InsertsInvoiceAndInvoiceItems() {
        stubSingleOrder(settings("5.000", "1"));

        orderService.checkoutCash("table-1", new CashCheckoutRequest(null, null, false));

        // Kiểm tra jdbcTemplate.update được gọi cho cả invoices và invoice_items
        verify(jdbcTemplate, times(2)).update(anyString(), any(Object[].class));
    }
}
