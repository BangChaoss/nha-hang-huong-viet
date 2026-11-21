package com.huongViet.table;

public record TableResponse(
    String id,
    String tableNumber,
    Integer capacity,
    TableStatus status,
    String zoneId,
    String zoneName,
    Boolean zoneIsVip,
    Integer availableSeats
) {
    public TableResponse(String id, String tableNumber, Integer capacity, TableStatus status,
                         String zoneId, String zoneName, Boolean zoneIsVip) {
        this(id, tableNumber, capacity, status, zoneId, zoneName, zoneIsVip, null);
    }
    public static TableResponse from(RestaurantTable table) {
        return from(table, table.getStatus());
    }

    public static TableResponse from(RestaurantTable table, TableStatus dynamicStatus) {
        return from(table, dynamicStatus, null);
    }

    public static TableResponse from(RestaurantTable table, TableStatus dynamicStatus, Integer availableSeats) {
        TableZone zone = table.getZone();
        return new TableResponse(
            table.getId(),
            table.getTableNumber(),
            table.getCapacity(),
            dynamicStatus != null ? dynamicStatus : table.getStatus(),
            table.getZoneId(),
            zone != null ? zone.getZoneName() : null,
            zone != null ? zone.getIsVip() : null,
            availableSeats
        );
    }
}
