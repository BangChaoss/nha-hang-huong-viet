import * as React from "react";
import { toast } from "sonner";
import { AlertTriangle, Loader2 } from "lucide-react";
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
import { useCancelBooking } from "../hooks/use-cancel-booking";
import type { ReservationResponse } from "../types/reservation.types";

interface CancelBookingDialogProps {
  booking: ReservationResponse | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function CancelBookingDialog({
  booking,
  open,
  onOpenChange,
}: CancelBookingDialogProps) {
  const [reason, setReason] = React.useState("");
  const cancelMutation = useCancelBooking();

  React.useEffect(() => {
    if (open) {
      setReason("");
    }
  }, [open]);

  if (!booking) {
    return null;
  }

  const code = booking.id ? booking.id.substring(0, 8).toUpperCase() : "";

  const handleConfirmCancel = async () => {
    try {
      await cancelMutation.mutateAsync({
        id: booking.id,
        reason: reason.trim() || undefined,
        phone: booking.guestPhone,
      });
      toast.success(`Đã hủy đặt bàn HV-${code} thành công.`);
      onOpenChange(false);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "Hủy đặt bàn thất bại.";
      toast.error(msg);
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2 text-destructive text-base">
            <AlertTriangle className="h-5 w-5 shrink-0" />
            <span>Xác Nhận Hủy Đặt Bàn HV-{code}</span>
          </DialogTitle>
          <DialogDescription className="text-xs">
            Bạn có chắc chắn muốn hủy đặt bàn vào ngày <strong>{booking.reservationDate}</strong> lúc{" "}
            <strong>{booking.reservationTime?.substring(0, 5)}</strong>? Thao tác này sẽ giải phóng chỗ cho khách khác.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-2 py-2">
          <Label htmlFor="cancelReason" className="text-xs font-medium">
            Lý do hủy (không bắt buộc)
          </Label>
          <Textarea
            id="cancelReason"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder="Ví dụ: Thay đổi kế hoạch đột xuất, có việc bận..."
            rows={3}
            className="text-xs"
          />
        </div>

        <DialogFooter className="gap-2 sm:gap-0">
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
            variant="destructive"
            size="sm"
            onClick={() => void handleConfirmCancel()}
            disabled={cancelMutation.isPending}
            className="text-xs"
          >
            {cancelMutation.isPending ? (
              <Loader2 className="h-3.5 w-3.5 animate-spin mr-1" />
            ) : null}
            Xác nhận hủy đặt bàn
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
