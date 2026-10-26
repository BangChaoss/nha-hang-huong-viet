package com.huongViet.menu;

/**
 * WebSocket payload published to {@code /topic/menu/refresh} to signal clients
 * that the menu structure (categories / dishes) has changed (BR-55).
 */
public record MenuRefreshMessage(
    String reason
) {}
