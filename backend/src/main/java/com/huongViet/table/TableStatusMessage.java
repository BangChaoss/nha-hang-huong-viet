package com.huongViet.table;

public record TableStatusMessage(
    String tableId,
    String tableNumber,
    TableStatus oldStatus,
    TableStatus newStatus,
    String zoneName
) {}
