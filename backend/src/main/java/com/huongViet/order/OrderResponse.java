package com.huongViet.order;

import java.math.BigDecimal;
import java.util.List;

/**
 * Đơn hàng kèm danh sách món.
 *
 * <p>{@code subtotal} là tiền của các món <b>chưa huỷ</b> — món đã huỷ (CANCELLED)
 * không bao giờ được tính vào hoá đơn (BR-41); {@code cancelledAmount} giữ lại
 * phần tiền đã bỏ ra để đối soát.</p>
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
    BigDecimal subtotal,
    BigDecimal cancelledAmount,
    List<OrderItemResponse> items
) {
    public static OrderResponse from(Order order, List<OrderItem> items) {
        String tableNumber = (order.getTable() != null) ? order.getTable().getTableNumber() : null;
        List<OrderItemResponse> itemResponses = items != null
            ? items.stream().map(OrderItemResponse::from).toList()
            : List.of();

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal cancelledAmount = BigDecimal.ZERO;
        for (OrderItemResponse item : itemResponses) {
            if (item.cookingStatus() == CookingStatus.CANCELLED) {
                cancelledAmount = cancelledAmount.add(item.lineTotal());
            } else {
                subtotal = subtotal.add(item.lineTotal());
            }
        }

        return new OrderResponse(
            order.getId(),
            order.getTableId(),
            tableNumber,
            order.getStaffId(),
            order.getOrderType(),
            order.getStatus(),
            order.getCreatedAt() != null ? order.getCreatedAt().toString() : null,
            order.getClosedAt() != null ? order.getClosedAt().toString() : null,
            subtotal,
            cancelledAmount,
            itemResponses
        );
    }
}
