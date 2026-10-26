package com.huongViet.reservation;

/**
 * Reservation lifecycle status enum.
 */
public enum ReservationStatus {
    PENDING_HOLD,       // Initial status upon submission (UC08)
    PENDING_DEPOSIT,    // Future VIP / large party deposit awaiting
    CONFIRMED,          // Staff confirmed and assigned table (UC09)
    ARRIVED,            // Customer checked in at the restaurant
    CANCELLED,          // Cancelled by customer or staff
    NO_SHOW             // Customer failed to arrive
}
