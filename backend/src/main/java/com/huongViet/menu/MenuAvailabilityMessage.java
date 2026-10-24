package com.huongViet.menu;

/**
 * WebSocket payload published to {@code /topic/menu/availability}
 * whenever the availability of a dish changes (STT 76 / BR-50).
 */
public record MenuAvailabilityMessage(
    String menuItemId,
    String itemName,
    Boolean isAvailable
) {}
