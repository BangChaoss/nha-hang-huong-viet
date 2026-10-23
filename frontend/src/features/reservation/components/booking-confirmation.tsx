import * as React from "react";
import {
  CheckCircle2,
  Copy,
  Check,
  Calendar,
  Users,
  MapPin,
  Phone,
  User,
  Clock,
} from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardFooter, CardHeader } from "@/components/ui/card";
import { RESERVATION_STATUS_CONFIG, type ReservationResponse } from "../types/reservation.types";

interface BookingConfirmationProps {
  reservation: ReservationResponse;
  onReset: () => void;
  onViewHistory?: () => void;
}

export function BookingConfirmation({
  reservation,
  onReset,
  onViewHistory,
}: BookingConfirmationProps) {
  const [copied, setCopied] = React.useState(false);

  const bookingCode = reservation.id.substring(0, 8).toUpperCase();

  const handleCopyCode = async () => {
    try {
      await navigator.clipboard.writeText(bookingCode);
      setCopied(true);
      toast.success("Đã sao chép mã đặt bàn!");
      setTimeout(() => setCopied(false), 2000);
    } catch {
      toast.error("Không thể sao chép tự động");
    }
  };

  const statusCfg =
    RESERVATION_STATUS_CONFIG[reservation.status] || RESERVATION_STATUS_CONFIG.PENDING_HOLD;

  return (
    <Card className="mx-auto max-w-2xl border-emerald-500/30 bg-card shadow-lg">
      <CardHeader className="text-center pb-4 pt-8">
        <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-emerald-500/15 text-emerald-600 dark:text-emerald-400">
          <CheckCircle2 className="h-10 w-10 animate-in zoom-in-50 duration-300" />
        </div>
        <h2 className="text-2xl font-bold tracking-tight text-foreground">
          Yêu Cầu Đặt Bàn Đã Được Ghi Nhận!
        </h2>
        <p className="text-sm text-muted-foreground mt-1">
          Cảm ơn quý khách đã tin tưởng và lựa chọn Nhà hàng Hương Việt.
        </p>
      </CardHeader>

      <CardContent className="space-y-6">
        {/* Booking Code Card */}
        <div className="flex flex-col sm:flex-row items-center justify-between rounded-xl border border-primary/20 bg-primary/5 p-4 gap-3">
          <div>
            <p className="text-xs font-medium text-muted-foreground uppercase tracking-wider">
              Mã tra cứu đặt bàn
            </p>
            <p className="text-2xl font-extrabold tracking-widest text-primary font-mono">
              HV-{bookingCode}
            </p>
          </div>
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={handleCopyCode}
            className="gap-2 border-primary/30 hover:bg-primary/10"
          >
            {copied ? <Check className="h-4 w-4 text-emerald-600" /> : <Copy className="h-4 w-4" />}
            {copied ? "Đã chép" : "Sao chép mã"}
          </Button>
        </div>

        {/* Details Grid */}
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 rounded-xl border p-4 text-sm bg-muted/20">
          <div className="flex items-center gap-3">
            <User className="h-4 w-4 text-muted-foreground shrink-0" />
            <div>
              <p className="text-xs text-muted-foreground">Khách hàng</p>
              <p className="font-medium text-foreground">{reservation.guestName}</p>
            </div>
          </div>

          <div className="flex items-center gap-3">
            <Phone className="h-4 w-4 text-muted-foreground shrink-0" />
            <div>
              <p className="text-xs text-muted-foreground">Số điện thoại</p>
              <p className="font-medium text-foreground">{reservation.guestPhone}</p>
            </div>
          </div>

          <div className="flex items-center gap-3">
            <Calendar className="h-4 w-4 text-muted-foreground shrink-0" />
            <div>
              <p className="text-xs text-muted-foreground">Ngày dùng bữa</p>
              <p className="font-medium text-foreground">{reservation.reservationDate}</p>
            </div>
          </div>

          <div className="flex items-center gap-3">
            <Clock className="h-4 w-4 text-muted-foreground shrink-0" />
            <div>
              <p className="text-xs text-muted-foreground">Khung giờ</p>
              <p className="font-medium text-foreground">
                {reservation.reservationTime.substring(0, 5)}
              </p>
            </div>
          </div>

          <div className="flex items-center gap-3">
            <Users className="h-4 w-4 text-muted-foreground shrink-0" />
            <div>
              <p className="text-xs text-muted-foreground">Số lượng khách</p>
              <p className="font-medium text-foreground">{reservation.partySize} người</p>
            </div>
          </div>

          <div className="flex items-center gap-3">
            <MapPin className="h-4 w-4 text-muted-foreground shrink-0" />
            <div>
              <p className="text-xs text-muted-foreground">Khu vực</p>
              <p className="font-medium text-foreground">
                {reservation.zoneName || "Khu vực tiêu chuẩn"}
                {reservation.zoneIsVip && " (VIP)"}
              </p>
            </div>
          </div>
        </div>

        {reservation.specialRequests && (
          <div className="rounded-lg bg-muted/40 p-3 text-xs text-muted-foreground">
            <span className="font-semibold text-foreground">Yêu cầu đặc biệt: </span>
            {reservation.specialRequests}
          </div>
        )}

        {/* Notice Info Box */}
        <div className="rounded-xl border border-amber-500/30 bg-amber-500/10 p-4 text-xs text-amber-800 dark:text-amber-300 space-y-1">
          <div className="flex items-center gap-2 font-semibold">
            <span>Trạng thái đơn:</span>
            <Badge variant="outline" className={statusCfg.badgeCls}>
              {statusCfg.label}
            </Badge>
          </div>
          <p className="mt-1 leading-relaxed">
            Nhân viên nhà hàng sẽ gọi điện thoại tới số <strong>{reservation.guestPhone}</strong>{" "}
            trong vòng 15 phút để hoàn tất xác nhận bàn ăn của quý khách.
          </p>
        </div>
      </CardContent>

      <CardFooter className="flex flex-col sm:flex-row justify-end gap-3 border-t pt-5">
        <Button variant="outline" onClick={onReset} className="w-full sm:w-auto">
          Đặt thêm bàn khác
        </Button>
        {onViewHistory && (
          <Button
            onClick={onViewHistory}
            className="w-full sm:w-auto bg-primary text-primary-foreground"
          >
            Xem lịch sử đặt bàn
          </Button>
        )}
      </CardFooter>
    </Card>
  );
}
