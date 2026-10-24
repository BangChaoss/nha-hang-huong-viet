package com.huongViet.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Màn hình bếp (KDS) — quản lý hàng đợi phiếu bếp.
 * BR-37: bếp chỉ được cập nhật trạng thái phiếu, không được thay đổi nội dung đơn.
 */
@Service
@Transactional(readOnly = true)
public class KitchenTicketService {

    private static final Logger logger = LoggerFactory.getLogger(KitchenTicketService.class);

    private static final String KITCHEN_TICKETS_TOPIC = "/topic/kitchen/tickets";
    private static final String KITCHEN_COMPLETED_TOPIC = "/topic/kitchen/completed";

    /**
     * BR-39: chỉ chuyển tiếp trạng thái theo đúng trình tự nghiệp vụ.
     */
    private static final Map<CookingStatus, Set<CookingStatus>> ALLOWED_TRANSITIONS = Map.of(
        CookingStatus.QUEUED, Set.of(CookingStatus.PREPARING, CookingStatus.READY),
        CookingStatus.PREPARING, Set.of(CookingStatus.READY),
        CookingStatus.READY, Set.of(CookingStatus.SERVED),
        CookingStatus.SERVED, Set.of(),
        CookingStatus.CANCELLED, Set.of()
    );

    private final KitchenTicketRepository kitchenTicketRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderRepository orderRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public KitchenTicketService(KitchenTicketRepository kitchenTicketRepository,
                                 OrderItemRepository orderItemRepository,
                                 OrderRepository orderRepository,
                                 SimpMessagingTemplate messagingTemplate) {
        this.kitchenTicketRepository = kitchenTicketRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderRepository = orderRepository;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * BR-34/BR-35: hàng đợi KDS luôn được sắp xếp FIFO theo thời điểm tạo.
     */
    public List<KitchenTicketResponse> listAll(CookingStatus status) {
        List<KitchenTicket> tickets = (status != null)
            ? kitchenTicketRepository.findByStatusOrderByCreatedAtAsc(status)
            : kitchenTicketRepository.findAllByOrderByCreatedAtAsc();

        return tickets.stream().map(KitchenTicketResponse::from).toList();
    }

    /**
     * BR-37/BR-39: cập nhật trạng thái phiếu bếp.
     */
    @Transactional
    public KitchenTicketResponse updateStatus(String id, UpdateTicketStatusRequest request) {
        KitchenTicket ticket = kitchenTicketRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phiếu bếp."));

        CookingStatus oldStatus = ticket.getStatus();
        CookingStatus newStatus = request.status();

        Set<CookingStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(oldStatus, Set.of());
        if (!allowed.contains(newStatus)) {
            logger.warn("BR-39 violation blocked: ticket {} cannot move from {} to {}",
                ticket.getId(), oldStatus, newStatus);
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Không thể chuyển trạng thái phiếu bếp từ " + oldStatus + " sang " + newStatus + ".");
        }

        ticket.setStatus(newStatus);
        if (newStatus == CookingStatus.SERVED) {
            ticket.setCompletedAt(LocalDateTime.now());
        }
        KitchenTicket savedTicket = kitchenTicketRepository.save(ticket);
        if (savedTicket == null) {
            savedTicket = ticket;
        }

        // Đồng bộ trạng thái nấu xuống dòng món của đơn
        orderItemRepository.findById(savedTicket.getOrderItemId()).ifPresent(orderItem -> {
            orderItem.setCookingStatus(newStatus);
            orderItemRepository.save(orderItem);
        });

        logger.info("Updated kitchen ticket {} status from {} to {}", savedTicket.getId(), oldStatus, newStatus);

        publishTicketEvent(new KitchenTicketEvent("UPDATED", KitchenTicketResponse.from(savedTicket)));

        if (newStatus == CookingStatus.READY || newStatus == CookingStatus.SERVED) {
            publishCompletedMessage(buildCompletedMessage(savedTicket, newStatus));
        }

        return KitchenTicketResponse.from(savedTicket);
    }

    private KitchenCompletedMessage buildCompletedMessage(KitchenTicket ticket, CookingStatus status) {
        Order order = (ticket.getOrderId() != null)
            ? orderRepository.findById(ticket.getOrderId()).orElse(null)
            : null;

        String tableId = (order != null) ? order.getTableId() : null;
        String tableNumber = ticket.getTableNumber();
        if (order != null && order.getTable() != null && order.getTable().getTableNumber() != null) {
            tableNumber = order.getTable().getTableNumber();
        }

        return new KitchenCompletedMessage(
            ticket.getOrderId(),
            ticket.getOrderItemId(),
            tableId,
            tableNumber,
            ticket.getItemName(),
            ticket.getQuantity(),
            status
        );
    }

    private void publishTicketEvent(KitchenTicketEvent event) {
        try {
            messagingTemplate.convertAndSend(KITCHEN_TICKETS_TOPIC, event);
            logger.debug("Broadcasted kitchen ticket event: {}", event);
        } catch (Exception ex) {
            logger.error("Failed to broadcast kitchen ticket event via WebSocket: ", ex);
        }
    }

    private void publishCompletedMessage(KitchenCompletedMessage message) {
        try {
            messagingTemplate.convertAndSend(KITCHEN_COMPLETED_TOPIC, message);
            logger.debug("Broadcasted kitchen completed message: {}", message);
        } catch (Exception ex) {
            logger.error("Failed to broadcast kitchen completed message via WebSocket: ", ex);
        }
    }
}
