package com.huongViet.order;

import com.huongViet.menu.MenuItem;
import com.huongViet.menu.MenuItemRepository;
import com.huongViet.payment.PaymentOrderRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

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
    @Mock
    private PaymentOrderRepository paymentOrderRepository;

    private OrderService orderService;

    private RestaurantTable occupiedTable;
    private MenuItem pho;
    private MenuItem unavailableDish;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(
            orderRepository,
            orderItemRepository,
            kitchenTicketRepository,
            orderItemAuditRepository,
            menuItemRepository,
            tableRepository,
            messagingTemplate,
            loyaltyAccounts,
            loyaltySettings,
            voucherService,
            jdbcTemplate,
            reservationRepository,
            paymentOrderRepository
        );

        occupiedTable = new RestaurantTable("table-1", "zone-1", "Bàn 1", 4);
        occupiedTable.setStatus(TableStatus.OCCUPIED);

        pho = new MenuItem("menu-1", "cat-1", "Phở Bò", "Phở bò đặc biệt",
            new BigDecimal("55000"), null, true);

        unavailableDish = new MenuItem("menu-2", "cat-1", "Gà chiên mật ong", "Gà chiên",
            new BigDecimal("89000"), null, false);

        org.mockito.Mockito.lenient().when(tableRepository.findById("table-1")).thenReturn(Optional.of(occupiedTable));
    }

    private CreateOrderRequest request(Boolean submit, String... menuItemIds) {
        List<OrderItemRequest> items = java.util.Arrays.stream(menuItemIds)
            .map(id -> new OrderItemRequest(id, 1, "Ít cay"))
            .toList();
        return new CreateOrderRequest("table-1", OrderType.DINE_IN, items, submit);
    }

    private OrderItem item(String id, String orderId, MenuItem menu, int quantity, CookingStatus status) {
        OrderItem item = new OrderItem(id, orderId, menu.getId(), quantity, menu.getCurrentPrice(), null);
        item.setMenuItem(menu);
        item.setCookingStatus(status);
        return item;
    }

    @Test
    void openSeatedOrder_CreatesDistinctOrdersAndRejectsOverflow() {
        List<Order> seated = new ArrayList<>();
        when(tableRepository.lockActiveById("table-1")).thenReturn(Optional.of(occupiedTable));
        when(orderRepository.findSeatedOrdersByTableIds(List.of("table-1"))).thenReturn(seated);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            seated.add(order);
            return order;
        });

        OrderResponse first = orderService.openSeatedOrder("table-1", 1, "staff-1");
        OrderResponse second = orderService.openSeatedOrder("table-1", 2, "staff-1");

        assertNotEquals(first.id(), second.id());
        assertEquals("table-1", first.tableId());
        assertEquals(first.tableId(), second.tableId());
        assertEquals(1, first.partySize());
        assertEquals(2, second.partySize());
        assertTrue(first.seated());
        assertTrue(second.seated());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> orderService.openSeatedOrder("table-1", 2, "staff-1"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals("Bàn còn 1 ghế trống.", ex.getReason());
        verify(orderRepository, times(2)).save(any(Order.class));
    }

    @Test
    void releaseSeats_LegacyOrderOnlyCancelsWhenEmpty_AndDoesNotChangeTableStatus() {
        Order order = openOrder();
        OrderItem row = queuedItem();
        when(orderRepository.findTableIdByOrderId("order-1")).thenReturn(Optional.of("table-1"));
        when(tableRepository.lockActiveById("table-1")).thenReturn(Optional.of(occupiedTable));
        when(orderRepository.lockById("order-1")).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderIdOrderByCreatedAtAsc("order-1")).thenReturn(List.of(row));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> orderService.releaseSeats("order-1"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals(OrderStatus.IN_PROGRESS, order.getStatus());
        verify(orderRepository, never()).save(order);

        row.setCookingStatus(CookingStatus.CANCELLED);
        orderService.releaseSeats("order-1");
        orderService.releaseSeats("order-1"); // Repeating the same request is harmless.

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertNotNull(order.getClosedAt());
        assertEquals(TableStatus.OCCUPIED, occupiedTable.getStatus());
        verify(orderRepository).save(order);
        verify(tableRepository, never()).save(occupiedTable);
        verifyNoInteractions(reservationRepository, messagingTemplate);
    }

    @Test
    @DisplayName("create - ném lỗi 409 CONFLICT nếu bàn không ở trạng thái OCCUPIED")
    void create_TableNotOccupied_ThrowsConflict() {
        RestaurantTable availableTable = new RestaurantTable("table-1", "zone-1", "Bàn 1", 4);
        availableTable.setStatus(TableStatus.AVAILABLE);

        when(tableRepository.lockActiveById("table-1")).thenReturn(Optional.of(availableTable));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> orderService.create(request(true, "menu-1"), "staff-1"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Chỉ có thể gọi món cho bàn đang sử dụng"));
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("create - ném lỗi 404 nếu không tìm thấy bàn")
    void create_TableNotFound_ThrowsNotFound() {
        when(tableRepository.lockActiveById("table-1")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> orderService.create(request(true, "menu-1"), "staff-1"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("Không tìm thấy bàn.", ex.getReason());
    }

    @Test
    @DisplayName("create - ném lỗi 409 CONFLICT nếu món không còn sẵn và không tạo đơn")
    void create_DishUnavailable_ThrowsConflict() {
        when(tableRepository.lockActiveById("table-1")).thenReturn(Optional.of(occupiedTable));
        when(menuItemRepository.findByIdAndDeletedAtIsNull("menu-2")).thenReturn(Optional.of(unavailableDish));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> orderService.create(request(true, "menu-2"), "staff-1"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Gà chiên mật ong"));
        verify(orderRepository, never()).save(any(Order.class));
        verify(kitchenTicketRepository, never()).save(any(KitchenTicket.class));
    }

    @Test
    @DisplayName("create - ném lỗi 404 nếu không tìm thấy món ăn")
    void create_DishNotFound_ThrowsNotFound() {
        when(tableRepository.lockActiveById("table-1")).thenReturn(Optional.of(occupiedTable));
        when(menuItemRepository.findByIdAndDeletedAtIsNull("menu-x")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> orderService.create(request(true, "menu-x"), "staff-1"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("Không tìm thấy món ăn.", ex.getReason());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("create với submit=true - tạo một phiếu bếp cho mỗi món và phát WebSocket")
    void create_SubmitTrue_CreatesKitchenTickets() {
        when(tableRepository.lockActiveById("table-1")).thenReturn(Optional.of(occupiedTable));
        when(menuItemRepository.findByIdAndDeletedAtIsNull("menu-1")).thenReturn(Optional.of(pho));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderItemRepository.findByOrderIdOrderByCreatedAtAsc(anyString()))
            .thenAnswer(inv -> List.of(item("item-1", "order-1", pho, 1, CookingStatus.QUEUED)));
        when(kitchenTicketRepository.save(any(KitchenTicket.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.create(request(true, "menu-1"), "staff-1");

        assertNotNull(response.id());
        assertEquals("Bàn 1", response.tableNumber());
        assertEquals(OrderStatus.IN_PROGRESS, response.status());
        assertEquals(1, response.items().size());
        assertEquals("Phở Bò", response.items().get(0).itemName());
        assertEquals(new BigDecimal("55000"), response.items().get(0).lineTotal());

        verify(kitchenTicketRepository, times(1)).save(any(KitchenTicket.class));
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/kitchen/tickets"), any(KitchenTicketEvent.class));

        ArgumentCaptor<KitchenTicketEvent> eventCaptor = ArgumentCaptor.forClass(KitchenTicketEvent.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/kitchen/tickets"), eventCaptor.capture());
        assertEquals("CREATED", eventCaptor.getValue().action());
        assertEquals("Phở Bò", eventCaptor.getValue().ticket().itemName());
        assertEquals("Ít cay", eventCaptor.getValue().ticket().note());
    }

    @Test
    @DisplayName("create với submit=false - chỉ tạo đơn, không tạo phiếu bếp và không phát WebSocket")
    void create_SubmitFalse_CreatesNoKitchenTicket() {
        when(tableRepository.lockActiveById("table-1")).thenReturn(Optional.of(occupiedTable));
        when(menuItemRepository.findByIdAndDeletedAtIsNull("menu-1")).thenReturn(Optional.of(pho));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderItemRepository.findByOrderIdOrderByCreatedAtAsc(anyString()))
            .thenAnswer(inv -> List.of(item("item-1", "order-1", pho, 1, CookingStatus.QUEUED)));

        OrderResponse response = orderService.create(request(false, "menu-1"), "staff-1");

        assertNotNull(response.id());
        assertEquals(1, response.items().size());
        verify(orderRepository, times(1)).save(any(Order.class));
        verify(kitchenTicketRepository, never()).save(any(KitchenTicket.class));
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(KitchenTicketEvent.class));
    }

    @Test
    @DisplayName("listAll - không có bộ lọc thì trả về tất cả đơn, mới nhất trước")
    void listAll_NoFilter_ReturnsAll() {
        Order order = new Order("order-1", "table-1", "staff-1", OrderType.DINE_IN);
        order.setTable(occupiedTable);

        when(orderRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(order));
        when(orderItemRepository.findByOrderIdOrderByCreatedAtAsc("order-1")).thenReturn(List.of());

        List<OrderResponse> result = orderService.listAll(null, null);

        assertEquals(1, result.size());
        assertEquals("order-1", result.get(0).id());
        verify(orderRepository, never()).findByTableIdOrderByCreatedAtDesc(anyString());
    }

    @Test
    @DisplayName("listAll - lọc theo bàn")
    void listAll_FilterByTable() {
        when(orderRepository.findByTableIdOrderByCreatedAtDesc("table-1")).thenReturn(List.of());

        List<OrderResponse> result = orderService.listAll("table-1", null);

        assertTrue(result.isEmpty());
        verify(orderRepository).findByTableIdOrderByCreatedAtDesc("table-1");
    }

    @Test
    @DisplayName("getById - ném lỗi 404 nếu không tìm thấy đơn hàng")
    void getById_NotFound_ThrowsNotFound() {
        when(orderRepository.findById("order-x")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> orderService.getById("order-x"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("Không tìm thấy đơn hàng.", ex.getReason());
    }

    @Test
    @DisplayName("getActiveByTable - trả về đơn đang mở gần nhất của bàn")
    void getActiveByTable_ReturnsActiveOrder() {
        Order order = new Order("order-1", "table-1", "staff-1", OrderType.DINE_IN);
        order.setTable(occupiedTable);

        when(orderRepository.findFirstByTableIdAndStatusOrderByCreatedAtDesc("table-1", OrderStatus.IN_PROGRESS))
            .thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderIdOrderByCreatedAtAsc("order-1")).thenReturn(List.of());

        OrderResponse response = orderService.getActiveByTable("table-1");

        assertEquals("order-1", response.id());
        assertEquals("Bàn 1", response.tableNumber());
    }

    @Test
    @DisplayName("getActiveByTable - ném lỗi 404 nếu bàn chưa có đơn đang mở")
    void getActiveByTable_NoActiveOrder_ThrowsNotFound() {
        when(orderRepository.findFirstByTableIdAndStatusOrderByCreatedAtDesc("table-1", OrderStatus.IN_PROGRESS))
            .thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> orderService.getActiveByTable("table-1"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("Bàn này chưa có đơn hàng đang mở.", ex.getReason());
    }

    // ---------- UC-17: them / sua / huy mon trong don ----------

    private Order openOrder() {
        Order order = new Order("order-1", "table-1", "staff-1", OrderType.DINE_IN);
        order.setTable(occupiedTable);
        return order;
    }

    private OrderItem queuedItem() {
        OrderItem item = new OrderItem("item-1", "order-1", "menu-1", 2,
            new BigDecimal("55000"), null);
        item.setMenuItem(pho);
        return item;
    }

    @Test
    @DisplayName("addItem - them mon vao don dang mo, tao phieu bep va ghi nhat ky")
    void addItem_Success() {
        Order order = openOrder();
        when(orderRepository.lockById("order-1")).thenReturn(Optional.of(order));
        when(menuItemRepository.findByIdAndDeletedAtIsNull("menu-1")).thenReturn(Optional.of(pho));
        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(i -> i.getArgument(0));
        when(kitchenTicketRepository.save(any(KitchenTicket.class))).thenAnswer(i -> i.getArgument(0));
        when(orderItemRepository.findByOrderIdOrderByCreatedAtAsc("order-1")).thenReturn(List.of());

        OrderResponse response = orderService.addItem("order-1",
            new AddOrderItemRequest("menu-1", 2, "Ít cay"), "staff-1");

        assertNotNull(response.id());
        verify(kitchenTicketRepository).save(any(KitchenTicket.class));
        verify(orderItemAuditRepository).save(any(OrderItemAudit.class));
        verify(messagingTemplate).convertAndSend(eq("/topic/kitchen/tickets"), any(KitchenTicketEvent.class));
    }

    @Test
    @DisplayName("addItem - ném lỗi 409 nếu món đã hết hàng (BR-40)")
    void addItem_UnavailableDish_ThrowsConflict() {
        Order order = openOrder();
        when(orderRepository.lockById("order-1")).thenReturn(Optional.of(order));
        when(menuItemRepository.findByIdAndDeletedAtIsNull("menu-2")).thenReturn(Optional.of(unavailableDish));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> orderService.addItem("order-1", new AddOrderItemRequest("menu-2", 1, null), "staff-1"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(orderItemRepository, never()).save(any(OrderItem.class));
    }

    @Test
    @DisplayName("addItems - lô đầu tiên tạo nhiều phiếu bếp đều không gắn cờ Gọi thêm")
    void addItems_FirstBatchKeepsAllTicketsNotAdditional() {
        Order order = openOrder();
        MenuItem bunCha = new MenuItem("menu-3", "cat-1", "Bún chả", "Bún chả Hà Nội",
            new BigDecimal("45000"), null, true);
        when(orderRepository.lockById("order-1")).thenReturn(Optional.of(order));
        when(menuItemRepository.findByIdAndDeletedAtIsNull("menu-1")).thenReturn(Optional.of(pho));
        when(menuItemRepository.findByIdAndDeletedAtIsNull("menu-3")).thenReturn(Optional.of(bunCha));
        when(kitchenTicketRepository.existsByOrderId("order-1")).thenReturn(false);
        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(i -> i.getArgument(0));
        when(kitchenTicketRepository.save(any(KitchenTicket.class))).thenAnswer(i -> i.getArgument(0));
        when(orderItemRepository.findByOrderIdOrderByCreatedAtAsc("order-1")).thenReturn(List.of());

        orderService.addItems("order-1", new AddOrderItemsBatchRequest(List.of(
            new AddOrderItemRequest("menu-1", 2, " Ít cay "),
            new AddOrderItemRequest("menu-3", 1, "   ")
        )), "staff-1");

        ArgumentCaptor<KitchenTicket> ticketCaptor = ArgumentCaptor.forClass(KitchenTicket.class);
        verify(kitchenTicketRepository, times(2)).save(ticketCaptor.capture());
        List<KitchenTicket> savedTickets = ticketCaptor.getAllValues();
        assertEquals(2, savedTickets.size());
        assertFalse(savedTickets.get(0).isAdditional());
        assertFalse(savedTickets.get(1).isAdditional());
        assertEquals("Ít cay", savedTickets.get(0).getNote());
        assertNull(savedTickets.get(1).getNote());
        verify(orderItemAuditRepository, times(2)).save(any(OrderItemAudit.class));
        verify(messagingTemplate, times(2)).convertAndSend(eq("/topic/kitchen/tickets"), any(KitchenTicketEvent.class));
    }

    @Test
    @DisplayName("addItems - lô gọi thêm khi đã có phiếu bếp sẽ đánh dấu tất cả phiếu là Gọi thêm")
    void addItems_SubsequentBatchMarksAllTicketsAdditional() {
        Order order = openOrder();
        MenuItem bunCha = new MenuItem("menu-3", "cat-1", "Bún chả", "Bún chả Hà Nội",
            new BigDecimal("45000"), null, true);
        when(orderRepository.lockById("order-1")).thenReturn(Optional.of(order));
        when(menuItemRepository.findByIdAndDeletedAtIsNull("menu-1")).thenReturn(Optional.of(pho));
        when(menuItemRepository.findByIdAndDeletedAtIsNull("menu-3")).thenReturn(Optional.of(bunCha));
        when(kitchenTicketRepository.existsByOrderId("order-1")).thenReturn(true);
        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(i -> i.getArgument(0));
        when(kitchenTicketRepository.save(any(KitchenTicket.class))).thenAnswer(i -> i.getArgument(0));
        when(orderItemRepository.findByOrderIdOrderByCreatedAtAsc("order-1")).thenReturn(List.of());

        orderService.addItems("order-1", new AddOrderItemsBatchRequest(List.of(
            new AddOrderItemRequest("menu-1", 1, null),
            new AddOrderItemRequest("menu-3", 2, "Không rau")
        )), "staff-1");

        ArgumentCaptor<KitchenTicket> ticketCaptor = ArgumentCaptor.forClass(KitchenTicket.class);
        verify(kitchenTicketRepository, times(2)).save(ticketCaptor.capture());
        assertTrue(ticketCaptor.getAllValues().stream().allMatch(KitchenTicket::isAdditional));
    }

    @Test
    @DisplayName("addItems - có 1 món hết hàng sẽ từ chối cả lô, không lưu món hay phiếu bếp nào")
    void addItems_UnavailableDishRejectsEntireBatchAtomically() {
        Order order = openOrder();
        when(orderRepository.lockById("order-1")).thenReturn(Optional.of(order));
        when(menuItemRepository.findByIdAndDeletedAtIsNull("menu-1")).thenReturn(Optional.of(pho));
        when(menuItemRepository.findByIdAndDeletedAtIsNull("menu-2")).thenReturn(Optional.of(unavailableDish));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> orderService.addItems("order-1", new AddOrderItemsBatchRequest(List.of(
                new AddOrderItemRequest("menu-1", 1, null),
                new AddOrderItemRequest("menu-2", 1, null)
            )), "staff-1"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(orderItemRepository, never()).save(any(OrderItem.class));
        verify(kitchenTicketRepository, never()).save(any(KitchenTicket.class));
        verifyNoInteractions(orderItemAuditRepository);
    }

    private void stubGroup(OrderItem... items) {
        when(orderRepository.lockById("order-1")).thenReturn(Optional.of(openOrder()));
        when(orderItemRepository.findByOrderIdOrderByCreatedAtAsc("order-1")).thenReturn(List.of(items));
    }

    private KitchenTicket stubTicket(OrderItem item) {
        KitchenTicket ticket = new KitchenTicket("ticket-" + item.getId(), item.getId(), item.getOrderId(),
            "Bàn 1", pho.getItemName(), item.getQuantity(), item.getNote());
        when(kitchenTicketRepository.findByOrderItemId(item.getId())).thenReturn(Optional.of(ticket));
        return ticket;
    }

    @Test
    void decrementGroup_CancelsNewestSinglePortionAndKeepsOlderTickets() {
        OrderItem first = queuedItem();
        OrderItem newest = item("item-2", "order-1", pho, 1, CookingStatus.QUEUED);
        stubGroup(first, newest);
        KitchenTicket latestTicket = stubTicket(newest);
        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(i -> i.getArgument(0));
        when(kitchenTicketRepository.save(latestTicket)).thenReturn(latestTicket);

        OrderResponse response = orderService.adjustItems("order-1", new AdjustOrderItemsRequest(
            List.of("item-2", "item-1"), AdjustOrderItemsRequest.Action.DECREMENT, CookingStatus.QUEUED, 3, null, null), "staff-1");

        assertEquals(2, first.getQuantity());
        assertEquals(CookingStatus.QUEUED, first.getCookingStatus());
        assertEquals(CookingStatus.CANCELLED, latestTicket.getStatus());
        assertEquals(new BigDecimal("110000"), response.subtotal());
        assertEquals(new BigDecimal("55000"), response.cancelledAmount());
        verify(kitchenTicketRepository).save(latestTicket);
        verify(orderItemAuditRepository).save(argThat(audit -> audit.getOrderItemId().equals("item-2")
            && audit.getAction().equals("CANCEL_ITEM") && audit.getReason().equals("Giảm số lượng")));
    }

    @Test
    void decrementGroup_UpdatesQuantityInExistingTicket() {
        OrderItem first = queuedItem();
        stubGroup(first);
        KitchenTicket ticket = stubTicket(first);
        when(orderItemRepository.save(first)).thenReturn(first);
        when(kitchenTicketRepository.save(ticket)).thenReturn(ticket);

        OrderResponse response = orderService.adjustItems("order-1", new AdjustOrderItemsRequest(
            List.of("item-1"), AdjustOrderItemsRequest.Action.DECREMENT, CookingStatus.QUEUED, 2, null, null), "staff-1");

        assertEquals(1, first.getQuantity());
        assertEquals(1, ticket.getQuantity());
        assertEquals(CookingStatus.QUEUED, ticket.getStatus());
        assertEquals(new BigDecimal("55000"), response.subtotal());
    }

    @Test
    void decrementGroup_RejectsStaleQuantityAndRequiresConfirmationForLastPortion() {
        stubGroup(item("item-1", "order-1", pho, 1, CookingStatus.QUEUED));
        for (int displayedQuantity : List.of(2, 1)) {
            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> orderService.adjustItems("order-1", new AdjustOrderItemsRequest(List.of("item-1"),
                    AdjustOrderItemsRequest.Action.DECREMENT, CookingStatus.QUEUED, displayedQuantity, null, null), "staff-1"));
            assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        }
        verify(orderItemRepository, never()).save(any(OrderItem.class));
    }

    @Test
    void cancelGroup_RejectsItemsOfAnotherOrder() {
        stubGroup(queuedItem());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> orderService.adjustItems("order-1", new AdjustOrderItemsRequest(List.of("item-1", "item-other-order"),
                AdjustOrderItemsRequest.Action.CANCEL, CookingStatus.QUEUED, 3, null, "Khách đổi món"), "staff-1"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(orderItemRepository, never()).save(any(OrderItem.class));
    }

    @Test
    void cancelGroup_ValidatesEveryBatchBeforeMutatingAny() {
        OrderItem first = queuedItem();
        OrderItem second = item("item-2", "order-1", pho, 1, CookingStatus.QUEUED);
        stubGroup(first, second);
        stubTicket(first);
        KitchenTicket started = stubTicket(second);
        started.setStatus(CookingStatus.PREPARING);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> orderService.adjustItems("order-1", new AdjustOrderItemsRequest(List.of("item-1", "item-2"),
                AdjustOrderItemsRequest.Action.CANCEL, CookingStatus.QUEUED, 3, null, "Khách đổi món"), "staff-1"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals(CookingStatus.QUEUED, first.getCookingStatus());
        verify(orderItemRepository, never()).save(any(OrderItem.class));
        verifyNoInteractions(orderItemAuditRepository);
    }

    @Test
    void updateGroupNote_SyncsEveryTicketWithoutChangingQuantity() {
        OrderItem first = queuedItem();
        OrderItem second = item("item-2", "order-1", pho, 1, CookingStatus.QUEUED);
        stubGroup(first, second);
        KitchenTicket firstTicket = stubTicket(first);
        KitchenTicket secondTicket = stubTicket(second);
        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(i -> i.getArgument(0));
        when(kitchenTicketRepository.save(any(KitchenTicket.class))).thenAnswer(i -> i.getArgument(0));
        orderService.adjustItems("order-1", new AdjustOrderItemsRequest(List.of("item-1", "item-2"),
            AdjustOrderItemsRequest.Action.UPDATE_NOTE, CookingStatus.QUEUED, 3, " Không hành ", null), "staff-1");

        assertEquals("Không hành", firstTicket.getNote());
        assertEquals("Không hành", secondTicket.getNote());
        assertEquals(2, firstTicket.getQuantity());
        assertEquals(1, secondTicket.getQuantity());
        verify(orderItemAuditRepository, times(2)).save(any(OrderItemAudit.class));
    }

    @Test
    @DisplayName("updateItem - đổi số lượng, đồng bộ phiếu bếp và ghi nhật ký")
    void updateItem_Success() {
        Order order = openOrder();
        OrderItem item = queuedItem();
        when(orderRepository.lockById("order-1")).thenReturn(Optional.of(order));
        when(orderItemRepository.findById("item-1")).thenReturn(Optional.of(item));
        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(i -> i.getArgument(0));
        when(orderItemRepository.findByOrderIdOrderByCreatedAtAsc("order-1")).thenReturn(List.of(item));

        orderService.updateItem("order-1", "item-1", new UpdateOrderItemRequest(5, "Không cay"), "staff-1");

        assertEquals(5, item.getQuantity());
        verify(orderItemAuditRepository).save(any(OrderItemAudit.class));
    }

    @Test
    @DisplayName("updateItem - ném lỗi 409 nếu bếp đã nấu xong món (BR-39)")
    void updateItem_AlreadyReady_ThrowsConflict() {
        Order order = openOrder();
        OrderItem item = queuedItem();
        item.setCookingStatus(CookingStatus.READY);
        when(orderRepository.lockById("order-1")).thenReturn(Optional.of(order));
        when(orderItemRepository.findById("item-1")).thenReturn(Optional.of(item));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> orderService.updateItem("order-1", "item-1", new UpdateOrderItemRequest(3, null), "staff-1"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(orderItemRepository, never()).save(any(OrderItem.class));
    }

    @Test
    @DisplayName("cancelItem - đánh dấu hủy món và hủy luôn phiếu bếp")
    void cancelItem_Success() {
        Order order = openOrder();
        OrderItem item = queuedItem();
        KitchenTicket ticket = new KitchenTicket("ticket-1", "item-1", "order-1", "Bàn 1", "Phở Bò", 2, null);
        when(orderRepository.lockById("order-1")).thenReturn(Optional.of(order));
        when(orderItemRepository.findById("item-1")).thenReturn(Optional.of(item));
        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(i -> i.getArgument(0));
        when(kitchenTicketRepository.findByOrderItemId("item-1")).thenReturn(Optional.of(ticket));
        when(kitchenTicketRepository.save(any(KitchenTicket.class))).thenAnswer(i -> i.getArgument(0));
        when(orderItemRepository.findByOrderIdOrderByCreatedAtAsc("order-1")).thenReturn(List.of(item));

        orderService.cancelItem("order-1", "item-1", new CancelOrderItemRequest("Khách đổi món"), "staff-1");

        assertEquals(CookingStatus.CANCELLED, item.getCookingStatus());
        assertEquals("Khách đổi món", item.getCancelReason());
        assertEquals(CookingStatus.CANCELLED, ticket.getStatus());
        verify(orderItemAuditRepository).save(any(OrderItemAudit.class));
    }

    @Test
    @DisplayName("cancelItem - ném lỗi 409 nếu món đã được phục vụ (BR-39)")
    void cancelItem_AlreadyServed_ThrowsConflict() {
        Order order = openOrder();
        OrderItem item = queuedItem();
        item.setCookingStatus(CookingStatus.SERVED);
        when(orderRepository.lockById("order-1")).thenReturn(Optional.of(order));
        when(orderItemRepository.findById("item-1")).thenReturn(Optional.of(item));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> orderService.cancelItem("order-1", "item-1", new CancelOrderItemRequest("test"), "staff-1"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(orderItemRepository, never()).save(any(OrderItem.class));
    }

    @Test
    @DisplayName("cancelItem - ném lỗi 409 khi món đã được xác nhận đang nấu (BR-41)")
    void cancelItem_PreparingItem_ThrowsConflict() {
        Order order = openOrder();
        OrderItem item = queuedItem();
        item.setCookingStatus(CookingStatus.PREPARING);
        when(orderRepository.lockById("order-1")).thenReturn(Optional.of(order));
        when(orderItemRepository.findById("item-1")).thenReturn(Optional.of(item));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> orderService.cancelItem("order-1", "item-1", new CancelOrderItemRequest("Khách đổi món"), "staff-1"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("chờ chế biến"));
        verify(orderItemRepository, never()).save(any(OrderItem.class));
        verify(kitchenTicketRepository, never()).save(any(KitchenTicket.class));
    }

    @Test
    @DisplayName("cancelItem - ném lỗi 409 khi phiếu bếp đã sang PREPARING dù món còn QUEUED (BR-41)")
    void cancelItem_TicketAlreadyStarted_ThrowsConflict() {
        Order order = openOrder();
        OrderItem item = queuedItem();
        KitchenTicket ticket = new KitchenTicket("ticket-1", "item-1", "order-1", "Bàn 1", "Phở Bò", 2, null);
        ticket.setStatus(CookingStatus.PREPARING);
        when(orderRepository.lockById("order-1")).thenReturn(Optional.of(order));
        when(orderItemRepository.findById("item-1")).thenReturn(Optional.of(item));
        when(kitchenTicketRepository.findByOrderItemId("item-1")).thenReturn(Optional.of(ticket));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> orderService.cancelItem("order-1", "item-1", new CancelOrderItemRequest("Khách đổi món"), "staff-1"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals(CookingStatus.QUEUED, item.getCookingStatus());
        verify(orderItemRepository, never()).save(any(OrderItem.class));
    }

    @Test
    @DisplayName("cancelItem - ném lỗi 409 khi món đã được huỷ trước đó")
    void cancelItem_AlreadyCancelled_ThrowsConflict() {
        Order order = openOrder();
        OrderItem item = queuedItem();
        item.setCookingStatus(CookingStatus.CANCELLED);
        when(orderRepository.lockById("order-1")).thenReturn(Optional.of(order));
        when(orderItemRepository.findById("item-1")).thenReturn(Optional.of(item));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> orderService.cancelItem("order-1", "item-1", new CancelOrderItemRequest("test"), "staff-1"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("đã được huỷ"));
    }

    @Test
    @DisplayName("cancelItem - món vừa đặt (QUEUED) huỷ được và không tính vào tạm tính của đơn (BR-41)")
    void cancelItem_QueuedItem_ExcludedFromSubtotal() {
        Order order = openOrder();
        OrderItem item = queuedItem();
        when(orderRepository.lockById("order-1")).thenReturn(Optional.of(order));
        when(orderItemRepository.findById("item-1")).thenReturn(Optional.of(item));
        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(i -> i.getArgument(0));
        when(kitchenTicketRepository.findByOrderItemId("item-1")).thenReturn(Optional.empty());
        when(orderItemRepository.findByOrderIdOrderByCreatedAtAsc("order-1")).thenReturn(List.of(item));

        OrderResponse response = orderService.cancelItem("order-1", "item-1",
            new CancelOrderItemRequest("Khách đổi món"), "staff-1");

        assertEquals(0, BigDecimal.ZERO.compareTo(response.subtotal()));
        assertEquals(0, new BigDecimal("110000").compareTo(response.cancelledAmount()));
        assertEquals(CookingStatus.CANCELLED, response.items().get(0).cookingStatus());
    }

    @Test
    @DisplayName("OrderResponse - tạm tính chỉ cộng các món chưa huỷ")
    void orderResponse_SubtotalSkipsCancelledItems() {
        Order order = openOrder();
        OrderItem kept = queuedItem();
        OrderItem cancelled = new OrderItem("item-2", "order-1", "menu-1", 1,
            new BigDecimal("55000"), null);
        cancelled.setMenuItem(pho);
        cancelled.setCookingStatus(CookingStatus.CANCELLED);

        OrderResponse response = OrderResponse.from(order, List.of(kept, cancelled));

        assertEquals(0, new BigDecimal("110000").compareTo(response.subtotal()));
        assertEquals(0, new BigDecimal("55000").compareTo(response.cancelledAmount()));
    }

    @Test
    @DisplayName("updateItem - ném lỗi 409 nếu đơn đã đóng")
    void updateItem_ClosedOrder_ThrowsConflict() {
        Order order = openOrder();
        order.setStatus(OrderStatus.COMPLETED);
        when(orderRepository.lockById("order-1")).thenReturn(Optional.of(order));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> orderService.updateItem("order-1", "item-1", new UpdateOrderItemRequest(3, null), "staff-1"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }
}
