package com.huongViet.order;

/**
 * Phiếu bếp hiển thị trên màn hình KDS.
 */
public record KitchenTicketResponse(
    String id,
    String orderId,
    String orderItemId,
    String tableNumber,
    String itemName,
    Integer quantity,
    String note,
    CookingStatus status,
    String createdAt,
    String completedAt
) {
    public static KitchenTicketResponse from(KitchenTicket ticket) {
        return new KitchenTicketResponse(
            ticket.getId(),
            ticket.getOrderId(),
            ticket.getOrderItemId(),
            ticket.getTableNumber(),
            ticket.getItemName(),
            ticket.getQuantity(),
            ticket.getNote(),
            ticket.getStatus(),
            ticket.getCreatedAt() != null ? ticket.getCreatedAt().toString() : null,
            ticket.getCompletedAt() != null ? ticket.getCompletedAt().toString() : null
        );
    }
}
