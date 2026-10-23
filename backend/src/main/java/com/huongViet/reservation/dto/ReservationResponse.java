package com.huongViet.reservation.dto;

import com.huongViet.reservation.Reservation;
import com.huongViet.reservation.ReservationStatus;
import com.huongViet.table.RestaurantTable;
import com.huongViet.table.TableZone;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record ReservationResponse(
    String id,
    String guestName,
    String guestPhone,
    String guestEmail,
    Integer partySize,
    LocalDate reservationDate,
    LocalTime reservationTime,
    String specialRequests,
    ReservationStatus status,
    String zoneId,
    String zoneName,
    Boolean zoneIsVip,
    String tableId,
    String tableNumber,
    BigDecimal depositAmount,
    LocalDateTime createdAt
) {
    public static ReservationResponse from(Reservation reservation) {
        return from(reservation, null, null, null);
    }

    public static ReservationResponse from(Reservation reservation, String fallbackZoneId, String fallbackZoneName, Boolean fallbackZoneIsVip) {
        String zoneId = fallbackZoneId;
        String zoneName = fallbackZoneName;
        Boolean zoneIsVip = fallbackZoneIsVip;
        String tableId = null;
        String tableNumber = null;

        RestaurantTable table = reservation.getTable();
        if (table != null) {
            tableId = table.getId();
            tableNumber = table.getTableNumber();
            zoneId = table.getZoneId();
            TableZone zone = table.getZone();
            if (zone != null) {
                zoneName = zone.getZoneName();
                zoneIsVip = zone.getIsVip();
            }
        }

        return new ReservationResponse(
            reservation.getId(),
            reservation.getGuestName(),
            reservation.getGuestPhone(),
            reservation.getGuestEmail(),
            reservation.getPartySize(),
            reservation.getReservationDate(),
            reservation.getReservationTime(),
            reservation.getSpecialRequests(),
            reservation.getStatus(),
            zoneId,
            zoneName,
            zoneIsVip,
            tableId,
            tableNumber,
            reservation.getDepositAmount(),
            reservation.getCreatedAt()
        );
    }
}
