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
  Check,
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
  // Multi-select table IDs support for table merging
  const [selectedTableIds, setSelectedTableIds] = React.useState<string[]>([]);
  const [notes, setNotes] = React.useState<string>("");
  const [viewMode, setViewMode] = React.useState<"floor-plan" | "list">("floor-plan");

  const updateStatusMutation = useUpdateReservationStatus();

  // Reset local state when dialog opens with a booking
  React.useEffect(() => {
    if (open && booking) {
      if (booking.mergedTableIds && booking.mergedTableIds.trim()) {
        const ids = booking.mergedTableIds.split(",").map((s) => s.trim()).filter(Boolean);
        setSelectedTableIds(ids.length > 0 ? ids : (booking.tableId ? [booking.tableId] : []));
      } else if (booking.tableId) {
        setSelectedTableIds([booking.tableId]);
      } else {
        setSelectedTableIds([]);
      }
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

  // Helper map of all known tables (from availableTables and floorPlanLayouts)
  // MUST be called before any conditional return to strictly follow Rules of Hooks
  const allKnownTables = React.useMemo(() => {
    const map = new Map<string, { id: string; tableNumber: string; capacity: number; zoneId?: string | null; zoneName?: string | null }>();
    if (availableTables) {
      for (const t of availableTables) {
        map.set(t.id, {
          id: t.id,
          tableNumber: t.tableNumber,
          capacity: t.capacity,
          zoneId: t.zoneId ?? null,
          zoneName: t.zoneName ?? null,
        });
      }
    }
    if (floorPlanLayouts) {
      for (const l of floorPlanLayouts) {
        if (!map.has(l.tableId)) {
          map.set(l.tableId, {
            id: l.tableId,
            tableNumber: l.tableNumber,
            capacity: l.capacity,
            zoneId: l.zoneId || null,
            zoneName: l.zoneName,
          });
        }
      }
    }
    return map;
  }, [availableTables, floorPlanLayouts]);

  if (!booking) {
    return null;
  }

  const code = booking.id ? booking.id.substring(0, 8).toUpperCase() : "";
  const timeFormatted = booking.reservationTime ? booking.reservationTime.substring(0, 5) : "";

  // List of currently selected table objects
  const selectedTablesList = selectedTableIds
    .map((id) => allKnownTables.get(id))
    .filter((t): t is NonNullable<typeof t> => t !== undefined);

  // Total merged capacity
  const totalMergedCapacity = selectedTablesList.reduce((sum, t) => sum + (t.capacity || 0), 0);
  const isCapacitySufficient = totalMergedCapacity >= booking.partySize;

  // Toggle table selection with SAME ZONE & NON-REDUNDANT MERGING constraints
  const handleToggleTable = (tableId: string, tableNumber: string, zoneId?: string | null, capacity = 0) => {
    if (selectedTableIds.includes(tableId)) {
      // Deselect table
      setSelectedTableIds((prev) => prev.filter((id) => id !== tableId));
      toast.info(`Đã bỏ chọn Bàn ${tableNumber}`);
      return;
    }

    const partySize = booking.partySize;

    // Rule 1: For small bookings (partySize <= 4), if 1 table is already sufficient, do not allow merging extra tables
    if (partySize <= 4 && selectedTableIds.length === 1) {
      const currentSelected = allKnownTables.get(selectedTableIds[0]!);
      if (currentSelected && (currentSelected.capacity || 0) >= partySize) {
        toast.warning(
          `Bàn ${currentSelected.tableNumber} (sức chứa ${currentSelected.capacity} chỗ) đã đủ cho đoàn ${partySize} khách. Để đổi sang bàn khác, vui lòng bỏ chọn bàn này trước.`
        );
        return;
      }
    }

    // Rule 2: If a single table in the current selection already fits the whole group, warn to use it alone
    if (selectedTableIds.length > 0) {
      const singleSufficient = selectedTablesList.find((t) => (t.capacity || 0) >= partySize);
      if (singleSufficient) {
        toast.warning(
          `Bàn ${singleSufficient.tableNumber} (sức chứa ${singleSufficient.capacity} chỗ) đã đủ cho đoàn ${partySize} khách. Không cần gộp thêm bàn khác.`
        );
        return;
      }

      // If the new table alone already fits the whole group, warn user to select it alone
      if (capacity >= partySize) {
        toast.warning(
          `Bàn ${tableNumber} (sức chứa ${capacity} chỗ) một mình đã đủ cho đoàn ${partySize} khách. Vui lòng bỏ chọn các bàn khác nếu muốn sử dụng bàn này.`
        );
        return;
      }
    }

    // Rule 3: Verify same zone constraint
    if (selectedTableIds.length > 0 && zoneId) {
      const firstSelected = allKnownTables.get(selectedTableIds[0]!);
      if (firstSelected?.zoneId && firstSelected.zoneId !== zoneId) {
        toast.error(`Chỉ có thể gộp các bàn trong CÙNG MỘT KHU VỰC (${firstSelected.zoneName || "khu vực đã chọn"})!`);
        return;
      }
    }

    setSelectedTableIds((prev) => [...prev, tableId]);
    if (selectedTableIds.length === 0) {
      toast.success(`Đã chọn Bàn ${tableNumber} (${capacity} chỗ)`);
    } else {
      toast.success(`Đã thêm Bàn ${tableNumber} (${capacity} chỗ) vào danh sách gộp bàn`);
    }
  };

  const handleSelectFromCanvas = (tableLayout: FloorPlanLayoutResponse | null) => {
    if (!tableLayout) return;

    // Check if clicked table is in availableTables
    const availTable = availableTables?.find((t) => t.id === tableLayout.tableId);
    if (!availTable && !selectedTableIds.includes(tableLayout.tableId)) {
      toast.warning(
        `Bàn ${tableLayout.tableNumber} hiện không khả dụng trong khung giờ này.`
      );
      return;
    }

    handleToggleTable(
      tableLayout.tableId,
      tableLayout.tableNumber,
      availTable?.zoneId ?? tableLayout.zoneId ?? null,
      tableLayout.capacity
    );
  };

  const handleConfirm = async () => {
    if (selectedTableIds.length === 0) {
      toast.error("Vui lòng chọn ít nhất một bàn để gán cho khách.");
      return;
    }

    if (!isCapacitySufficient) {
      toast.error(
        `Tổng sức chứa (${totalMergedCapacity} chỗ) chưa đủ cho đoàn ${booking.partySize} khách. Vui lòng chọn thêm bàn để gộp.`
      );
      return;
    }

    if (selectedTableIds.length > 1) {
      // Validate small groups (<= 4 guests) should not merge multiple tables
      if (booking.partySize <= 4) {
        toast.error(
          `Đoàn nhỏ (${booking.partySize} khách) vui lòng chỉ gán 1 bàn, không gộp nhiều bàn.`
        );
        return;
      }

      // Validate no single table is already sufficient
      const singleSufficient = selectedTablesList.find((t) => (t.capacity || 0) >= booking.partySize);
      if (singleSufficient) {
        toast.error(
          `Bàn ${singleSufficient.tableNumber} (sức chứa ${singleSufficient.capacity} chỗ) đã đủ cho đoàn ${booking.partySize} khách. Không cần gộp thêm bàn khác.`
        );
        return;
      }

      // Validate no redundant table in the merged set (when 3 or more tables are selected)
      if (selectedTablesList.length > 2) {
        const redundantTable = selectedTablesList.find(
          (t) => totalMergedCapacity - (t.capacity || 0) >= booking.partySize
        );
        if (redundantTable) {
          toast.error(
            `Gộp bàn dư thừa: Bàn ${redundantTable.tableNumber} không cần thiết vì các bàn còn lại đã đủ cho đoàn ${booking.partySize} khách.`
          );
          return;
        }
      }
    }

    try {
      await updateStatusMutation.mutateAsync({
        id: booking.id,
        payload: {
          status: "CONFIRMED",
          tableId: selectedTableIds[0]!,
          tableIds: selectedTableIds,
          ...(notes.trim() ? { reason: notes.trim() } : {}),
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
            <span>Xác Nhận Đặt Bàn & Xếp Bàn</span>
            <Badge variant="outline" className="font-mono text-primary">
              HV-{code}
            </Badge>
          </DialogTitle>
          <DialogDescription>
            Chọn 1 bàn hoặc chọn nhiều bàn để gộp bàn trong cùng khu vực sao cho đủ chỗ cho khách.
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

          {/* Selected Table(s) Merging Indicator Banner */}
          {selectedTablesList.length > 0 ? (
            <div className="rounded-lg bg-primary/10 border border-primary/30 p-3 text-xs space-y-2">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div className="flex items-center gap-2">
                  <Sparkles className="h-4 w-4 text-primary shrink-0" />
                  <span className="font-bold text-foreground">
                    {selectedTablesList.length > 1 ? "Bàn gộp đã chọn: " : "Bàn đã chọn: "}
                    <span className="text-primary font-extrabold">
                      {selectedTablesList.map((t) => `Bàn ${t.tableNumber} (${t.capacity} chỗ)`).join(" + ")}
                    </span>
                  </span>
                </div>
                <div className="flex items-center gap-2">
                  <Badge variant="secondary" className="font-mono text-xs">
                    Tổng: {totalMergedCapacity} chỗ / Yêu cầu: {booking.partySize} khách
                  </Badge>
                  {isCapacitySufficient ? (
                    <Badge variant="default" className="bg-emerald-600 text-white text-[10px]">
                      Đủ chỗ ✅
                    </Badge>
                  ) : (
                    <Badge variant="outline" className="border-amber-500 text-amber-700 bg-amber-500/10 text-[10px]">
                      Thiếu {booking.partySize - totalMergedCapacity} chỗ ⚠️
                    </Badge>
                  )}
                </div>
              </div>
              {selectedTablesList.length > 1 && (
                <p className="text-[11px] text-muted-foreground">
                  💡 Tính năng gộp bàn: Các bàn trên đều thuộc cùng khu vực và sẽ được giữ cùng lúc cho đoàn khách này.
                </p>
              )}
            </div>
          ) : (
            <div className="rounded-md border border-dashed border-amber-500/40 bg-amber-500/5 p-2 text-xs text-amber-700 dark:text-amber-400">
              Vui lòng chọn bàn bằng cách bấm vào các bàn trên Sơ đồ 2D hoặc trong Danh sách bàn bên dưới (cho phép chọn gộp nhiều bàn).
            </div>
          )}

          {/* Table Selection Tabs (Floor Plan 2D vs List) */}
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <Label className="text-xs font-semibold">
                Chọn bàn xếp chỗ (Bấm để chọn/bỏ chọn gộp bàn) <span className="text-destructive">*</span>
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
                      * Nhấp chọn một hoặc nhiều bàn trên sơ đồ để gộp bàn. Bấm biểu tượng "Căn vừa" để nhìn trọn toàn bộ sơ đồ.
                    </p>
                    <div className="border rounded-xl overflow-hidden bg-slate-50/50">
                      <FloorPlanCanvas
                        layouts={floorPlanLayouts}
                        selectedTableIds={selectedTableIds}
                        onSelectTable={handleSelectFromCanvas}
                        readOnly={true}
                        canvasHeight={420}
                      />
                    </div>
                  </div>
                ) : (
                  <div className="rounded-md border border-dashed p-4 text-center text-xs text-muted-foreground">
                    Chưa thiết lập sơ đồ mặt bằng. Vui lòng chuyển sang tab Danh sách bàn.
                  </div>
                )}
              </TabsContent>

              {/* Tab 2: Table List with Multi-Select Checkbox */}
              <TabsContent value="list" className="pt-2">
                {isLoadingTables ? (
                  <div className="flex items-center justify-center p-6 text-xs text-muted-foreground">
                    <Loader2 className="h-4 w-4 animate-spin mr-2" />
                    Đang tìm bàn khả dụng...
                  </div>
                ) : availableTables && availableTables.length > 0 ? (
                  <div className="max-h-60 overflow-y-auto space-y-1.5 rounded-md border p-2">
                    {availableTables.map((table) => {
                      const isSelected = selectedTableIds.includes(table.id);
                      return (
                        <button
                          key={table.id}
                          type="button"
                          onClick={() => handleToggleTable(table.id, table.tableNumber, table.zoneId, table.capacity)}
                          className={`w-full flex items-center justify-between rounded-md p-2.5 text-xs text-left transition-colors ${
                            isSelected
                              ? "bg-primary/10 border border-primary text-primary font-medium"
                              : "hover:bg-accent border border-transparent text-foreground"
                          }`}
                        >
                          <div className="flex items-center gap-2.5">
                            <div className={`h-4 w-4 rounded flex items-center justify-center border ${
                              isSelected ? "bg-primary border-primary text-white" : "border-muted-foreground/40"
                            }`}>
                              {isSelected && <Check className="h-3 w-3 stroke-[3]" />}
                            </div>
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
                    Không tìm thấy bàn trống nào trong khu vực này tại khung giờ đã chọn.
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
              placeholder="Ví dụ: Đã gọi điện xác nhận khách đến đúng giờ, đã ghép bàn 01 + 02..."
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
            disabled={updateStatusMutation.isPending || selectedTableIds.length === 0 || !isCapacitySufficient}
            className="text-xs"
          >
            {updateStatusMutation.isPending ? (
              <Loader2 className="h-3.5 w-3.5 animate-spin mr-1" />
            ) : (
              <CheckCircle2 className="h-3.5 w-3.5 mr-1" />
            )}
            Xác nhận & Gán bàn ({totalMergedCapacity} chỗ)
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
