package com.huongViet.order;

/**
 * Sự kiện WebSocket gửi tới KDS khi phiếu bếp được tạo hoặc cập nhật.
 * {@code action} là {@code "CREATED"} hoặc {@code "UPDATED"}.
 */
public record KitchenTicketEvent(
    String action,
    KitchenTicketResponse ticket
) {}
