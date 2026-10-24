package com.huongViet.order;

import java.util.List;

/**
 * Đơn hàng kèm danh sách món.
 */
public record OrderResponse(
    String id,
    String tableId,
    String tableNumber,
    String staffId,
    OrderType orderType,
    OrderStatus status,
    String createdAt,
    String closedAt,
    List<OrderItemResponse> items
) {
    public static OrderResponse from(Order order, List<OrderItem> items) {
        String tableNumber = (order.getTable() != null) ? order.getTable().getTableNumber() : null;
        List<OrderItemResponse> itemResponses = items != null
            ? items.stream().map(OrderItemResponse::from).toList()
            : List.of();

        return new OrderResponse(
            order.getId(),
            order.getTableId(),
            tableNumber,
            order.getStaffId(),
            order.getOrderType(),
            order.getStatus(),
            order.getCreatedAt() != null ? order.getCreatedAt().toString() : null,
            order.getClosedAt() != null ? order.getClosedAt().toString() : null,
            itemResponses
        );
    }
}
