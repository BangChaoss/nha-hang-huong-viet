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
    String mergedTableIds,
    BigDecimal depositAmount,
    String cancellationReason,
    LocalDateTime createdAt
) {
    // Backward-compatible constructor for existing tests
    public ReservationResponse(
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
        String cancellationReason,
        LocalDateTime createdAt
    ) {
        this(id, guestName, guestPhone, guestEmail, partySize, reservationDate, reservationTime,
             specialRequests, status, zoneId, zoneName, zoneIsVip, tableId, tableNumber, null,
             depositAmount, cancellationReason, createdAt);
    }

    public static ReservationResponse from(Reservation reservation) {
        return from(reservation, null, null, null, null);
    }

    public static ReservationResponse from(Reservation reservation, String fallbackZoneId, String fallbackZoneName, Boolean fallbackZoneIsVip) {
        return from(reservation, fallbackZoneId, fallbackZoneName, fallbackZoneIsVip, null);
    }

    public static ReservationResponse from(Reservation reservation, String fallbackZoneId, String fallbackZoneName, Boolean fallbackZoneIsVip, String customTableNumber) {
        String zoneId = reservation.getZoneId() != null ? reservation.getZoneId() : fallbackZoneId;
        String zoneName = fallbackZoneName;
        Boolean zoneIsVip = fallbackZoneIsVip;
        String tableId = reservation.getTableId();
        String tableNumber = customTableNumber;

        if (reservation.getZone() != null) {
            zoneId = reservation.getZone().getId();
            zoneName = reservation.getZone().getZoneName();
            zoneIsVip = reservation.getZone().getIsVip();
        }

        RestaurantTable table = reservation.getTable();
        if (table != null) {
            if (tableId == null) {
                tableId = table.getId();
            }
            if (tableNumber == null) {
                tableNumber = table.getTableNumber();
            }
            if (table.getZone() != null) {
                zoneId = table.getZoneId();
                zoneName = table.getZone().getZoneName();
                zoneIsVip = table.getZone().getIsVip();
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
            reservation.getMergedTableIds(),
            reservation.getDepositAmount(),
            reservation.getCancellationReason(),
            reservation.getCreatedAt()
        );
    }
}
