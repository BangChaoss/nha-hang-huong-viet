import * as React from "react";
import { CheckCircle2, Phone, Sparkles, UserCheck, XCircle, Clock } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { TableCell, TableRow } from "@/components/ui/table";
import { RESERVATION_STATUS_CONFIG, type ReservationResponse } from "../types/reservation.types";

interface StaffBookingRowProps {
  booking: ReservationResponse;
  onOpenConfirm: (booking: ReservationResponse) => void;
  onCheckIn: (booking: ReservationResponse) => void;
  onCancel: (booking: ReservationResponse) => void;
  isActionPending?: boolean;
}

export function StaffBookingRow({
  booking,
  onOpenConfirm,
  onCheckIn,
  onCancel,
  isActionPending = false,
}: StaffBookingRowProps) {
  const statusCfg =
    RESERVATION_STATUS_CONFIG[booking.status] || RESERVATION_STATUS_CONFIG.PENDING_HOLD;
  const code = booking.id ? booking.id.substring(0, 8).toUpperCase() : "";
  const timeFormatted = booking.reservationTime ? booking.reservationTime.substring(0, 5) : "";
  const isPending = booking.status === "PENDING_HOLD";
  const isConfirmed = booking.status === "CONFIRMED";

  return (
    <TableRow
      className={`transition-colors text-xs ${
        isPending
          ? "bg-amber-500/10 hover:bg-amber-500/15 border-l-4 border-l-amber-500 font-medium"
          : ""
      }`}
    >
      {/* Code */}
      <TableCell className="font-mono font-bold text-primary whitespace-nowrap">
        HV-{code}
      </TableCell>

      {/* Guest Name & Phone */}
      <TableCell>
        <div className="flex flex-col">
          <span className="font-semibold text-foreground">{booking.guestName}</span>
          <span className="inline-flex items-center gap-1 text-[11px] text-muted-foreground">
            <Phone className="h-3 w-3" />
            {booking.guestPhone}
          </span>
        </div>
      </TableCell>

      {/* Party Size */}
      <TableCell className="text-center whitespace-nowrap">
        <span className="rounded-full bg-secondary px-2 py-0.5 font-semibold text-secondary-foreground">
          {booking.partySize} khách
        </span>
      </TableCell>

      {/* Time & Date */}
      <TableCell className="whitespace-nowrap">
        <div className="flex flex-col">
          <span className="flex items-center gap-1 font-semibold text-foreground">
            <Clock className="h-3 w-3 text-muted-foreground" />
            {timeFormatted}
          </span>
          <span className="text-[11px] text-muted-foreground">{booking.reservationDate}</span>
        </div>
      </TableCell>

      {/* Table & Zone */}
      <TableCell>
        <div className="flex flex-col">
          {booking.tableNumber ? (
            <span className="inline-flex items-center gap-1 font-semibold text-foreground">
              <Sparkles className="h-3 w-3 text-primary" />
              Bàn {booking.tableNumber}
            </span>
          ) : (
            <span className="text-muted-foreground italic">Chưa gán bàn</span>
          )}
          <span className="text-[11px] text-muted-foreground">
            {booking.zoneName ?? "Khu chung"}
            {booking.zoneIsVip ? " ⭐" : ""}
          </span>
        </div>
      </TableCell>

      {/* Status Badge & Note */}
      <TableCell>
        <div className="flex flex-col gap-1">
          <Badge variant="outline" className={`w-fit text-[11px] ${statusCfg.badgeCls}`}>
            {statusCfg.label}
          </Badge>
          {booking.cancellationReason && (
            <span
              className="text-[10px] text-muted-foreground line-clamp-1"
              title={booking.cancellationReason}
            >
              Lý do: {booking.cancellationReason}
            </span>
          )}
          {booking.specialRequests && (
            <span
              className="text-[10px] text-muted-foreground/80 line-clamp-1 italic"
              title={booking.specialRequests}
            >
              Ghi chú: {booking.specialRequests}
            </span>
          )}
        </div>
      </TableCell>

      {/* Actions */}
      <TableCell className="text-right whitespace-nowrap">
        <div className="flex items-center justify-end gap-1.5">
          {isPending && (
            <Button
              type="button"
              size="sm"
              variant="default"
              onClick={() => onOpenConfirm(booking)}
              disabled={isActionPending}
              className="h-7 text-xs bg-amber-600 hover:bg-amber-700 text-white"
            >
              <CheckCircle2 className="h-3.5 w-3.5 mr-1" />
              Xác nhận & Gán bàn
            </Button>
          )}

          {isConfirmed && (
            <>
              <Button
                type="button"
                size="sm"
                variant="default"
                onClick={() => onCheckIn(booking)}
                disabled={isActionPending}
                className="h-7 text-xs bg-emerald-600 hover:bg-emerald-700 text-white"
              >
                <UserCheck className="h-3.5 w-3.5 mr-1" />
                Check-in
              </Button>
              <Button
                type="button"
                size="sm"
                variant="ghost"
                onClick={() => onCancel(booking)}
                disabled={isActionPending}
                className="h-7 text-xs text-destructive hover:bg-destructive/10"
              >
                <XCircle className="h-3.5 w-3.5 mr-1" />
                Hủy
              </Button>
            </>
          )}

          {!isPending && !isConfirmed && (
            <span className="text-[11px] text-muted-foreground">—</span>
          )}
        </div>
      </TableCell>
    </TableRow>
  );
}
