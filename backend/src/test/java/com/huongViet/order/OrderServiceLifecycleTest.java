package com.huongViet.order;

import com.huongViet.menu.MenuItem;
import com.huongViet.menu.MenuItemRepository;
import com.huongViet.table.RestaurantTable;
import com.huongViet.table.TableRepository;
import com.huongViet.table.TableStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kiểm thử vòng đời đơn gọi món tại bàn: tạo đơn, thêm/sửa/hủy món,
 * phiếu bếp và nhật ký thay đổi (UC-15, UC-17).
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceLifecycleTest {

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
    private com.huongViet.payment.LoyaltyAccountRepository loyaltyAccounts;
    @Mock
    private com.huongViet.payment.LoyaltySettingsRepository loyaltySettings;
    @Mock
    private com.huongViet.voucher.VoucherService voucherService;
    @Mock
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    @Mock
    private com.huongViet.reservation.ReservationRepository reservationRepository;

    private OrderService orderService;

    private RestaurantTable occupiedTable;
    private MenuItem pho;
    private MenuItem bunCha;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(
            orderRepository, orderItemRepository, kitchenTicketRepository, orderItemAuditRepository,
            menuItemRepository, tableRepository, messagingTemplate, loyaltyAccounts, loyaltySettings,
            voucherService, jdbcTemplate, reservationRepository);

        occupiedTable = new RestaurantTable("table-1", "zone-1", "Bàn 1", 4);
        occupiedTable.setStatus(TableStatus.OCCUPIED);

        pho = new MenuItem("menu-1", "cat-1", "Phở Bò", "Phở bò đặc biệt",
            new BigDecimal("55000"), null, true);
        bunCha = new MenuItem("menu-2", "cat-1", "Bún Chả", "Bún chả Hà Nội",
            new BigDecimal("45000"), null, true);

        org.mockito.Mockito.lenient().when(tableRepository.findById("table-1")).thenReturn(Optional.of(occupiedTable));
    }

    private Order openOrder() {
        Order order = new Order("order-1", "table-1", "staff-1", OrderType.DINE_IN);
        order.setTable(occupiedTable);
        return order;
    }

    private OrderItem queuedItem() {
        OrderItem item = new OrderItem("item-1", "order-1", "menu-1", 2,
            new BigDecimal("55000"), "Ít cay");
        item.setMenuItem(pho);
        return item;
    }

    private void stubCreateHappyPath() {
        when(tableRepository.findByIdAndDeletedAtIsNull("table-1")).thenReturn(Optional.of(occupiedTable));
        when(menuItemRepository.findByIdAndDeletedAtIsNull("menu-1")).thenReturn(Optional.of(pho));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        OrderItem item1 = new OrderItem("item-1", "order-1", "menu-1", 2, new BigDecimal("55000"), "Ít cay");
        item1.setMenuItem(pho);
        OrderItem item2 = new OrderItem("item-2", "order-1", "menu-2", 1, new BigDecimal("45000"), null);
        item2.setMenuItem(bunCha);
        when(orderItemRepository.findByOrderIdOrderByCreatedAtAsc(anyString()))
            .thenReturn(List.of(item1, item2));
        when(kitchenTicketRepository.save(any(KitchenTicket.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("create - mỗi món trong đơn sinh một phiếu bếp riêng, đủ thông tin bàn và ghi chú")
    void create_MultipleItems_CreatesOneTicketPerDish() {
        stubCreateHappyPath();
        when(menuItemRepository.findByIdAndDeletedAtIsNull("menu-2")).thenReturn(Optional.of(bunCha));

        CreateOrderRequest request = new CreateOrderRequest(
            "table-1", OrderType.DINE_IN,
            List.of(new OrderItemRequest("menu-1", 2, "Ít cay"),
                new OrderItemRequest("menu-2", 1, null)),
            true);

        OrderResponse response = orderService.create(request, "staff-1");

        assertEquals(2, response.items().size());
        assertEquals(0, new BigDecimal("155000").compareTo(response.subtotal()));

        ArgumentCaptor<KitchenTicket> ticketCaptor = ArgumentCaptor.forClass(KitchenTicket.class);
        verify(kitchenTicketRepository, times(2)).save(ticketCaptor.capture());
        List<KitchenTicket> tickets = ticketCaptor.getAllValues();
        assertEquals("Phở Bò", tickets.get(0).getItemName());
        assertEquals(2, tickets.get(0).getQuantity());
        assertEquals("Ít cay", tickets.get(0).getNote());
        assertEquals("Bàn 1", tickets.get(0).getTableNumber());
        assertEquals(1, tickets.get(0).getPriority());
        assertEquals(CookingStatus.QUEUED, tickets.get(0).getStatus());
        assertEquals("Bún Chả", tickets.get(1).getItemName());

        ArgumentCaptor<KitchenTicketEvent> eventCaptor = ArgumentCaptor.forClass(KitchenTicketEvent.class);
        verify(messagingTemplate, times(2))
            .convertAndSend(eq("/topic/kitchen/tickets"), eventCaptor.capture());
        assertEquals(List.of("CREATED", "CREATED"),
            eventCaptor.getAllValues().stream().map(KitchenTicketEvent::action).toList());
    }

    @Test
    @DisplayName("create - orderType và submit null dùng mặc định DINE_IN + gửi bếp ngay")
    void create_NullSubmitAndOrderType_UsesDefaults() {
        stubCreateHappyPath();

        CreateOrderRequest request = new CreateOrderRequest(
            "table-1", null,
            List.of(new OrderItemRequest("menu-1", 1, null)),
            null);

        OrderResponse response = orderService.create(request, "staff-1");

        assertEquals(OrderType.DINE_IN, response.orderType());
        verify(kitchenTicketRepository).save(any(KitchenTicket.class));
    }

    @Test
    @DisplayName("create - lỗi WebSocket không chặn việc tạo đơn và phiếu bếp")
    void create_WebSocketFailure_DoesNotBreakOrderCreation() {
        stubCreateHappyPath();
        doThrow(new RuntimeException("broker down"))
            .when(messagingTemplate).convertAndSend(eq("/topic/kitchen/tickets"), any(KitchenTicketEvent.class));

        OrderResponse response = orderService.create(new CreateOrderRequest(
            "table-1", OrderType.DINE_IN,
            List.of(new OrderItemRequest("menu-1", 1, null)), true), "staff-1");

        assertNotNull(response.id());
        verify(kitchenTicketRepository).save(any(KitchenTicket.class));
    }

    @Test
    @DisplayName("addItem - đơn đã đóng thì trả về 409, không tra cứu món")
    void addItem_ClosedOrder_ThrowsConflict() {
        Order order = openOrder();
        order.setStatus(OrderStatus.COMPLETED);
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> orderService.addItem("order-1",
                new AddOrderItemRequest("menu-1", 1, null), "staff-1"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(menuItemRepository, never()).findByIdAndDeletedAtIsNull(any());
        verify(orderItemRepository, never()).save(any(OrderItem.class));
    }

    @Test
    @DisplayName("addItem - không tìm thấy đơn trả về 404")
    void addItem_OrderNotFound_ThrowsNotFound() {
        when(orderRepository.findById("order-x")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> orderService.addItem("order-x",
                new AddOrderItemRequest("menu-1", 1, null), "staff-1"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("Không tìm thấy đơn hàng.", ex.getReason());
    }

    @Test
    @DisplayName("addItem - không tìm thấy món trả về 404")
    void addItem_MenuItemNotFound_ThrowsNotFound() {
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(openOrder()));
        when(menuItemRepository.findByIdAndDeletedAtIsNull("menu-x")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> orderService.addItem("order-1",
                new AddOrderItemRequest("menu-x", 1, null), "staff-1"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("Không tìm thấy món ăn.", ex.getReason());
    }

    @Test
    @DisplayName("addItem - tạo phiếu bếp đúng số lượng/ghi chú và ghi nhật ký ADD_ITEM")
    void addItem_CreatesTicketAndAudit() {
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(openOrder()));
        when(menuItemRepository.findByIdAndDeletedAtIsNull("menu-1")).thenReturn(Optional.of(pho));
        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(kitchenTicketRepository.save(any(KitchenTicket.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderItemRepository.findByOrderIdOrderByCreatedAtAsc("order-1")).thenReturn(List.of());

        orderService.addItem("order-1", new AddOrderItemRequest("menu-1", 3, "Không hành"), "staff-7");

        ArgumentCaptor<KitchenTicket> ticketCaptor = ArgumentCaptor.forClass(KitchenTicket.class);
        verify(kitchenTicketRepository).save(ticketCaptor.capture());
        assertEquals(3, ticketCaptor.getValue().getQuantity());
        assertEquals("Không hành", ticketCaptor.getValue().getNote());
        assertEquals("Bàn 1", ticketCaptor.getValue().getTableNumber());

        ArgumentCaptor<OrderItemAudit> auditCaptor = ArgumentCaptor.forClass(OrderItemAudit.class);
        verify(orderItemAuditRepository).save(auditCaptor.capture());
        OrderItemAudit audit = auditCaptor.getValue();
        assertEquals("ADD_ITEM", audit.getAction());
        assertEquals("staff-7", audit.getChangedBy());
        assertEquals(0, audit.getOldQuantity());
        assertEquals(3, audit.getNewQuantity());
        assertEquals(CookingStatus.QUEUED, audit.getNewStatus());
    }

    @Test
    @DisplayName("updateItem - món không thuộc đơn trả về 404")
    void updateItem_ItemOfAnotherOrder_ThrowsNotFound() {
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(openOrder()));
        OrderItem other = queuedItem();
        other.setOrderId("order-2");
        when(orderItemRepository.findById("item-1")).thenReturn(Optional.of(other));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> orderService.updateItem("order-1", "item-1",
                new UpdateOrderItemRequest(3, null), "staff-1"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("Món này không thuộc đơn hàng.", ex.getReason());
        verify(orderItemRepository, never()).save(any(OrderItem.class));
    }

    @Test
    @DisplayName("updateItem - không tìm thấy món trả về 404")
    void updateItem_ItemNotFound_ThrowsNotFound() {
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(openOrder()));
        when(orderItemRepository.findById("item-x")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> orderService.updateItem("order-1", "item-x",
                new UpdateOrderItemRequest(3, null), "staff-1"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    @DisplayName("updateItem - ghi chú rỗng được xóa thành null và đồng bộ xuống phiếu bếp")
    void updateItem_BlankNote_ClearsNoteAndSyncsTicket() {
        OrderItem item = queuedItem();
        KitchenTicket ticket = new KitchenTicket("ticket-1", "item-1", "order-1",
            "Bàn 1", "Phở Bò", 2, "Ít cay");
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(openOrder()));
        when(orderItemRepository.findById("item-1")).thenReturn(Optional.of(item));
        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(kitchenTicketRepository.findByOrderItemId("item-1")).thenReturn(Optional.of(ticket));
        when(kitchenTicketRepository.save(any(KitchenTicket.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderItemRepository.findByOrderIdOrderByCreatedAtAsc("order-1")).thenReturn(List.of(item));

        orderService.updateItem("order-1", "item-1",
            new UpdateOrderItemRequest(null, "   "), "staff-1");

        assertNull(item.getNote());
        assertEquals(2, item.getQuantity());
        assertNull(ticket.getNote());
        assertEquals(2, ticket.getQuantity());
        verify(messagingTemplate).convertAndSend(eq("/topic/kitchen/tickets"),
            any(KitchenTicketEvent.class));
    }

    @Test
    @DisplayName("updateItem - note null giữ nguyên ghi chú cũ, chỉ đổi số lượng")
    void updateItem_NullNote_KeepsOldNote() {
        OrderItem item = queuedItem();
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(openOrder()));
        when(orderItemRepository.findById("item-1")).thenReturn(Optional.of(item));
        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderItemRepository.findByOrderIdOrderByCreatedAtAsc("order-1")).thenReturn(List.of(item));

        orderService.updateItem("order-1", "item-1",
            new UpdateOrderItemRequest(5, null), "staff-1");

        assertEquals(5, item.getQuantity());
        assertEquals("Ít cay", item.getNote());
    }

    @Test
    @DisplayName("updateItem - nhật ký ghi rõ số lượng cũ → mới")
    void updateItem_AuditRecordsQuantityChange() {
        OrderItem item = queuedItem();
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(openOrder()));
        when(orderItemRepository.findById("item-1")).thenReturn(Optional.of(item));
        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderItemRepository.findByOrderIdOrderByCreatedAtAsc("order-1")).thenReturn(List.of(item));

        orderService.updateItem("order-1", "item-1",
            new UpdateOrderItemRequest(5, null), "staff-1");

        ArgumentCaptor<OrderItemAudit> captor = ArgumentCaptor.forClass(OrderItemAudit.class);
        verify(orderItemAuditRepository).save(captor.capture());
        assertEquals("UPDATE_ITEM", captor.getValue().getAction());
        assertEquals(2, captor.getValue().getOldQuantity());
        assertEquals(5, captor.getValue().getNewQuantity());
        assertTrue(captor.getValue().getReason().contains("2 → 5"));
    }

    @Test
    @DisplayName("cancelItem - lý do rỗng dùng mặc định 'Khách đổi món'")
    void cancelItem_BlankReason_UsesDefaultReason() {
        OrderItem item = queuedItem();
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(openOrder()));
        when(orderItemRepository.findById("item-1")).thenReturn(Optional.of(item));
        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderItemRepository.findByOrderIdOrderByCreatedAtAsc("order-1")).thenReturn(List.of(item));

        orderService.cancelItem("order-1", "item-1",
            new CancelOrderItemRequest("   "), "staff-1");

        assertEquals("Khách đổi món", item.getCancelReason());

        ArgumentCaptor<OrderItemAudit> captor = ArgumentCaptor.forClass(OrderItemAudit.class);
        verify(orderItemAuditRepository).save(captor.capture());
        assertEquals("CANCEL_ITEM", captor.getValue().getAction());
        assertEquals("Khách đổi món", captor.getValue().getReason());
        assertEquals(CookingStatus.CANCELLED, captor.getValue().getNewStatus());
    }

    @Test
    @DisplayName("cancelItem - món chưa có phiếu bếp vẫn hủy được, không phát sự kiện bếp")
    void cancelItem_NoTicket_StillCancels() {
        OrderItem item = queuedItem();
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(openOrder()));
        when(orderItemRepository.findById("item-1")).thenReturn(Optional.of(item));
        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderItemRepository.findByOrderIdOrderByCreatedAtAsc("order-1")).thenReturn(List.of(item));

        orderService.cancelItem("order-1", "item-1",
            new CancelOrderItemRequest("Hết nguyên liệu"), "staff-1");

        assertEquals(CookingStatus.CANCELLED, item.getCookingStatus());
        verify(kitchenTicketRepository, never()).save(any(KitchenTicket.class));
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/kitchen/tickets"),
            any(KitchenTicketEvent.class));
    }

    @Test
    @DisplayName("cancelItem - món thuộc đơn khác trả về 404")
    void cancelItem_ItemOfAnotherOrder_ThrowsNotFound() {
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(openOrder()));
        OrderItem other = queuedItem();
        other.setOrderId("order-2");
        when(orderItemRepository.findById("item-1")).thenReturn(Optional.of(other));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> orderService.cancelItem("order-1", "item-1",
                new CancelOrderItemRequest("test"), "staff-1"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(orderItemRepository, never()).save(any(OrderItem.class));
    }

    @Test
    @DisplayName("listAll - chỉ lọc theo trạng thái thì dùng truy vấn theo trạng thái")
    void listAll_StatusOnly_UsesStatusQuery() {
        when(orderRepository.findByStatusOrderByCreatedAtAsc(OrderStatus.IN_PROGRESS))
            .thenReturn(List.of(openOrder()));
        when(orderItemRepository.findByOrderIdOrderByCreatedAtAsc("order-1")).thenReturn(List.of());

        List<OrderResponse> result = orderService.listAll(null, OrderStatus.IN_PROGRESS);

        assertEquals(1, result.size());
        verify(orderRepository).findByStatusOrderByCreatedAtAsc(OrderStatus.IN_PROGRESS);
        verify(orderRepository, never()).findAllByOrderByCreatedAtDesc();
    }

    @Test
    @DisplayName("listAll - lọc theo bàn và trạng thái ngay trong bộ nhớ")
    void listAll_TableAndStatus_FiltersInMemory() {
        Order inProgress = openOrder();
        Order completed = openOrder();
        completed.setStatus(OrderStatus.COMPLETED);
        when(orderRepository.findByTableIdOrderByCreatedAtDesc("table-1"))
            .thenReturn(List.of(inProgress, completed));
        when(orderItemRepository.findByOrderIdOrderByCreatedAtAsc("order-1")).thenReturn(List.of());

        List<OrderResponse> result = orderService.listAll("table-1", OrderStatus.IN_PROGRESS);

        assertEquals(1, result.size());
        assertEquals(OrderStatus.IN_PROGRESS, result.get(0).status());
    }

    @Test
    @DisplayName("getById - trả về đơn kèm món và tạm tính")
    void getById_ReturnsOrderWithItems() {
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(openOrder()));
        when(orderItemRepository.findByOrderIdOrderByCreatedAtAsc("order-1"))
            .thenReturn(List.of(queuedItem()));

        OrderResponse response = orderService.getById("order-1");

        assertEquals(1, response.items().size());
        assertEquals("Phở Bò", response.items().get(0).itemName());
        assertEquals(0, new BigDecimal("110000").compareTo(response.subtotal()));
    }

    @Test
    @DisplayName("getById - đơn chưa gắn bàn trả về tableNumber null")
    void getById_OrderWithoutTable_ReturnsNullTableNumber() {
        Order order = new Order("order-1", null, "staff-1", OrderType.TAKE_AWAY);
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderIdOrderByCreatedAtAsc("order-1")).thenReturn(List.of());

        OrderResponse response = orderService.getById("order-1");

        assertNull(response.tableNumber());
    }
}
