import * as React from "react";
import { useQuery } from "@tanstack/react-query";
import { toast } from "sonner";
import {
  CheckCircle2,
  XCircle,
  AlertCircle,
  Loader2,
  Sparkles,
  Users,
  Calendar,
  Clock,
  MapPin,
  LayoutGrid,
  List,
} from "lucide-react";
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
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { reservationApi } from "../api/reservation-api";
import { useUpdateReservationStatus } from "../hooks/use-update-reservation-status";
import { floorPlanApi, type FloorPlanLayoutResponse } from "@/lib/table-api";
import { FloorPlanCanvas } from "@/components/hv/FloorPlanCanvas";
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
  const [viewMode, setViewMode] = React.useState<"floor-plan" | "list">("floor-plan");

  const updateStatusMutation = useUpdateReservationStatus();

  // Reset local state when dialog opens with a booking
  React.useEffect(() => {
    if (open && booking) {
      setSelectedTableId(booking.tableId ?? "");
      setNotes("");
      setViewMode("floor-plan");
    }
  }, [open, booking]);

  const bookingId = booking?.id;
  const { data: availableTables, isLoading: isLoadingTables } = useQuery<AvailableTable[]>({
    queryKey: ["available-tables", bookingId],
    queryFn: () => (bookingId ? reservationApi.getAvailableTables(bookingId) : Promise.resolve([])),
    enabled: open && Boolean(bookingId),
  });

  const { data: floorPlanLayouts, isLoading: isLoadingFloorPlan } = useQuery<FloorPlanLayoutResponse[]>({
    queryKey: ["floor-plan-layouts"],
    queryFn: () => floorPlanApi.getAll(),
    enabled: open,
  });

  if (!booking) {
    return null;
  }

  const code = booking.id ? booking.id.substring(0, 8).toUpperCase() : "";
  const timeFormatted = booking.reservationTime ? booking.reservationTime.substring(0, 5) : "";

  // Find currently selected table details
  const selectedTable = availableTables?.find((t) => t.id === selectedTableId)
    ?? floorPlanLayouts?.find((l) => l.tableId === selectedTableId);

  const handleSelectFromCanvas = (tableLayout: FloorPlanLayoutResponse | null) => {
    if (!tableLayout) return;

    // Check if clicked table is in availableTables
    const isAvail = availableTables?.some((t) => t.id === tableLayout.tableId);
    if (!isAvail) {
      toast.warning(
        `Bàn ${tableLayout.tableNumber} hiện không khả dụng hoặc không đủ sức chứa cho ${booking.partySize} khách.`
      );
      return;
    }

    setSelectedTableId(tableLayout.tableId);
    toast.success(`Đã chọn Bàn ${tableLayout.tableNumber} (${tableLayout.capacity} chỗ)`);
  };

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
      <DialogContent className="max-w-3xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2 text-lg">
            <span>Xác Nhận Đặt Bàn & Gán Bàn</span>
            <Badge variant="outline" className="font-mono text-primary">
              HV-{code}
            </Badge>
          </DialogTitle>
          <DialogDescription>
            Kiểm tra thông tin khách hàng, chọn bàn trực quan trên sơ đồ 2D hoặc danh sách bàn phù hợp.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4 py-2">
          {/* Reservation Summary */}
          <div className="rounded-lg border bg-muted/30 p-3 text-xs space-y-2">
            <div className="flex items-center justify-between">
              <span className="font-semibold text-foreground text-sm">{booking.guestName}</span>
              <span className="text-muted-foreground font-mono">{booking.guestPhone}</span>
            </div>
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 text-muted-foreground">
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
                <span className="font-semibold text-foreground">{booking.partySize} khách</span>
              </div>
              <div className="flex items-center gap-1.5">
                <MapPin className="h-3.5 w-3.5" />
                <span className="truncate font-semibold text-foreground">
                  {booking.zoneName ?? "Khu vực đang cập nhật"}
                  {booking.zoneIsVip ? " ⭐" : ""}
                </span>
              </div>
            </div>
            {booking.specialRequests && (
              <div className="border-t pt-1.5 text-muted-foreground italic">
                Ghi chú khách: &ldquo;{booking.specialRequests}&rdquo;
              </div>
            )}
          </div>

          {/* Selected Table Indicator Banner */}
          {selectedTable ? (
            <div className="flex items-center justify-between rounded-lg bg-primary/10 border border-primary/30 p-2.5 text-xs">
              <div className="flex items-center gap-2">
                <Sparkles className="h-4 w-4 text-primary shrink-0" />
                <div>
                  <span className="font-bold text-primary">
                    Bàn đã chọn: Bàn {selectedTable.tableNumber}
                  </span>
                  <span className="text-muted-foreground ml-2">
                    ({selectedTable.capacity} chỗ)
                  </span>
                </div>
              </div>
              <Badge variant="default" className="text-[10px] bg-primary text-primary-foreground">
                Đã chọn
              </Badge>
            </div>
          ) : (
            <div className="rounded-md border border-dashed border-amber-500/40 bg-amber-500/5 p-2 text-xs text-amber-700 dark:text-amber-400">
              Vui lòng chọn bàn bằng cách bấm vào bàn trên Sơ đồ 2D hoặc trong Danh sách bàn bên dưới.
            </div>
          )}

          {/* Table Selection Tabs (Floor Plan 2D vs List) */}
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <Label className="text-xs font-semibold">
                Chọn bàn gán <span className="text-destructive">*</span>
              </Label>
              <span className="text-[11px] text-muted-foreground">
                (Yêu cầu tối thiểu {booking.partySize} chỗ)
              </span>
            </div>

            <Tabs value={viewMode} onValueChange={(v) => setViewMode(v as "floor-plan" | "list")}>
              <TabsList className="grid w-full grid-cols-2 h-8">
                <TabsTrigger value="floor-plan" className="text-xs flex items-center gap-1.5">
                  <LayoutGrid className="h-3.5 w-3.5" />
                  Sơ đồ bàn 2D
                </TabsTrigger>
                <TabsTrigger value="list" className="text-xs flex items-center gap-1.5">
                  <List className="h-3.5 w-3.5" />
                  Danh sách bàn ({availableTables?.length ?? 0})
                </TabsTrigger>
              </TabsList>

              {/* Tab 1: 2D Floor Plan Canvas */}
              <TabsContent value="floor-plan" className="pt-2">
                {isLoadingFloorPlan ? (
                  <div className="flex h-64 items-center justify-center rounded-xl border bg-muted/20 text-xs text-muted-foreground">
                    <Loader2 className="h-4 w-4 animate-spin mr-2" />
                    Đang tải sơ đồ mặt bằng...
                  </div>
                ) : floorPlanLayouts && floorPlanLayouts.length > 0 ? (
                  <div className="space-y-2">
                    <p className="text-[11px] text-muted-foreground">
                      * Nhấp chuột trực tiếp vào bàn trên sơ đồ để chọn gán cho khách.
                    </p>
                    <div className="border rounded-xl overflow-hidden bg-slate-50/50">
                      <FloorPlanCanvas
                        layouts={floorPlanLayouts}
                        selectedTableId={selectedTableId}
                        onSelectTable={handleSelectFromCanvas}
                        readOnly={true}
                        canvasHeight={360}
                      />
                    </div>
                  </div>
                ) : (
                  <div className="rounded-md border border-dashed p-4 text-center text-xs text-muted-foreground">
                    Chưa thiết lập sơ đồ mặt bằng. Vui lòng chuyển sang tab Danh sách bàn.
                  </div>
                )}
              </TabsContent>

              {/* Tab 2: Table List */}
              <TabsContent value="list" className="pt-2">
                {isLoadingTables ? (
                  <div className="flex items-center justify-center p-6 text-xs text-muted-foreground">
                    <Loader2 className="h-4 w-4 animate-spin mr-2" />
                    Đang tìm bàn khả dụng...
                  </div>
                ) : availableTables && availableTables.length > 0 ? (
                  <div className="max-h-60 overflow-y-auto space-y-1.5 rounded-md border p-2">
                    {availableTables.map((table) => {
                      const isSelected = selectedTableId === table.id;
                      return (
                        <button
                          key={table.id}
                          type="button"
                          onClick={() => setSelectedTableId(table.id)}
                          className={`w-full flex items-center justify-between rounded-md p-2.5 text-xs text-left transition-colors ${
                            isSelected
                              ? "bg-primary/10 border border-primary text-primary font-medium"
                              : "hover:bg-accent border border-transparent text-foreground"
                          }`}
                        >
                          <div className="flex items-center gap-2">
                            <Sparkles className="h-3.5 w-3.5 shrink-0 text-primary" />
                            <span className="font-semibold">Bàn {table.tableNumber}</span>
                            <span className="text-muted-foreground">
                              ({table.zoneName ?? "Khu chung"})
                              {table.zoneIsVip ? " ⭐" : ""}
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
                  <div className="rounded-md border border-dashed p-4 text-center text-xs text-muted-foreground">
                    <AlertCircle className="h-4 w-4 mx-auto mb-1 text-amber-500" />
                    Không có bàn trống nào đủ cho {booking.partySize} khách trong khu vực này.
                  </div>
                )}
              </TabsContent>
            </Tabs>
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
              placeholder="Ví dụ: Đã gọi điện xác nhận khách đến đúng giờ..."
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
