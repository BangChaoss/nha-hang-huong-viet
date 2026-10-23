import * as React from "react";
import {
  Calendar,
  Clock,
  Users,
  MapPin,
  Sparkles,
  Phone,
  MessageSquare,
  XCircle,
  Star,
  AlertCircle,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import { RESERVATION_STATUS_CONFIG, type ReservationResponse } from "../types/reservation.types";

interface BookingHistoryCardProps {
  booking: ReservationResponse;
  onCancelClick?: (booking: ReservationResponse) => void;
}

export function BookingHistoryCard({ booking, onCancelClick }: BookingHistoryCardProps) {
  const statusCfg =
    RESERVATION_STATUS_CONFIG[booking.status] || RESERVATION_STATUS_CONFIG.PENDING_HOLD;
  const timeFormatted = booking.reservationTime ? booking.reservationTime.substring(0, 5) : "";
  const code = booking.id ? booking.id.substring(0, 8).toUpperCase() : "";

  // BR-23: E1 — 60-min rule for CONFIRMED bookings
  const isWithin60Min = React.useMemo(() => {
    if (booking.status !== "CONFIRMED") return false;
    try {
      const target = new Date(`${booking.reservationDate}T${booking.reservationTime}`);
      const diffMinutes = (target.getTime() - Date.now()) / (1000 * 60);
      return diffMinutes < 60;
    } catch {
      return false;
    }
  }, [booking.reservationDate, booking.reservationTime, booking.status]);

  const canCancel =
    (booking.status === "PENDING_HOLD" || booking.status === "CONFIRMED") &&
    Boolean(onCancelClick);

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

        {/* Cancellation reason if cancelled */}
        {booking.status === "CANCELLED" && booking.cancellationReason && (
          <div className="mt-3 flex items-start gap-2 rounded-md bg-rose-500/10 border border-rose-500/20 p-2 text-xs text-rose-700 dark:text-rose-400">
            <AlertCircle className="h-3.5 w-3.5 shrink-0 mt-0.5" />
            <span>Lý do hủy: {booking.cancellationReason}</span>
          </div>
        )}

        {/* Footer Actions & Info */}
        <div className="mt-4 flex flex-wrap items-center justify-between gap-3 border-t pt-3 text-[11px] text-muted-foreground">
          <span className="flex items-center gap-1">
            <Phone className="h-3 w-3" /> {booking.guestPhone}
          </span>

          <div className="flex items-center gap-2 ml-auto">
            {/* UC11 / D1 / BR-22: Leave Feedback Button for ARRIVED status */}
            {booking.status === "ARRIVED" && (
              <Button
                type="button"
                variant="outline"
                size="sm"
                className="h-7 text-xs border-primary/40 text-primary hover:bg-primary/10"
                onClick={() => {
                  window.alert("Tính năng đánh giá món ăn và dịch vụ đang được kết nối.");
                }}
              >
                <Star className="h-3.5 w-3.5 mr-1 text-amber-500 fill-amber-500" />
                Để lại đánh giá
              </Button>
            )}

            {/* UC12: Cancel Booking Button */}
            {canCancel && (
              <TooltipProvider>
                <Tooltip>
                  <TooltipTrigger asChild>
                    <span>
                      <Button
                        type="button"
                        variant="ghost"
                        size="sm"
                        disabled={isWithin60Min}
                        onClick={() => onCancelClick?.(booking)}
                        className="h-7 text-xs text-destructive hover:bg-destructive/10"
                      >
                        <XCircle className="h-3.5 w-3.5 mr-1" />
                        Hủy đặt bàn
                      </Button>
                    </span>
                  </TooltipTrigger>
                  {isWithin60Min && (
                    <TooltipContent className="text-xs max-w-xs">
                      Không thể hủy trực tuyến trong vòng 60 phút trước giờ hẹn. Vui lòng gọi hotline nhà hàng để được hỗ trợ.
                    </TooltipContent>
                  )}
                </Tooltip>
              </TooltipProvider>
            )}

            {booking.createdAt && (
              <span className="hidden sm:inline text-muted-foreground/80">
                Tạo lúc: {new Date(booking.createdAt).toLocaleDateString("vi-VN")}
              </span>
            )}
          </div>
        </div>
      </CardContent>
    </Card>
  );
}
