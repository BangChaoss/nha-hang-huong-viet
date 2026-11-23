package com.huongViet.table;

public record FloorPlanLayoutResponse(
    String id,
    String tableId,
    Double positionX,
    Double positionY,
    Double width,
    Double height,
    Double rotation,
    FloorPlanShape shape,
    String tableNumber,
    Integer capacity,
    TableStatus status,
    String zoneId,
    String zoneName,
    Boolean zoneIsVip,
    Integer occupiedSeats
) {
    public static FloorPlanLayoutResponse from(FloorPlanLayout layout, RestaurantTable table) {
        return from(layout, table, table.getStatus(), 0);
    }

    public static FloorPlanLayoutResponse from(FloorPlanLayout layout, RestaurantTable table, TableStatus dynamicStatus) {
        return from(layout, table, dynamicStatus, 0);
    }

    public static FloorPlanLayoutResponse from(FloorPlanLayout layout, RestaurantTable table, TableStatus dynamicStatus, Integer occupiedSeats) {
        TableZone zone = table.getZone();
        return new FloorPlanLayoutResponse(
            layout.getId(),
            layout.getTableId(),
            layout.getPositionX(),
            layout.getPositionY(),
            layout.getWidth(),
            layout.getHeight(),
            layout.getRotation(),
            layout.getShape(),
            table.getTableNumber(),
            table.getCapacity(),
            dynamicStatus != null ? dynamicStatus : table.getStatus(),
            table.getZoneId(),
            zone != null ? zone.getZoneName() : null,
            zone != null ? zone.getIsVip() : null,
            occupiedSeats != null ? occupiedSeats : 0
        );
    }
}
