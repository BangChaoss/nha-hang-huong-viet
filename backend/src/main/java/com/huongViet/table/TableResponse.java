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
        TableZone zone = table.getZone();
        return new TableResponse(
            table.getId(),
            table.getTableNumber(),
            table.getCapacity(),
            table.getStatus(),
            table.getZoneId(),
            zone != null ? zone.getZoneName() : null,
            zone != null ? zone.getIsVip() : null
        );
    }
}
