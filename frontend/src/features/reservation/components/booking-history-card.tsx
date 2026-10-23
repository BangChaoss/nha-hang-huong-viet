import * as React from "react";
import { Calendar, Clock, Users, MapPin, Sparkles, Phone, MessageSquare } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import { RESERVATION_STATUS_CONFIG, type ReservationResponse } from "../types/reservation.types";

interface BookingHistoryCardProps {
  booking: ReservationResponse;
}

export function BookingHistoryCard({ booking }: BookingHistoryCardProps) {
  const statusCfg =
    RESERVATION_STATUS_CONFIG[booking.status] || RESERVATION_STATUS_CONFIG.PENDING_HOLD;
  const timeFormatted = booking.reservationTime ? booking.reservationTime.substring(0, 5) : "";
  const code = booking.id ? booking.id.substring(0, 8).toUpperCase() : "";

  return (
    <Card className="overflow-hidden border transition-all hover:shadow-sm">
      <CardContent className="p-5">
        <div className="flex flex-wrap items-center justify-between gap-2 border-b pb-3">
          <div className="flex items-center gap-2">
            <span className="font-mono text-xs font-bold text-primary">HV-{code}</span>
            <span className="text-xs text-muted-foreground">·</span>
            <span className="text-xs text-muted-foreground">{booking.guestName}</span>
          </div>
          <Badge variant="outline" className={statusCfg.badgeCls}>
            {statusCfg.label}
          </Badge>
        </div>

        <div className="mt-4 grid grid-cols-2 sm:grid-cols-4 gap-3 text-xs">
          <div className="flex items-center gap-2 text-foreground">
            <Calendar className="h-4 w-4 text-muted-foreground shrink-0" />
            <span>{booking.reservationDate}</span>
          </div>

          <div className="flex items-center gap-2 text-foreground">
            <Clock className="h-4 w-4 text-muted-foreground shrink-0" />
            <span>{timeFormatted}</span>
          </div>

          <div className="flex items-center gap-2 text-foreground">
            <Users className="h-4 w-4 text-muted-foreground shrink-0" />
            <span>{booking.partySize} khách</span>
          </div>

          <div className="flex items-center gap-2 text-foreground">
            <MapPin className="h-4 w-4 text-muted-foreground shrink-0" />
            <span className="truncate">
              {booking.zoneName || "Khu vực chung"}
              {booking.zoneIsVip && " ⭐"}
            </span>
          </div>
        </div>

        {booking.tableNumber && (
          <div className="mt-3 inline-flex items-center gap-1.5 rounded-md bg-secondary/50 px-2.5 py-1 text-xs font-medium text-secondary-foreground">
            <Sparkles className="h-3.5 w-3.5 text-primary" />
            <span>
              Bàn số: <strong>{booking.tableNumber}</strong>
            </span>
          </div>
        )}

        {booking.specialRequests && (
          <div className="mt-3 flex items-start gap-2 rounded-md bg-muted/40 p-2 text-xs text-muted-foreground">
            <MessageSquare className="h-3.5 w-3.5 shrink-0 mt-0.5 text-muted-foreground" />
            <span className="line-clamp-2">Ghi chú: {booking.specialRequests}</span>
          </div>
        )}

        <div className="mt-3 flex items-center justify-between border-t pt-3 text-[11px] text-muted-foreground">
          <span className="flex items-center gap-1">
            <Phone className="h-3 w-3" /> {booking.guestPhone}
          </span>
          {booking.createdAt && (
            <span>Tạo lúc: {new Date(booking.createdAt).toLocaleDateString("vi-VN")}</span>
          )}
        </div>
      </CardContent>
    </Card>
  );
}
