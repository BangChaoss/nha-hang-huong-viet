package com.huongViet.reservation.dto;

import java.time.LocalTime;

public record TimeSlotAvailability(
    LocalTime time,
    String label,
    int confirmedSeats,
    int remainingCapacity,
    boolean available
) {}
