package com.huongViet.table;

public record TableResponse(
    String id,
    String tableNumber,
    Integer capacity,
    TableStatus status,
    String zoneId,
    String zoneName,
    Boolean zoneIsVip
) {
    public static TableResponse from(RestaurantTable table) {
        return from(table, table.getStatus());
    }

    public static TableResponse from(RestaurantTable table, TableStatus dynamicStatus) {
        TableZone zone = table.getZone();
        return new TableResponse(
            table.getId(),
            table.getTableNumber(),
            table.getCapacity(),
            dynamicStatus != null ? dynamicStatus : table.getStatus(),
            table.getZoneId(),
            zone != null ? zone.getZoneName() : null,
            zone != null ? zone.getIsVip() : null
        );
    }
}
