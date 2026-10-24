package com.huongViet.order;

import java.math.BigDecimal;

/**
 * Chi tiết một dòng món trong đơn hàng.
 */
public record OrderItemResponse(
    String id,
    String menuItemId,
    String itemName,
    Integer quantity,
    BigDecimal unitPrice,
    BigDecimal lineTotal,
    String note,
    CookingStatus cookingStatus,
    String cancelReason,
    String createdAt
) {
    public static OrderItemResponse from(OrderItem item) {
        String itemName = (item.getMenuItem() != null) ? item.getMenuItem().getItemName() : null;
        BigDecimal unitPrice = item.getUnitPriceSnapshot() != null
            ? item.getUnitPriceSnapshot()
            : BigDecimal.ZERO;
        int quantity = item.getQuantity() != null ? item.getQuantity() : 0;

        return new OrderItemResponse(
            item.getId(),
            item.getMenuItemId(),
            itemName,
            item.getQuantity(),
            unitPrice,
            unitPrice.multiply(BigDecimal.valueOf(quantity)),
            item.getNote(),
            item.getCookingStatus(),
            item.getCancelReason(),
            item.getCreatedAt() != null ? item.getCreatedAt().toString() : null
        );
    }
}
