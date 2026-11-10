import * as React from "react";
import {
  Calendar,
  Clock,
  Phone,
  Users,
  UtensilsCrossed,
  CheckCircle2,
  UserCheck,
  XCircle,
  AlertTriangle,
  Copy,
  Check,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import {
  RESERVATION_STATUS_CONFIG,
  type ReservationResponse,
} from "../types/reservation.types";

interface StaffBookingCardProps {
  booking: ReservationResponse;
  onOpenConfirm: (booking: ReservationResponse) => void;
  onCheckIn: (booking: ReservationResponse) => void;
  onCancel: (booking: ReservationResponse) => void;
  isActionPending?: boolean;
}

export function StaffBookingCard({
  booking,
  onOpenConfirm,
  onCheckIn,
  onCancel,
  isActionPending = false,
}: StaffBookingCardProps) {
  const [copied, setCopied] = React.useState(false);
  const code = booking.id ? booking.id.substring(0, 8).toUpperCase() : "";
  const timeFormatted = booking.reservationTime
    ? booking.reservationTime.substring(0, 5)
    : "";
  const isPending = booking.status === "PENDING_HOLD";
  const isConfirmed = booking.status === "CONFIRMED";

  const statusCfg =
    RESERVATION_STATUS_CONFIG[booking.status] ||
    RESERVATION_STATUS_CONFIG.PENDING_HOLD;

  // Calculate day label relative to today
  const dayLabel = React.useMemo(() => {
    if (!booking.reservationDate) return "";
    const today = new Date();
    today.setHours(0, 0, 0, 0);

    const [year, month, day] = booking.reservationDate.split("-").map(Number);
    const targetDate = new Date(year ?? 2026, (month ?? 1) - 1, day ?? 1);
    targetDate.setHours(0, 0, 0, 0);

    const diffDays = Math.round(
      (targetDate.getTime() - today.getTime()) / (1000 * 60 * 60 * 24),
    );

    if (diffDays === 0) return "Hôm nay";
    if (diffDays === 1) return "Ngày mai";
    if (diffDays === 2) return "Ngày kia";
    if (diffDays > 2) return `${diffDays} ngày nữa`;
    if (diffDays === -1) return "Hôm qua";
    return `${Math.abs(diffDays)} ngày trước`;
  }, [booking.reservationDate]);

  // Check-in timing evaluation
  const checkInTiming = React.useMemo(() => {
    if (!booking.reservationDate || !booking.reservationTime) {
      return { allowed: true, reason: null, diffMinutes: 0 };
    }
    const [year, month, day] = booking.reservationDate.split("-").map(Number);
    const [hours, minutes] = booking.reservationTime.split(":").map(Number);
    const bookingDate = new Date(
      year ?? 2026,
      (month ?? 1) - 1,
      day ?? 1,
      hours ?? 0,
      minutes ?? 0,
      0,
      0,
    );

    const now = new Date();
    const diffMinutes = (now.getTime() - bookingDate.getTime()) / (1000 * 60);

    if (diffMinutes < -30) {
      return {
        allowed: false,
        reason: "Chưa đến giờ check-in (trước tối đa 30p)",
        diffMinutes,
      };
    }
    if (diffMinutes > 30) {
      return {
        allowed: false,
        reason: "Quá giờ nhận bàn (>30p)",
        diffMinutes,
      };
    }
    return { allowed: true, reason: null, diffMinutes };
  }, [booking.reservationDate, booking.reservationTime]);

  const handleCopyPhone = (e: React.MouseEvent) => {
    e.stopPropagation();
    if (booking.guestPhone) {
      void navigator.clipboard.writeText(booking.guestPhone);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  return (
    <Card
      className={`relative overflow-hidden transition-all duration-200 border hover:shadow-md ${
        isPending
          ? "border-amber-400/60 bg-amber-500/[0.04] dark:bg-amber-500/[0.08]"
          : isConfirmed
            ? "border-emerald-500/30 bg-card"
            : "bg-card"
      }`}
    >
      {/* Visual Accent Strip */}
      <div
        className={`absolute left-0 top-0 bottom-0 w-1.5 ${
          isPending
            ? "bg-amber-500"
            : isConfirmed
              ? "bg-emerald-500"
              : booking.status === "ARRIVED"
                ? "bg-blue-500"
                : "bg-muted-foreground/30"
        }`}
      />

      <CardContent className="p-4 pl-5 flex flex-col justify-between gap-3 min-h-[170px]">
        {/* Top: Code, Time, Status */}
        <div className="flex items-start justify-between gap-2">
          <div className="flex flex-col">
            <div className="flex items-center gap-2">
              <span className="font-mono text-xs font-bold text-primary">
                HV-{code}
              </span>
              <Badge
                variant="outline"
                className={`text-[10px] px-1.5 py-0 h-5 font-normal ${statusCfg.badgeCls}`}
              >
                {statusCfg.label}
              </Badge>
            </div>

            <div className="flex items-center gap-2 mt-1">
              <span className="flex items-center gap-1 text-base font-bold text-foreground">
                <Clock className="h-4 w-4 text-primary shrink-0" />
                {timeFormatted}
              </span>
              <span className="text-xs text-muted-foreground">
                • {booking.reservationDate}
              </span>
              {dayLabel && (
                <span
                  className={`text-[11px] font-medium px-1.5 py-0.5 rounded ${
                    dayLabel === "Hôm nay"
                      ? "bg-primary/10 text-primary font-semibold"
                      : "bg-muted text-muted-foreground"
                  }`}
                >
                  {dayLabel}
                </span>
              )}
            </div>
          </div>

          <div className="flex items-center gap-1.5 rounded-full bg-secondary/80 px-2.5 py-1 text-xs font-medium text-secondary-foreground shrink-0">
            <Users className="h-3.5 w-3.5" />
            <span>{booking.partySize} khách</span>
          </div>
        </div>

        {/* Middle: Customer & Table info */}
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 text-xs py-1 border-t border-b border-border/50">
          <div>
            <p className="font-semibold text-foreground truncate">
              {booking.guestName}
            </p>
            <div className="flex items-center gap-1 text-muted-foreground mt-0.5">
              <Phone className="h-3 w-3 shrink-0" />
              <span>{booking.guestPhone}</span>
              <button
                type="button"
                onClick={handleCopyPhone}
                title="Sao chép SĐT"
                className="p-0.5 hover:text-foreground transition-colors"
              >
                {copied ? (
                  <Check className="h-3 w-3 text-emerald-500" />
                ) : (
                  <Copy className="h-3 w-3 text-muted-foreground" />
                )}
              </button>
            </div>
          </div>

          <div>
            <div className="flex items-center gap-1.5 font-medium text-foreground">
              <UtensilsCrossed className="h-3.5 w-3.5 text-muted-foreground shrink-0" />
              {booking.tableNumber ? (
                <span className="font-semibold text-emerald-700 dark:text-emerald-400">
                  Bàn {booking.tableNumber}
                </span>
              ) : (
                <span className="text-amber-600 dark:text-amber-400 font-medium">
                  Chưa gán bàn
                </span>
              )}
            </div>
            <p className="text-[11px] text-muted-foreground truncate mt-0.5">
              {booking.zoneName || "Khu vực đang cập nhật"}
              {booking.zoneIsVip ? " (VIP)" : ""}
            </p>
          </div>
        </div>

        {/* Special requests / notes */}
        {booking.specialRequests && (
          <p className="text-[11px] text-muted-foreground italic line-clamp-1">
            Ghi chú: {booking.specialRequests}
          </p>
        )}

        {/* Bottom Actions */}
        <div className="flex items-center justify-between gap-2 pt-1">
          <div>
            {isConfirmed && !checkInTiming.allowed && (
              <span className="flex items-center gap-1 text-[11px] font-medium text-amber-600 dark:text-amber-400">
                <AlertTriangle className="h-3 w-3 shrink-0" />
                {checkInTiming.reason}
              </span>
            )}
          </div>

          <div className="flex items-center gap-2 ml-auto">
            {isPending && (
              <Button
                type="button"
                size="sm"
                onClick={() => onOpenConfirm(booking)}
                disabled={isActionPending}
                className="h-9 px-4 text-xs font-semibold bg-amber-600 hover:bg-amber-700 text-white shadow-sm"
              >
                <CheckCircle2 className="h-4 w-4 mr-1.5" />
                Xác nhận & Gán bàn
              </Button>
            )}

            {isConfirmed && (
              <>
                <Button
                  type="button"
                  size="sm"
                  variant="outline"
                  onClick={() => onCancel(booking)}
                  disabled={isActionPending}
                  className="h-8 px-2.5 text-xs text-destructive hover:bg-destructive/10 border-destructive/20"
                >
                  <XCircle className="h-3.5 w-3.5 mr-1" />
                  Hủy
                </Button>
                <Button
                  type="button"
                  size="sm"
                  onClick={() => onCheckIn(booking)}
                  disabled={isActionPending || !checkInTiming.allowed}
                  title={checkInTiming.reason ?? "Xác nhận khách nhận bàn"}
                  className={`h-9 px-4 text-xs font-semibold ${
                    checkInTiming.allowed
                      ? "bg-emerald-600 hover:bg-emerald-700 text-white shadow-sm"
                      : "opacity-50 cursor-not-allowed bg-muted text-muted-foreground"
                  }`}
                >
                  <UserCheck className="h-4 w-4 mr-1.5" />
                  Check-in
                </Button>
              </>
            )}

            {!isPending && !isConfirmed && (
              <span className="text-xs text-muted-foreground">
                Hoàn tất
              </span>
            )}
          </div>
        </div>
      </CardContent>
    </Card>
  );
}
