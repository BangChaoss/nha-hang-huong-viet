package com.huongViet.order;

import com.huongViet.table.RestaurantTable;
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

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KitchenTicketServiceTest {

    @Mock
    private KitchenTicketRepository kitchenTicketRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private KitchenTicketService kitchenTicketService;

    private OrderItem orderItem;

    @BeforeEach
    void setUp() {
        kitchenTicketService = new KitchenTicketService(
            kitchenTicketRepository,
            orderItemRepository,
            orderRepository,
            messagingTemplate
        );

        orderItem = new OrderItem("item-1", "order-1", "menu-1", 2, new java.math.BigDecimal("55000"), "Ít cay");
        orderItem.setCookingStatus(CookingStatus.QUEUED);
    }

    private KitchenTicket ticket(CookingStatus status) {
        KitchenTicket ticket = new KitchenTicket("ticket-1", "item-1", "order-1", "Bàn 1", "Phở Bò", 2, "Ít cay");
        ticket.setStatus(status);
        return ticket;
    }

    @Test
    @DisplayName("listAll - không lọc thì lấy toàn bộ phiếu theo thứ tự FIFO")
    void listAll_NoFilter_ReturnsFifo() {
        KitchenTicket ticket = ticket(CookingStatus.QUEUED);
        when(kitchenTicketRepository.findAllByOrderByCreatedAtAsc()).thenReturn(List.of(ticket));

        List<KitchenTicketResponse> result = kitchenTicketService.listAll(null);

        assertEquals(1, result.size());
        assertEquals("Phở Bò", result.get(0).itemName());
        assertEquals("Bàn 1", result.get(0).tableNumber());
        verify(kitchenTicketRepository, never()).findByStatusOrderByCreatedAtAsc(any());
    }

    @Test
    @DisplayName("listAll - lọc theo trạng thái thì dùng bộ lọc FIFO của kho")
    void listAll_FilterByStatus() {
        when(kitchenTicketRepository.findByStatusOrderByCreatedAtAsc(CookingStatus.QUEUED))
            .thenReturn(List.of(ticket(CookingStatus.QUEUED)));

        List<KitchenTicketResponse> result = kitchenTicketService.listAll(CookingStatus.QUEUED);

        assertEquals(1, result.size());
        verify(kitchenTicketRepository).findByStatusOrderByCreatedAtAsc(CookingStatus.QUEUED);
    }

    @Test
    @DisplayName("updateStatus - ném lỗi 404 nếu không tìm thấy phiếu bếp")
    void updateStatus_NotFound_ThrowsNotFound() {
        when(kitchenTicketRepository.findById("ticket-x")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> kitchenTicketService.updateStatus("ticket-x", new UpdateTicketStatusRequest(CookingStatus.PREPARING)));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("Không tìm thấy phiếu bếp.", ex.getReason());
    }

    @Test
    @DisplayName("updateStatus - cho phép chuyển QUEUED sang PREPARING")
    void updateStatus_QueuedToPreparing_Success() {
        KitchenTicket ticket = ticket(CookingStatus.QUEUED);
        when(kitchenTicketRepository.findById("ticket-1")).thenReturn(Optional.of(ticket));
        when(kitchenTicketRepository.save(any(KitchenTicket.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderItemRepository.findById("item-1")).thenReturn(Optional.of(orderItem));

        KitchenTicketResponse response = kitchenTicketService.updateStatus("ticket-1",
            new UpdateTicketStatusRequest(CookingStatus.PREPARING));

        assertEquals(CookingStatus.PREPARING, response.status());
        assertEquals(CookingStatus.PREPARING, orderItem.getCookingStatus());
        verify(messagingTemplate).convertAndSend(eq("/topic/kitchen/tickets"), any(KitchenTicketEvent.class));
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/kitchen/completed"), any(KitchenCompletedMessage.class));
    }

    @Test
    @DisplayName("updateStatus - ném lỗi 409 khi chuyển ngược PREPARING sang QUEUED")
    void updateStatus_Backwards_ThrowsConflict() {
        when(kitchenTicketRepository.findById("ticket-1")).thenReturn(Optional.of(ticket(CookingStatus.PREPARING)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> kitchenTicketService.updateStatus("ticket-1", new UpdateTicketStatusRequest(CookingStatus.QUEUED)));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Không thể chuyển trạng thái phiếu bếp"));
        verify(kitchenTicketRepository, never()).save(any(KitchenTicket.class));
    }

    @Test
    @DisplayName("updateStatus - ném lỗi 409 khi phiếu đã SERVED (BR-39)")
    void updateStatus_ServedTicket_ThrowsConflict() {
        when(kitchenTicketRepository.findById("ticket-1")).thenReturn(Optional.of(ticket(CookingStatus.SERVED)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> kitchenTicketService.updateStatus("ticket-1", new UpdateTicketStatusRequest(CookingStatus.READY)));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Không thể chuyển trạng thái phiếu bếp"));
        verify(kitchenTicketRepository, never()).save(any(KitchenTicket.class));
    }

    @Test
    @DisplayName("updateStatus - chuyển sang READY thì phát thông báo hoàn thành tới bàn phục vụ")
    void updateStatus_ToReady_PublishesCompletedMessage() {
        KitchenTicket ticket = ticket(CookingStatus.QUEUED);
        Order order = new Order("order-1", "table-1", "staff-1", OrderType.DINE_IN);
        RestaurantTable table = new RestaurantTable("table-1", "zone-1", "Bàn 1", 4);
        table.setStatus(TableStatus.OCCUPIED);
        order.setTable(table);

        when(kitchenTicketRepository.findById("ticket-1")).thenReturn(Optional.of(ticket));
        when(kitchenTicketRepository.save(any(KitchenTicket.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderItemRepository.findById("item-1")).thenReturn(Optional.of(orderItem));
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));

        KitchenTicketResponse response = kitchenTicketService.updateStatus("ticket-1",
            new UpdateTicketStatusRequest(CookingStatus.READY));

        assertEquals(CookingStatus.READY, response.status());
        verify(messagingTemplate).convertAndSend(eq("/topic/kitchen/tickets"), any(KitchenTicketEvent.class));
        verify(messagingTemplate).convertAndSend(eq("/topic/kitchen/completed"), any(KitchenCompletedMessage.class));

        ArgumentCaptor<KitchenCompletedMessage> captor = ArgumentCaptor.forClass(KitchenCompletedMessage.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/kitchen/completed"), captor.capture());
        assertEquals("order-1", captor.getValue().orderId());
        assertEquals("item-1", captor.getValue().orderItemId());
        assertEquals("table-1", captor.getValue().tableId());
        assertEquals("Bàn 1", captor.getValue().tableNumber());
        assertEquals("Phở Bò", captor.getValue().itemName());
        assertEquals(CookingStatus.READY, captor.getValue().status());
    }

    @Test
    @DisplayName("updateStatus - chuyển sang SERVED thì ghi completedAt và đồng bộ trạng thái món")
    void updateStatus_ToServed_SetsCompletedAtAndMirrorsItem() {
        KitchenTicket ticket = ticket(CookingStatus.READY);
        when(kitchenTicketRepository.findById("ticket-1")).thenReturn(Optional.of(ticket));
        when(kitchenTicketRepository.save(any(KitchenTicket.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderItemRepository.findById("item-1")).thenReturn(Optional.of(orderItem));
        when(orderRepository.findById("order-1")).thenReturn(Optional.empty());

        KitchenTicketResponse response = kitchenTicketService.updateStatus("ticket-1",
            new UpdateTicketStatusRequest(CookingStatus.SERVED));

        assertEquals(CookingStatus.SERVED, response.status());
        assertNotNull(response.completedAt());
        assertNotNull(ticket.getCompletedAt());
        assertEquals(CookingStatus.SERVED, orderItem.getCookingStatus());
        verify(orderItemRepository).save(orderItem);
        verify(messagingTemplate).convertAndSend(eq("/topic/kitchen/completed"), any(KitchenCompletedMessage.class));
    }
}
