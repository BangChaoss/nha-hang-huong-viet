package com.huongViet.order;

/**
 * Thông báo gửi tới nhân viên phục vụ khi món đã sẵn sàng (READY) hoặc đã phục vụ (SERVED).
 */
public record KitchenCompletedMessage(
    String orderId,
    String orderItemId,
    String tableId,
    String tableNumber,
    String itemName,
    Integer quantity,
    CookingStatus status
) {}
