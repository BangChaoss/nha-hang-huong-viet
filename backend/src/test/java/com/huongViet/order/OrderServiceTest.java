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
    private MenuItemRepository menuItemRepository;

    @Mock
    private TableRepository tableRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

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
            menuItemRepository,
            tableRepository,
            messagingTemplate
        );

        occupiedTable = new RestaurantTable("table-1", "zone-1", "Bàn 1", 4);
        occupiedTable.setStatus(TableStatus.OCCUPIED);

        pho = new MenuItem("menu-1", "cat-1", "Phở Bò", "Phở bò đặc biệt",
            new BigDecimal("55000"), null, true);

        unavailableDish = new MenuItem("menu-2", "cat-1", "Gà chiên mật ong", "Gà chiên",
            new BigDecimal("89000"), null, false);
    }

    private CreateOrderRequest request(Boolean submit, String... menuItemIds) {
        List<OrderItemRequest> items = java.util.Arrays.stream(menuItemIds)
            .map(id -> new OrderItemRequest(id, 1, "Ít cay"))
            .toList();
        return new CreateOrderRequest("table-1", OrderType.DINE_IN, items, submit);
    }

    @Test
    @DisplayName("create - ném lỗi 409 CONFLICT nếu bàn không ở trạng thái OCCUPIED")
    void create_TableNotOccupied_ThrowsConflict() {
        RestaurantTable availableTable = new RestaurantTable("table-1", "zone-1", "Bàn 1", 4);
        availableTable.setStatus(TableStatus.AVAILABLE);

        when(tableRepository.findByIdAndDeletedAtIsNull("table-1")).thenReturn(Optional.of(availableTable));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> orderService.create(request(true, "menu-1"), "staff-1"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Chỉ có thể gọi món cho bàn đang sử dụng"));
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("create - ném lỗi 404 nếu không tìm thấy bàn")
    void create_TableNotFound_ThrowsNotFound() {
        when(tableRepository.findByIdAndDeletedAtIsNull("table-1")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> orderService.create(request(true, "menu-1"), "staff-1"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("Không tìm thấy bàn.", ex.getReason());
    }

    @Test
    @DisplayName("create - ném lỗi 409 CONFLICT nếu món không còn sẵn và không tạo đơn")
    void create_DishUnavailable_ThrowsConflict() {
        when(tableRepository.findByIdAndDeletedAtIsNull("table-1")).thenReturn(Optional.of(occupiedTable));
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
        when(tableRepository.findByIdAndDeletedAtIsNull("table-1")).thenReturn(Optional.of(occupiedTable));
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
        when(tableRepository.findByIdAndDeletedAtIsNull("table-1")).thenReturn(Optional.of(occupiedTable));
        when(menuItemRepository.findByIdAndDeletedAtIsNull("menu-1")).thenReturn(Optional.of(pho));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(inv -> inv.getArgument(0));
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
        when(tableRepository.findByIdAndDeletedAtIsNull("table-1")).thenReturn(Optional.of(occupiedTable));
        when(menuItemRepository.findByIdAndDeletedAtIsNull("menu-1")).thenReturn(Optional.of(pho));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(inv -> inv.getArgument(0));

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
}
