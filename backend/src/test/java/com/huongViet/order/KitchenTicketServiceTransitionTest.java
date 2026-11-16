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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kiểm thử máy trạng thái của hàng đợi bếp KDS (BR-34, BR-37, BR-39).
 */
@ExtendWith(MockitoExtension.class)
class KitchenTicketServiceTransitionTest {

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
            kitchenTicketRepository, orderItemRepository, orderRepository, messagingTemplate);

        orderItem = new OrderItem("item-1", "order-1", "menu-1", 2,
            new BigDecimal("55000"), "Ít cay");
        orderItem.setCookingStatus(CookingStatus.QUEUED);
    }

    private KitchenTicket ticket(CookingStatus status) {
        KitchenTicket ticket = new KitchenTicket("ticket-1", "item-1", "order-1",
            "Bàn 1", "Phở Bò", 2, "Ít cay");
        ticket.setStatus(status);
        return ticket;
    }

    private void stubUpdate(KitchenTicket ticket) {
        when(kitchenTicketRepository.findById("ticket-1")).thenReturn(Optional.of(ticket));
        when(kitchenTicketRepository.save(any(KitchenTicket.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(orderItemRepository.findById("item-1")).thenReturn(Optional.of(orderItem));
    }

    @Test
    @DisplayName("QUEUED → READY: bếp được báo hoàn tất trực tiếp, chưa ghi completedAt")
    void queuedToReady_IsAllowed() {
        KitchenTicket ticket = ticket(CookingStatus.QUEUED);
        stubUpdate(ticket);
        when(orderRepository.findById("order-1")).thenReturn(Optional.empty());

        KitchenTicketResponse response = kitchenTicketService.updateStatus(
            "ticket-1", new UpdateTicketStatusRequest(CookingStatus.READY));

        assertEquals(CookingStatus.READY, response.status());
        assertNull(response.completedAt());
        assertNull(ticket.getCompletedAt());
        assertEquals(CookingStatus.READY, orderItem.getCookingStatus());
        verify(orderItemRepository).save(orderItem);
        verify(messagingTemplate).convertAndSend(eq("/topic/kitchen/completed"),
            any(KitchenCompletedMessage.class));
    }

    @Test
    @DisplayName("PREPARING → READY: chuyển tiếp hợp lệ")
    void preparingToReady_IsAllowed() {
        KitchenTicket ticket = ticket(CookingStatus.PREPARING);
        orderItem.setCookingStatus(CookingStatus.PREPARING);
        stubUpdate(ticket);

        KitchenTicketResponse response = kitchenTicketService.updateStatus(
            "ticket-1", new UpdateTicketStatusRequest(CookingStatus.READY));

        assertEquals(CookingStatus.READY, response.status());
        assertEquals(CookingStatus.READY, orderItem.getCookingStatus());
    }

    @Test
    @DisplayName("READY → SERVED: ghi completedAt và đồng bộ món")
    void readyToServed_IsAllowedAndStampsCompletion() {
        KitchenTicket ticket = ticket(CookingStatus.READY);
        orderItem.setCookingStatus(CookingStatus.READY);
        stubUpdate(ticket);

        KitchenTicketResponse response = kitchenTicketService.updateStatus(
            "ticket-1", new UpdateTicketStatusRequest(CookingStatus.SERVED));

        assertEquals(CookingStatus.SERVED, response.status());
        assertEquals(CookingStatus.SERVED, orderItem.getCookingStatus());
        assertTrue(response.completedAt() != null && !response.completedAt().isBlank());
        assertTrue(ticket.getCompletedAt() != null);
    }

    @Test
    @DisplayName("CANCELLED không thể chuyển sang bất kỳ trạng thái nào")
    void cancelledTicket_CannotTransition() {
        when(kitchenTicketRepository.findById("ticket-1"))
            .thenReturn(Optional.of(ticket(CookingStatus.CANCELLED)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> kitchenTicketService.updateStatus("ticket-1",
                new UpdateTicketStatusRequest(CookingStatus.QUEUED)));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(kitchenTicketRepository, never()).save(any(KitchenTicket.class));
        verify(orderItemRepository, never()).save(any(OrderItem.class));
    }

    @Test
    @DisplayName("READY không thể quay lại QUEUED")
    void readyToQueued_IsRejected() {
        when(kitchenTicketRepository.findById("ticket-1"))
            .thenReturn(Optional.of(ticket(CookingStatus.READY)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> kitchenTicketService.updateStatus("ticket-1",
                new UpdateTicketStatusRequest(CookingStatus.QUEUED)));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    @DisplayName("SERVED là trạng thái cuối, không thể hủy")
    void servedToCancelled_IsRejected() {
        when(kitchenTicketRepository.findById("ticket-1"))
            .thenReturn(Optional.of(ticket(CookingStatus.SERVED)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> kitchenTicketService.updateStatus("ticket-1",
                new UpdateTicketStatusRequest(CookingStatus.CANCELLED)));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    @DisplayName("Không thể chuyển một phiếu sang chính trạng thái hiện tại")
    void sameStatus_IsRejected() {
        when(kitchenTicketRepository.findById("ticket-1"))
            .thenReturn(Optional.of(ticket(CookingStatus.QUEUED)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> kitchenTicketService.updateStatus("ticket-1",
                new UpdateTicketStatusRequest(CookingStatus.QUEUED)));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    @DisplayName("Dòng món đã bị xóa vẫn cập nhật được phiếu bếp")
    void orderItemMissing_StillUpdatesTicket() {
        KitchenTicket ticket = ticket(CookingStatus.QUEUED);
        when(kitchenTicketRepository.findById("ticket-1")).thenReturn(Optional.of(ticket));
        when(kitchenTicketRepository.save(any(KitchenTicket.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(orderItemRepository.findById("item-1")).thenReturn(Optional.empty());

        KitchenTicketResponse response = kitchenTicketService.updateStatus(
            "ticket-1", new UpdateTicketStatusRequest(CookingStatus.PREPARING));

        assertEquals(CookingStatus.PREPARING, response.status());
        verify(orderItemRepository, never()).save(any(OrderItem.class));
        verify(messagingTemplate).convertAndSend(eq("/topic/kitchen/tickets"),
            any(KitchenTicketEvent.class));
    }

    @Test
    @DisplayName("Thông báo hoàn thành dùng số bàn snapshot khi đơn không còn tồn tại")
    void completedMessage_FallsBackToTicketTableNumber() {
        KitchenTicket ticket = ticket(CookingStatus.QUEUED);
        stubUpdate(ticket);
        when(orderRepository.findById("order-1")).thenReturn(Optional.empty());

        kitchenTicketService.updateStatus("ticket-1",
            new UpdateTicketStatusRequest(CookingStatus.READY));

        ArgumentCaptor<KitchenCompletedMessage> captor =
            ArgumentCaptor.forClass(KitchenCompletedMessage.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/kitchen/completed"), captor.capture());
        assertNull(captor.getValue().tableId());
        assertEquals("Bàn 1", captor.getValue().tableNumber());
        assertEquals("Phở Bò", captor.getValue().itemName());
        assertEquals(2, captor.getValue().quantity());
    }

    @Test
    @DisplayName("Thông báo hoàn thành ưu tiên số bàn hiện tại của đơn")
    void completedMessage_UsesOrderTableNumber() {
        KitchenTicket ticket = ticket(CookingStatus.QUEUED);
        stubUpdate(ticket);

        Order order = new Order("order-1", "table-1", "staff-1", OrderType.DINE_IN);
        RestaurantTable table = new RestaurantTable("table-1", "zone-1", "Bàn VIP", 8);
        table.setStatus(TableStatus.OCCUPIED);
        order.setTable(table);
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));

        kitchenTicketService.updateStatus("ticket-1",
            new UpdateTicketStatusRequest(CookingStatus.READY));

        ArgumentCaptor<KitchenCompletedMessage> captor =
            ArgumentCaptor.forClass(KitchenCompletedMessage.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/kitchen/completed"), captor.capture());
        assertEquals("table-1", captor.getValue().tableId());
        assertEquals("Bàn VIP", captor.getValue().tableNumber());
    }

    @Test
    @DisplayName("Lỗi WebSocket không làm hỏng việc cập nhật trạng thái")
    void webSocketFailure_DoesNotBreakUpdate() {
        KitchenTicket ticket = ticket(CookingStatus.QUEUED);
        stubUpdate(ticket);
        doThrow(new RuntimeException("broker down"))
            .when(messagingTemplate).convertAndSend(eq("/topic/kitchen/tickets"),
                any(KitchenTicketEvent.class));

        KitchenTicketResponse response = kitchenTicketService.updateStatus(
            "ticket-1", new UpdateTicketStatusRequest(CookingStatus.PREPARING));

        assertEquals(CookingStatus.PREPARING, response.status());
    }

    @Test
    @DisplayName("Repository trả về null sau khi lưu vẫn dùng entity đã cập nhật")
    void saveReturnsNull_StillReturnsUpdatedTicket() {
        KitchenTicket ticket = ticket(CookingStatus.QUEUED);
        when(kitchenTicketRepository.findById("ticket-1")).thenReturn(Optional.of(ticket));
        when(kitchenTicketRepository.save(any(KitchenTicket.class))).thenReturn(null);
        when(orderItemRepository.findById("item-1")).thenReturn(Optional.of(orderItem));

        KitchenTicketResponse response = kitchenTicketService.updateStatus(
            "ticket-1", new UpdateTicketStatusRequest(CookingStatus.PREPARING));

        assertEquals(CookingStatus.PREPARING, response.status());
    }

    @Test
    @DisplayName("Bếp chỉ đổi trạng thái, không được sửa nội dung món của đơn")
    void updateStatus_DoesNotMutateOrderItemContent() {
        KitchenTicket ticket = ticket(CookingStatus.QUEUED);
        stubUpdate(ticket);

        kitchenTicketService.updateStatus("ticket-1",
            new UpdateTicketStatusRequest(CookingStatus.PREPARING));

        assertEquals(2, orderItem.getQuantity());
        assertEquals("Ít cay", orderItem.getNote());
        assertEquals(new BigDecimal("55000"), orderItem.getUnitPriceSnapshot());
    }

    @Test
    @DisplayName("listAll - hàng đợi rỗng trả về danh sách rỗng")
    void listAll_EmptyQueue_ReturnsEmptyList() {
        when(kitchenTicketRepository.findAllByOrderByCreatedAtAsc()).thenReturn(List.of());

        List<KitchenTicketResponse> result = kitchenTicketService.listAll(null);

        assertTrue(result.isEmpty());
    }
}
