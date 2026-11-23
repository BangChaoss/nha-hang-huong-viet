package com.huongViet.table;

public record TableStatusMessage(
    String tableId,
    String tableNumber,
    TableStatus oldStatus,
    TableStatus newStatus,
    String zoneName,
    Integer occupiedSeats
) {
    public TableStatusMessage(String tableId, String tableNumber, TableStatus oldStatus, TableStatus newStatus, String zoneName) {
        this(tableId, tableNumber, oldStatus, newStatus, zoneName, null);
    }
}
