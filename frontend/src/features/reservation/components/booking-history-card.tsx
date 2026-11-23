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
  Loader2,
  CheckCircle,
  UtensilsCrossed,
  CreditCard,
} from "lucide-react";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import { feedbackApi } from "@/features/feedback/api/feedback-api";
import { reservationApi } from "../api/reservation-api";
import { RESERVATION_STATUS_CONFIG, type ReservationResponse } from "../types/reservation.types";

interface BookingHistoryCardProps {
  booking: ReservationResponse;
  onCancelClick?: (booking: ReservationResponse) => void;
  onLeaveFeedback?: (invoiceId: string) => void;
}

export function BookingHistoryCard({
  booking,
  onCancelClick,
  onLeaveFeedback,
}: BookingHistoryCardProps) {
  const [checkingInvoice, setCheckingInvoice] = React.useState(false);
  const [isRedirectingDeposit, setIsRedirectingDeposit] = React.useState(false);

  const handleContinueDeposit = async (e?: React.MouseEvent) => {
    if (e) e.stopPropagation();
    try {
      setIsRedirectingDeposit(true);
      const res = await reservationApi.createDepositPayment(booking.id);
      if (res?.paymentUrl) {
        window.location.href = res.paymentUrl;
      } else {
        toast.error("Không thể khởi tạo cổng thanh toán VNPay");
        setIsRedirectingDeposit(false);
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "Lỗi khi gọi VNPay";
      toast.error(msg);
      setIsRedirectingDeposit(false);
    }
  };

  const handleLeaveFeedback = async () => {
    // Chỉ cho phép đánh giá khi đơn đã thanh toán hoàn tất (PAID)
    if (booking.status !== "PAID") {
      toast.warning("Chỉ có thể đánh giá khi đơn đặt bàn đã hoàn tất thanh toán hóa đơn.");
      return;
    }

    try {
      setCheckingInvoice(true);
      const info = await feedbackApi.getReservationInvoice(booking.id);

      if (info.hasFeedback) {
        toast.info("Đơn đặt bàn này đã được gửi đánh giá trước đó rồi.");
        return;
      }

      if (!info.isPaid) {
        toast.warning("Đơn đặt bàn này chưa hoàn tất thanh toán hóa đơn. Vui lòng thanh toán trước khi đánh giá.");
        return;
      }

      const invId = info.invoiceId || info.invoiceCode || booking.id;
      if (onLeaveFeedback) {
        onLeaveFeedback(invId);
      }
    } catch (err: unknown) {
      if (booking.status === "PAID" && onLeaveFeedback) {
        onLeaveFeedback(booking.id);
        return;
      }
      const msg = err instanceof Error ? err.message : "Không thể kiểm tra hoá đơn cho đơn đặt bàn này.";
      toast.error(msg);
    } finally {
      setCheckingInvoice(false);
    }
  };

  const statusCfg =
    RESERVATION_STATUS_CONFIG[booking.status] || RESERVATION_STATUS_CONFIG.PENDING_HOLD;
  const timeFormatted = booking.reservationTime ? booking.reservationTime.substring(0, 5) : "";
  const code = booking.id ? booking.id.substring(0, 8).toUpperCase() : "";

  // Kiểm tra thời gian hẹn đã qua hay chưa
  const isPastBookingTime = React.useMemo(() => {
    if (["CANCELLED", "NO_SHOW", "ARRIVED", "PAID", "REVIEWED"].includes(booking.status)) {
      return false;
    }
    try {
      const target = new Date(`${booking.reservationDate}T${booking.reservationTime}`);
      return Date.now() > target.getTime();
    } catch {
      return false;
    }
  }, [booking.reservationDate, booking.reservationTime, booking.status]);

  // Nút hủy chỉ hiển thị khi CHƯA ĐẾN NHẬN BÀN (PENDING_HOLD, PENDING_DEPOSIT, CONFIRMED)
  const canCancel =
    ["PENDING_HOLD", "PENDING_DEPOSIT", "CONFIRMED"].includes(booking.status) &&
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
          <div className="flex items-center gap-1.5">
            <Badge variant="outline" className={statusCfg.badgeCls}>
              {booking.status === "PENDING_DEPOSIT" ? "Chờ cọc VIP ⏳" : statusCfg.label}
            </Badge>
            {booking.status !== "PENDING_DEPOSIT" && booking.status !== "CANCELLED" && booking.depositAmount > 0 && (
              <Badge variant="outline" className="text-[10px] px-1.5 py-0 h-5 bg-emerald-500/10 text-emerald-700 border-emerald-500/30">
                Đã cọc 1tr ✅
              </Badge>
            )}
          </div>
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
              {booking.zoneName || "Khu vực đang cập nhật"}
              {booking.zoneIsVip && " ⭐"}
            </span>
          </div>
        </div>

        {booking.tableNumber && (
          <div className="mt-3 inline-flex items-center gap-1.5 rounded-md bg-secondary/50 px-2.5 py-1 text-xs font-medium text-secondary-foreground">
            <Sparkles className="h-3.5 w-3.5 text-primary" />
            <span>
              {booking.tableNumber.startsWith("Bàn") || booking.tableNumber.includes("+")
                ? ""
                : "Bàn số: "}
              <strong>{booking.tableNumber}</strong>
            </span>
          </div>
        )}

        {booking.specialRequests && (
          <div className="mt-3 flex items-start gap-2 rounded-md bg-muted/40 p-2 text-xs text-muted-foreground">
            <MessageSquare className="h-3.5 w-3.5 shrink-0 mt-0.5 text-muted-foreground" />
            <span className="line-clamp-2">Ghi chú: {booking.specialRequests}</span>
          </div>
        )}

        {booking.status === "CANCELLED" && booking.cancellationReason && (
          <div className="mt-3 flex items-start gap-2 rounded-md bg-rose-500/10 border border-rose-500/20 p-2 text-xs text-rose-700 dark:text-rose-400">
            <AlertCircle className="h-3.5 w-3.5 shrink-0 mt-0.5" />
            <span>Lý do hủy: {booking.cancellationReason}</span>
          </div>
        )}

        {/* Banner nhắc đặt cọc cho đơn VIP */}
        {booking.status === "PENDING_DEPOSIT" && (
          <div
            onClick={handleContinueDeposit}
            role="button"
            tabIndex={0}
            className="mt-3 flex items-center justify-between gap-3 rounded-lg border border-orange-500/30 bg-orange-500/10 p-3 text-xs text-orange-800 dark:text-orange-300 cursor-pointer hover:bg-orange-500/15 transition-colors"
          >
            <div className="flex items-center gap-2">
              <CreditCard className="h-4 w-4 text-orange-600 dark:text-orange-400 shrink-0" />
              <span>
                Đơn đặt bàn Phòng VIP đang chờ đặt cọc <strong>1.000.000đ</strong> để xác nhận giữ chỗ. Nhấn để tiếp tục thanh toán.
              </span>
            </div>
            <span className="font-semibold text-[11px] underline shrink-0 flex items-center gap-1 text-orange-700 dark:text-orange-400">
              {isRedirectingDeposit ? <Loader2 className="h-3 w-3 animate-spin" /> : "Thanh toán ngay →"}
            </span>
          </div>
        )}

        {/* Footer Actions */}
        <div className="mt-4 flex flex-wrap items-center justify-between gap-3 border-t pt-3 text-[11px] text-muted-foreground">
          <span className="flex items-center gap-1 font-mono">
            <Phone className="h-3 w-3" /> {booking.guestPhone}
          </span>

          <div className="flex items-center gap-2 ml-auto">
            {/* 0. Nút tiếp tục đặt cọc VNPay */}
            {booking.status === "PENDING_DEPOSIT" && (
              <Button
                type="button"
                size="sm"
                disabled={isRedirectingDeposit}
                onClick={handleContinueDeposit}
                className="h-8 text-xs font-semibold bg-orange-600 hover:bg-orange-700 text-white cursor-pointer shadow-sm gap-1.5"
              >
                {isRedirectingDeposit ? (
                  <Loader2 className="h-3.5 w-3.5 animate-spin" />
                ) : (
                  <CreditCard className="h-3.5 w-3.5" />
                )}
                Tiếp tục đặt cọc VNPay
              </Button>
            )}
            {/* 1. Nếu khách đang ở bàn (ARRIVED) nhưng chưa thanh toán: Hiển thị nhãn trạng thái, KHÔNG hiển thị nút đánh giá */}
            {booking.status === "ARRIVED" && (
              <span className="inline-flex items-center gap-1 rounded-full bg-blue-500/10 px-2.5 py-1 text-xs font-medium text-blue-700 dark:text-blue-400 border border-blue-500/20">
                <UtensilsCrossed className="h-3.5 w-3.5 mr-0.5" />
                Đang dùng bữa (chờ thanh toán)
              </span>
            )}

            {/* 2. NÚT ĐÁNH GIÁ: CHỈ HIỂN THỊ KHI ĐÃ HOÀN TẤT THANH TOÁN (status === "PAID") */}
            {booking.status === "PAID" && (
              <Button
                type="button"
                variant="outline"
                size="sm"
                disabled={checkingInvoice}
                className="h-8 text-xs border-primary/50 bg-primary/5 text-primary hover:bg-primary/15 font-semibold cursor-pointer"
                onClick={handleLeaveFeedback}
              >
                {checkingInvoice ? (
                  <Loader2 className="h-3.5 w-3.5 mr-1.5 animate-spin" />
                ) : (
                  <Star className="h-3.5 w-3.5 mr-1.5 text-amber-500 fill-amber-500" />
                )}
                Để lại đánh giá
              </Button>
            )}

            {/* 3. Đã đánh giá (REVIEWED) */}
            {booking.status === "REVIEWED" && (
              <span className="inline-flex items-center gap-1.5 rounded-full bg-purple-500/10 px-2.5 py-1 text-xs text-purple-700 dark:text-purple-400 border border-purple-500/20 font-semibold">
                <CheckCircle className="h-3.5 w-3.5 text-purple-600" />
                Đã đánh giá
              </span>
            )}

            {/* 4. Nút Hủy đặt bàn: Hiển thị khi CHƯA ĐẾN NHẬN BÀN */}
            {canCancel && (
              <TooltipProvider>
                <Tooltip>
                  <TooltipTrigger asChild>
                    <span>
                      <Button
                        type="button"
                        variant="ghost"
                        size="sm"
                        disabled={isPastBookingTime}
                        onClick={() => onCancelClick?.(booking)}
                        className="h-7 text-xs text-destructive hover:bg-destructive/10 disabled:opacity-50 cursor-pointer"
                      >
                        <XCircle className="h-3.5 w-3.5 mr-1" />
                        Hủy đặt bàn
                      </Button>
                    </span>
                  </TooltipTrigger>
                  {isPastBookingTime && (
                    <TooltipContent className="text-xs max-w-xs">
                      Thời gian hẹn đặt bàn đã qua. Vui lòng liên hệ hotline nhà hàng để được hỗ trợ.
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