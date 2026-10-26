package com.huongViet.table;

public record ZoneResponse(
    String id,
    String zoneName,
    Boolean isVip,
    long tableCount
) {
    public static ZoneResponse from(TableZone zone, long tableCount) {
        return new ZoneResponse(
            zone.getId(),
            zone.getZoneName(),
            zone.getIsVip(),
            tableCount
        );
    }
}
