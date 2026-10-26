package com.huongViet.reservation.dto;

import java.util.List;

public record ZoneAvailabilityResponse(
    String zoneId,
    String zoneName,
    Boolean isVip,
    int totalCapacity,
    List<TimeSlotAvailability> timeSlots
) {}
