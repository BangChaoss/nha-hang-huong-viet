package com.huongViet.order;

/**
 * Cooking state of a single dish inside an order.
 * Shared between {@code order_items.cooking_status} and {@code kitchen_tickets.status}.
 */
public enum CookingStatus {
    QUEUED,
    PREPARING,
    READY,
    SERVED,
    CANCELLED
}
