import * as React from "react";
import { useQuery } from "@tanstack/react-query";
import { toast } from "sonner";
import { CheckCircle2, XCircle, AlertCircle, Loader2, Sparkles, Users, Calendar, Clock, MapPin } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import { reservationApi } from "../api/reservation-api";
import { useUpdateReservationStatus } from "../hooks/use-update-reservation-status";
import type { AvailableTable, ReservationResponse } from "../types/reservation.types";

interface ConfirmBookingDialogProps {
  booking: ReservationResponse | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function ConfirmBookingDialog({
  booking,
  open,
  onOpenChange,
}: ConfirmBookingDialogProps) {
  const [selectedTableId, setSelectedTableId] = React.useState<string>("");
  const [notes, setNotes] = React.useState<string>("");

  const updateStatusMutation = useUpdateReservationStatus();

  // Reset local state when dialog opens with a booking
  React.useEffect(() => {
    if (open && booking) {
      setSelectedTableId(booking.tableId ?? "");
      setNotes("");
    }
  }, [open, booking]);

  const bookingId = booking?.id;
  const { data: availableTables, isLoading: isLoadingTables } = useQuery<AvailableTable[]>({
    queryKey: ["available-tables", bookingId],
    queryFn: () => (bookingId ? reservationApi.getAvailableTables(bookingId) : Promise.resolve([])),
    enabled: open && Boolean(bookingId),
  });

  if (!booking) {
    return null;
  }

  const code = booking.id ? booking.id.substring(0, 8).toUpperCase() : "";
  const timeFormatted = booking.reservationTime ? booking.reservationTime.substring(0, 5) : "";

  const handleConfirm = async () => {
    if (!selectedTableId) {
      toast.error("Vui lòng chọn bàn khả dụng để gán cho khách.");
      return;
    }

    try {
      await updateStatusMutation.mutateAsync({
        id: booking.id,
        payload: {
          status: "CONFIRMED",
          tableId: selectedTableId,
          reason: notes.trim() || undefined,
        },
      });
      toast.success(`Đã xác nhận thành công đơn đặt bàn HV-${code}`);
      onOpenChange(false);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "Xác nhận đặt bàn thất bại";
      toast.error(msg);
    }
  };

  const handleReject = async () => {
    if (!notes.trim()) {
      toast.error("Vui lòng nhập lý do từ chối/hủy đặt bàn.");
      return;
    }

    try {
      await updateStatusMutation.mutateAsync({
        id: booking.id,
        payload: {
          status: "CANCELLED",
          reason: notes.trim(),
        },
      });
      toast.info(`Đã hủy đơn đặt bàn HV-${code}`);
      onOpenChange(false);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "Hủy đặt bàn thất bại";
      toast.error(msg);
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2 text-lg">
            <span>Xác Nhận Đặt Bàn</span>
            <Badge variant="outline" className="font-mono text-primary">
              HV-{code}
            </Badge>
          </DialogTitle>
          <DialogDescription>
            Kiểm tra thông tin khách hàng, chọn bàn phù hợp và lưu kết quả liên hệ.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4 py-2">
          {/* Reservation Summary */}
          <div className="rounded-lg border bg-muted/30 p-3 text-xs space-y-2">
            <div className="flex items-center justify-between">
              <span className="font-semibold text-foreground">{booking.guestName}</span>
              <span className="text-muted-foreground">{booking.guestPhone}</span>
            </div>
            <div className="grid grid-cols-2 gap-2 text-muted-foreground">
              <div className="flex items-center gap-1.5">
                <Calendar className="h-3.5 w-3.5" />
                <span>{booking.reservationDate}</span>
              </div>
              <div className="flex items-center gap-1.5">
                <Clock className="h-3.5 w-3.5" />
                <span>{timeFormatted}</span>
              </div>
              <div className="flex items-center gap-1.5">
                <Users className="h-3.5 w-3.5" />
                <span>{booking.partySize} khách</span>
              </div>
              <div className="flex items-center gap-1.5">
                <MapPin className="h-3.5 w-3.5" />
                <span className="truncate">{booking.zoneName ?? "Chưa chỉ định khu"}</span>
              </div>
            </div>
            {booking.specialRequests && (
              <div className="border-t pt-1.5 text-muted-foreground italic">
                Ghi chú khách: &ldquo;{booking.specialRequests}&rdquo;
              </div>
            )}
          </div>

          {/* Table Selection */}
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <Label className="text-xs font-semibold">
                Chọn bàn gán <span className="text-destructive">*</span>
              </Label>
              <span className="text-[11px] text-muted-foreground">
                (Sắp xếp theo tối ưu sức chứa)
              </span>
            </div>

            {isLoadingTables ? (
              <div className="flex items-center justify-center p-4 text-xs text-muted-foreground">
                <Loader2 className="h-4 w-4 animate-spin mr-2" />
                Đang tìm bàn khả dụng...
              </div>
            ) : availableTables && availableTables.length > 0 ? (
              <div className="max-h-44 overflow-y-auto space-y-1.5 rounded-md border p-2">
                {availableTables.map((table) => {
                  const isSelected = selectedTableId === table.id;
                  return (
                    <button
                      key={table.id}
                      type="button"
                      onClick={() => setSelectedTableId(table.id)}
                      className={`w-full flex items-center justify-between rounded-md p-2 text-xs text-left transition-colors ${
                        isSelected
                          ? "bg-primary/10 border border-primary text-primary font-medium"
                          : "hover:bg-accent border border-transparent text-foreground"
                      }`}
                    >
                      <div className="flex items-center gap-2">
                        <Sparkles className="h-3.5 w-3.5 shrink-0 text-primary" />
                        <span>Bàn {table.tableNumber}</span>
                        <span className="text-muted-foreground">
                          ({table.zoneName ?? "Khu chung"})
                        </span>
                      </div>
                      <Badge variant="secondary" className="text-[10px]">
                        {table.capacity} chỗ
                      </Badge>
                    </button>
                  );
                })}
              </div>
            ) : (
              <div className="rounded-md border border-dashed p-3 text-center text-xs text-muted-foreground">
                <AlertCircle className="h-4 w-4 mx-auto mb-1 text-amber-500" />
                Không có bàn trống nào đủ cho {booking.partySize} khách trong khu vực này.
              </div>
            )}
          </div>

          {/* Staff notes */}
          <div className="space-y-1.5">
            <Label htmlFor="staffNotes" className="text-xs font-semibold">
              Ghi chú của nhân viên / Kết quả liên hệ
            </Label>
            <Textarea
              id="staffNotes"
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              placeholder="Ví dụ: Đã gọi điện xác nhận khách đến đúng 18h..."
              rows={2}
              className="text-xs"
            />
          </div>
        </div>

        <DialogFooter className="flex-col sm:flex-row gap-2">
          <Button
            type="button"
            variant="destructive"
            size="sm"
            onClick={() => void handleReject()}
            disabled={updateStatusMutation.isPending}
            className="sm:mr-auto text-xs"
          >
            <XCircle className="h-3.5 w-3.5 mr-1" />
            Từ chối / Hủy đơn
          </Button>
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => onOpenChange(false)}
            className="text-xs"
          >
            Đóng
          </Button>
          <Button
            type="button"
            size="sm"
            onClick={() => void handleConfirm()}
            disabled={updateStatusMutation.isPending || !selectedTableId}
            className="text-xs"
          >
            {updateStatusMutation.isPending ? (
              <Loader2 className="h-3.5 w-3.5 animate-spin mr-1" />
            ) : (
              <CheckCircle2 className="h-3.5 w-3.5 mr-1" />
            )}
            Xác nhận đặt bàn
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
