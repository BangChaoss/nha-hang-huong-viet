import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Minus,
  Plus,
  Users,
  Clock,
  Phone,
  ChefHat,
  Wrench,
  Trash2,
  UserCheck,
  CalendarCheck,
  ArrowRightLeft,
  AlertTriangle,
  Sparkles,
} from "lucide-react";
import { toast } from "sonner";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from "@/components/ui/dialog";
import {
  AlertDialog,
  AlertDialogContent,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogAction,
  AlertDialogCancel,
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Switch } from "@/components/ui/switch";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { formatVND } from "@/lib/restaurant-store";
import { cn } from "@/lib/utils";
import { validateVoucher } from "@/lib/voucher-api";
import { paymentApi } from "@/lib/payment-api";
import { tableApi, zoneApi, type TableStatus, type TableResponse, type ZoneResponse } from "@/lib/table-api";
import { reservationApi } from "@/features/reservation/api/reservation-api";
import { ConfirmBookingDialog } from "@/features/reservation/components/confirm-booking-dialog";
import type { ReservationResponse } from "@/features/reservation/types/reservation.types";
import { useTableWebSocket } from "@/lib/use-table-websocket";
import { menuApi } from "@/features/menu/api/menu-api";
import { useCreateOrder } from "@/features/order/hooks/use-create-order";
import { orderApi } from "@/features/order/api/order-api";
import { useKitchenCompleted } from "@/features/order/hooks/use-kitchen-tickets";
import { useChangeOrderItems } from "@/features/order/hooks/use-order-items";
import { COOKING_STATUS_LABELS, type OrderResponse } from "@/features/order/types/order.types";
import { groupOrderItems, type OrderItemGroup } from "@/features/order/utils/order-item-groups";
import { orderCode } from "@/features/order/utils/order-code";
import { splitBillItems } from "@/features/order/utils/bill";

const ALL_CATEGORIES = "all";
const LEGACY_CART_KEY = "__legacy__";
const MAX_CART_QTY = 99;

/** Món được chọn tạm thời trong giỏ nháp trước khi xác nhận gửi bếp. */
type CartLine = { key: string; menuItemId: string; quantity: number; note: string };

const TABLE_STATUS_LABELS: Record<TableStatus, string> = {
  AVAILABLE: "Trống",
  RESERVED: "Đã đặt trước",
  OCCUPIED: "Đang phục vụ",
  MAINTENANCE: "Bảo trì",
};

const REAL_STATUS_CLASSES: Record<TableStatus, string> = {
  AVAILABLE: "status-available",
  RESERVED: "status-reserved",
  OCCUPIED: "status-occupied",
  MAINTENANCE: "status-maintenance",
};

function localDateString(date = new Date()): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function reservationDateTime(reservation: ReservationResponse): Date | null {
  if (!reservation.reservationDate || !reservation.reservationTime) return null;
  const [year, month, day] = reservation.reservationDate.split("-").map(Number);
  const [hours, minutes] = reservation.reservationTime.split(":").map(Number);
  return new Date(year ?? 2026, (month ?? 1) - 1, day ?? 1, hours ?? 0, minutes ?? 0, 0, 0);
}

function isWithinReservationWindow(reservation: ReservationResponse): boolean {
  const bookingDt = reservationDateTime(reservation);
  if (!bookingDt) return false;
  const diffMinutes = (Date.now() - bookingDt.getTime()) / 60000;
  return diffMinutes >= -120 && diffMinutes <= 119;
}

function evaluateCheckInWindow(reservation?: ReservationResponse): {
  allowed: boolean;
  reason: string | null;
  isOverdue: boolean;
} {
  if (!reservation) return { allowed: true, reason: null, isOverdue: false };
  const bookingDt = reservationDateTime(reservation);
  if (!bookingDt) return { allowed: true, reason: null, isOverdue: false };
  const diffMinutes = (Date.now() - bookingDt.getTime()) / 60000;
  if (diffMinutes < -30) {
    return { allowed: false, reason: "Chưa đến giờ check-in (trước tối đa 30p)", isOverdue: false };
  }
  if (diffMinutes > 30) {
    return { allowed: false, reason: "Quá giờ nhận bàn (>30p)", isOverdue: true };
  }
  return { allowed: true, reason: null, isOverdue: false };
}

function bookingCode(id: string): string {
  return `HV-${id.slice(0, 8).toUpperCase()}`;
}

export type ConsolidatedTable = TableResponse & {
  baseTableNumber: string;
  subTables: TableResponse[];
};

/**
 * Gom các bàn bị chia tách (ví dụ: Bàn 02A + Bàn 02B) thành 1 bàn gốc chưa chia duy nhất (Bàn 02)
 * với tổng sức chứa và trạng thái hợp nhất.
 */
export function consolidateTables(rawTables: TableResponse[]): ConsolidatedTable[] {
  const map = new Map<string, TableResponse[]>();
  const order: string[] = [];

  for (const table of rawTables) {
    // Tách hậu tố [A-Z] ở cuối nếu có (ví dụ "Bàn 03A" -> "Bàn 03", "Bàn 05B" -> "Bàn 05", "Bàn 20A" -> "Bàn 20")
    const baseNumber = table.tableNumber.replace(/\s*[A-Z]$/, "").trim();
    const key = `${table.zoneId || "none"}__${baseNumber}`;
    if (!map.has(key)) {
      map.set(key, []);
      order.push(key);
    }
    map.get(key)!.push(table);
  }

  return order.map((key) => {
    const subList = map.get(key)!;
    const first = subList[0]!;
    const baseNumber = first.tableNumber.replace(/\s*[A-Z]$/, "").trim();

    if (subList.length === 1 && first.tableNumber === baseNumber) {
      return {
        ...first,
        baseTableNumber: baseNumber,
        subTables: subList,
      };
    }

    const totalCapacity = subList.reduce((sum, t) => sum + (t.capacity || 0), 0);
    const hasOccupied = subList.some((t) => t.status === "OCCUPIED");
    const hasReserved = subList.some((t) => t.status === "RESERVED");
    const allMaintenance = subList.every((t) => t.status === "MAINTENANCE");

    let status: TableStatus = "AVAILABLE";
    if (hasOccupied) {
      status = "OCCUPIED";
    } else if (hasReserved) {
      status = "RESERVED";
    } else if (allMaintenance) {
      status = "MAINTENANCE";
    }

    let totalAvailableSeats: number | null = null;
    if (hasOccupied) {
      totalAvailableSeats = subList.reduce((sum, t) => {
        if (t.status === "AVAILABLE") return sum + t.capacity;
        if (t.status === "OCCUPIED") return sum + (t.availableSeats ?? 0);
        return sum;
      }, 0);
    }

    return {
      ...first,
      tableNumber: baseNumber,
      baseTableNumber: baseNumber,
      capacity: totalCapacity,
      availableSeats: totalAvailableSeats,
      status,
      subTables: subList,
    };
  });
}

/**
 * Chuyển danh sách bàn thành từng bàn lẻ hiển thị độc lập trên POS (không gộp bàn).
 */
export function toIndividualTables(rawTables: TableResponse[]): ConsolidatedTable[] {
  return rawTables.map((t) => ({
    ...t,
    baseTableNumber: t.tableNumber,
    subTables: [t],
  }));
}

export type StaffPOSProps = {
  focusedTarget?: { tableId: string; orderId?: string; nonce: number } | null;
  onClearFocusedTarget?: () => void;
  onOpenBookingsTab?: (opts?: { tab?: "needs-action" | "today" }) => void;
};

/**
 * Màn hình phục vụ: chọn bàn để xem thực đơn, gọi món và chuyển sang thanh toán.
 */
export function StaffPOS({ focusedTarget, onClearFocusedTarget, onOpenBookingsTab }: StaffPOSProps = {}) {
  const queryClient = useQueryClient();
  const [tableSelection, setSelectedTable] = useState<{ table: ConsolidatedTable; orderId?: string } | null>(null);
  const [bill, setBill] = useState<{ table: ConsolidatedTable; order: OrderResponse } | null>(null);
  const [reassignBooking, setReassignBooking] = useState<ReservationResponse | null>(null);

  const zonesQuery = useQuery({
    queryKey: ["zones"],
    queryFn: () => zoneApi.list(),
    staleTime: 60_000,
  });

  const tablesQuery = useQuery({
    queryKey: ["tables"],
    queryFn: () => tableApi.list(),
    staleTime: 0,
    refetchInterval: 5000,
    refetchOnWindowFocus: true,
  });

  const today = localDateString();
  const reservationsQuery = useQuery({
    queryKey: ["staff-pos-reservations", today],
    queryFn: () => reservationApi.listByDate(today),
    staleTime: 0,
    refetchInterval: 10000,
    refetchOnWindowFocus: true,
  });

  const pendingReservationsQuery = useQuery({
    queryKey: ["admin-reservations", undefined, "pending", undefined],
    queryFn: async () => (await reservationApi.listAdminBookings?.({ scope: "pending" })) ?? [],
    staleTime: 15_000,
    refetchInterval: 15_000,
  });

  const invalidateAllPosAndBookings = () => {
    void queryClient.invalidateQueries({ queryKey: ["tables"] });
    void queryClient.invalidateQueries({ queryKey: ["staff-pos-reservations"] });
    void queryClient.invalidateQueries({ queryKey: ["admin-reservations"] });
    void queryClient.invalidateQueries({ queryKey: ["orders"] });
    void queryClient.invalidateQueries({ queryKey: ["floor-plan-layouts"] });
  };

  useTableWebSocket({
    onTableStatusChange: invalidateAllPosAndBookings,
    onFloorPlanChange: () => void queryClient.invalidateQueries({ queryKey: ["tables"] }),
    onReservationChange: invalidateAllPosAndBookings,
  });

  const zones: ZoneResponse[] = zonesQuery.data ?? [];
  const tables: TableResponse[] = tablesQuery.data ?? [];
  const todayReservations: ReservationResponse[] = reservationsQuery.data ?? [];
  const pendingBookings: ReservationResponse[] = pendingReservationsQuery.data ?? [];
  const todayConfirmedCount = todayReservations.filter((r) => r.status === "CONFIRMED").length;

  // Hiển thị từng bàn lẻ, không gộp bàn
  const individualTables: ConsolidatedTable[] = useMemo(
    () => toIndividualTables(tables),
    [tables],
  );

  useEffect(() => {
    if (!focusedTarget || individualTables.length === 0) return;
    const match = individualTables.find(
      (t) => t.id === focusedTarget.tableId || t.subTables.some((st) => st.id === focusedTarget.tableId),
    );
    if (match) {
      setSelectedTable({
        table: match,
        ...(focusedTarget.orderId ? { orderId: focusedTarget.orderId } : {}),
      });
      onClearFocusedTarget?.();
    }
  }, [focusedTarget, individualTables, onClearFocusedTarget]);

  const matchesReservationTable = (r: ReservationResponse, table: TableResponse | ConsolidatedTable): boolean => {
    const subIds = (table as ConsolidatedTable).subTables?.map((t) => t.id) ?? [table.id];
    if (r.tableId && subIds.includes(r.tableId)) return true;
    if (r.mergedTableIds && r.mergedTableIds.split(",").map((s) => s.trim()).some((id) => subIds.includes(id))) return true;
    if (r.tableNumber) {
      const assignedNumbers = r.tableNumber.split("+").map((s) => s.trim()).filter(Boolean);
      if (assignedNumbers.includes(table.tableNumber) || subIds.some((id) => r.tableNumber?.includes(id))) {
        return true;
      }
    }
    return false;
  };

  const classifyReservationsForTable = (table: TableResponse | ConsolidatedTable) => {
    const matched = todayReservations.filter(
      (r) => ["CONFIRMED", "ARRIVED"].includes(r.status) && matchesReservationTable(r, table),
    );
    const arrivedList = matched.filter((r) => r.status === "ARRIVED");
    const confirmedList = matched
      .filter((r) => r.status === "CONFIRMED")
      .sort((a, b) => (a.reservationTime || "").localeCompare(b.reservationTime || ""));
    const activeConfirmed =
      confirmedList.find((r) => isWithinReservationWindow(r)) ??
      (table.status === "RESERVED" ? confirmedList[0] : undefined);
    const upcomingConfirmed = confirmedList.filter((r) => {
      if (r.id === activeConfirmed?.id) return false;
      const dt = reservationDateTime(r);
      return dt ? dt.getTime() > Date.now() : false;
    });
    return {
      arrivedList,
      activeConfirmed,
      upcomingConfirmed,
      primaryReservation: activeConfirmed ?? arrivedList[0] ?? upcomingConfirmed[0],
    };
  };

  const selectedTable = tableSelection && (individualTables.find(
    (table) => table.id === tableSelection.table.id || table.subTables.some((part) => part.id === tableSelection.table.id)
  ) ?? tableSelection.table);
  const selectedTableResInfo = selectedTable ? classifyReservationsForTable(selectedTable) : undefined;
  const selectedReservation = selectedTableResInfo?.primaryReservation;
  const exclusiveTable = selectedTableResInfo?.arrivedList.length
    ? selectedTable?.subTables.find((part) =>
        part.status === "OCCUPIED" &&
        selectedTableResInfo.arrivedList.some(
          (r) =>
            (Boolean(r.zoneIsVip) || Boolean(r.mergedTableIds?.includes(","))) &&
            (r.tableId === part.id || r.mergedTableIds?.split(",").some((id) => id.trim() === part.id)),
        ),
      )
    : undefined;
  const updateSelectedTable = (updated: TableResponse) => {
    queryClient.setQueryData<TableResponse[]>(["tables"], (current) =>
      current?.map((table) => (table.id === updated.id ? updated : table)),
    );
  };

  const tablesByZone = useMemo(() => {
    const map = new Map<string, ConsolidatedTable[]>();
    for (const z of zones) {
      map.set(z.id, []);
    }
    const defaultList: ConsolidatedTable[] = [];
    for (const t of individualTables) {
      if (t.zoneId && map.has(t.zoneId)) {
        map.get(t.zoneId)!.push(t);
      } else {
        defaultList.push(t);
      }
    }
    if (defaultList.length > 0) {
      map.set("other", defaultList);
    }
    return map;
  }, [zones, individualTables]);

  const counts = (status: TableStatus) => individualTables.filter((item) => item.status === status).length;

  return (
    <div className="space-y-8">
      <div className="space-y-4">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="text-xs uppercase tracking-[0.2em] text-muted-foreground">Sơ đồ bàn ăn</p>
            <h1 className="text-4xl font-semibold">Phục vụ & Thu ngân</h1>
          </div>
          <div className="flex flex-wrap gap-2">
            {(Object.keys(TABLE_STATUS_LABELS) as TableStatus[]).map((status) => (
              <span
                key={status}
                className={cn("rounded-full px-3 py-1 text-xs font-medium", REAL_STATUS_CLASSES[status])}
              >
                {TABLE_STATUS_LABELS[status]} · {counts(status)}
              </span>
            ))}
          </div>
        </div>
        {onOpenBookingsTab && (
          <div className="flex flex-wrap items-center gap-2 rounded-xl border border-border/70 bg-secondary/40 px-4 py-2.5 text-xs">
            <span className="font-medium text-muted-foreground">Liên kết Đặt Bàn:</span>
            <button
              type="button"
              onClick={() => onOpenBookingsTab({ tab: "needs-action" })}
              className="rounded-full border border-amber-500/40 bg-amber-500/10 px-3 py-1 font-medium text-amber-800 hover:bg-amber-500/20 dark:text-amber-300 cursor-pointer"
            >
              Chờ duyệt đặt bàn · {pendingBookings.length}
            </button>
            <button
              type="button"
              onClick={() => onOpenBookingsTab({ tab: "upcoming" })}
              className="rounded-full border border-sky-500/40 bg-sky-500/10 px-3 py-1 font-medium text-sky-800 hover:bg-sky-500/20 dark:text-sky-300 cursor-pointer"
            >
              Lịch đặt hôm nay · {todayConfirmedCount}
            </button>
          </div>
        )}
      </div>

      {tablesQuery.isError && (
        <p role="alert" className="rounded-md border border-alert/30 bg-alert/5 px-3 py-2 text-sm text-alert">
          {tablesQuery.error instanceof Error
            ? tablesQuery.error.message
            : "Không tải được danh sách bàn từ cơ sở dữ liệu."}
        </p>
      )}

      {tablesQuery.isLoading && (
        <p className="text-sm text-muted-foreground">Đang tải danh sách bàn và khu vực...</p>
      )}

      {zones.map((zone) => {
        const zoneTables = tablesByZone.get(zone.id) ?? [];
        if (zoneTables.length === 0) return null;
        return (
          <section key={zone.id}>
            <div className="mb-3 flex items-center gap-2">
              <h2 className="text-2xl font-medium text-moss">{zone.zoneName}</h2>
              {zone.isVip && (
                <span className="rounded-full bg-amber-500/10 px-2 py-0.5 text-xs font-medium text-amber-700 dark:text-amber-400 border border-amber-500/30">
                  VIP
                </span>
              )}
            </div>
            <div className="grid grid-cols-2 gap-5 sm:grid-cols-3 lg:grid-cols-5">
              {zoneTables.map((item) => {
                const resInfo = classifyReservationsForTable(item);
                const reservation = resInfo.activeConfirmed ?? resInfo.arrivedList[0];
                const nextUpcoming = resInfo.upcomingConfirmed[0];
                const isMaintenance = item.status === "MAINTENANCE";
                const statusLabel = TABLE_STATUS_LABELS[item.status] ?? item.status;
                const statusClass = REAL_STATUS_CLASSES[item.status] ?? "status-available";

                return (
                  <button
                    key={item.id}
                    onClick={() => setSelectedTable({ table: item })}
                    className="surface-card group p-5 text-left transition hover:-translate-y-0.5 hover:border-primary/40 cursor-pointer"
                  >
                    <div className="flex items-start justify-between">
                      <span className="font-display text-2xl font-semibold">{item.tableNumber}</span>
                      {isMaintenance && <Wrench className="h-4 w-4 text-muted-foreground" />}
                    </div>
                    <p className="mt-1 flex flex-wrap items-center gap-1 text-xs text-muted-foreground">
                      <Users className="h-3 w-3" aria-hidden="true" />
                      {item.status === "OCCUPIED" && item.availableSeats != null
                        ? `${item.availableSeats}/${item.capacity} ghế trống`
                        : `${item.capacity} ghế`}
                    </p>
                    <span className={cn("mt-4 inline-block rounded-full px-2.5 py-0.5 text-xs font-medium", statusClass)}>
                      {statusLabel}
                    </span>
                    {item.status === "RESERVED" && reservation && (
                      <p className="mt-2 text-xs text-muted-foreground">
                        <span className="block font-semibold text-primary">{bookingCode(reservation.id)}</span>
                        <span className="block font-medium text-foreground">{reservation.guestName} · {reservation.partySize} khách</span>
                        <span className="block">{reservation.reservationTime.slice(0, 5)} · {reservation.guestPhone}</span>
                        {reservation.tableNumber?.includes("+") && (
                          <span className="block text-[11px] text-amber-700 dark:text-amber-400">
                            Ghép: {reservation.tableNumber}
                          </span>
                        )}
                      </p>
                    )}
                    {item.status === "OCCUPIED" && resInfo.arrivedList.length > 0 && (
                      <div className="mt-2 space-y-0.5 text-xs text-muted-foreground">
                        <span className="inline-block rounded bg-blue-500/10 px-1.5 py-0.5 text-[10px] font-semibold text-blue-700 dark:text-blue-300">
                          Đặt trước · {bookingCode(resInfo.arrivedList[0]!.id)}
                        </span>
                        <span className="block font-medium text-foreground">
                          {resInfo.arrivedList[0]!.guestName} · {resInfo.arrivedList[0]!.reservationTime.slice(0, 5)}
                        </span>
                        {resInfo.arrivedList[0]!.depositAmount > 0 && (
                          <span className="block text-[10px] font-medium text-emerald-700 dark:text-emerald-400">
                            Đã cọc VIP 250k
                          </span>
                        )}
                      </div>
                    )}
                    {item.status !== "RESERVED" && nextUpcoming && (
                      <p className="mt-2 text-[11px] font-medium text-amber-700 dark:text-amber-400">
                        Lịch đặt {nextUpcoming.reservationTime.slice(0, 5)} · {nextUpcoming.guestName}
                      </p>
                    )}
                  </button>
                );
              })}
            </div>
          </section>
        );
      })}

      {selectedTable?.status === "RESERVED" && (
        <ReservationDialog
          table={selectedTable}
          reservation={selectedTableResInfo?.activeConfirmed ?? selectedReservation}
          onClose={() => setSelectedTable(null)}
          onTableUpdated={updateSelectedTable}
          onReassign={(res) => setReassignBooking(res)}
        />
      )}

      {selectedTable && selectedTable.status !== "RESERVED" && (
        <OrderDialog
          key={selectedTable.id}
          table={selectedTable}
          initialOrderId={tableSelection?.orderId}
          exclusiveTableId={exclusiveTable?.id}
          arrivedReservations={selectedTableResInfo?.arrivedList ?? []}
          upcomingReservations={selectedTableResInfo?.upcomingConfirmed ?? []}
          onClose={() => setSelectedTable(null)}
          onTableUpdated={updateSelectedTable}
          onBill={(order) => {
            setBill({ table: selectedTable, order });
            setSelectedTable(null);
          }}
        />
      )}

      {bill && (
        <BillingDialog table={bill.table} order={bill.order} onClose={() => {
          setSelectedTable({ table: bill.table, orderId: bill.order.id });
          setBill(null);
        }} />
      )}

      <ConfirmBookingDialog
        booking={reassignBooking}
        open={Boolean(reassignBooking)}
        onOpenChange={(open) => {
          if (!open) setReassignBooking(null);
        }}
      />
    </div>
  );
}

function ReservationDialog({
  table,
  reservation,
  onClose,
  onTableUpdated,
  onReassign,
}: {
  table: TableResponse;
  reservation?: ReservationResponse | undefined;
  onClose: () => void;
  onTableUpdated?: (table: TableResponse) => void;
  onReassign?: (reservation: ReservationResponse) => void;
}) {
  const queryClient = useQueryClient();
  const [confirmStatusChange, setConfirmStatusChange] = useState<{
    status: TableStatus;
    action?: "CANCEL" | "NO_SHOW";
  } | null>(null);

  const subTables: TableResponse[] = (table as any).subTables ?? [table];
  const checkInTiming = evaluateCheckInWindow(reservation);

  const invalidateLinkedQueries = () => {
    void queryClient.invalidateQueries({ queryKey: ["tables"] });
    void queryClient.invalidateQueries({ queryKey: ["staff-pos-reservations"] });
    void queryClient.invalidateQueries({ queryKey: ["admin-reservations"] });
    void queryClient.invalidateQueries({ queryKey: ["orders"] });
    void queryClient.invalidateQueries({ queryKey: ["floor-plan-layouts"] });
  };

  const changeStatusMutation = useMutation({
    mutationFn: async (payload: {
      id: string;
      status: TableStatus;
      reservationAction?: "CANCEL" | "NO_SHOW";
    }) => {
      let lastUpdated = table;
      for (const st of subTables) {
        lastUpdated = await tableApi.updateStatus(st.id, payload.status, {
          ...(reservation ? { reservationId: reservation.id } : {}),
          ...(payload.reservationAction ? { reservationAction: payload.reservationAction } : {}),
        });
      }
      return lastUpdated;
    },
    onSuccess: (updated) => {
      toast.success(`${table.tableNumber}: ${TABLE_STATUS_LABELS[updated.status]}.`);
      invalidateLinkedQueries();
      setConfirmStatusChange(null);
      if (onTableUpdated) onTableUpdated(updated);
    },
    onError: (error: Error) => toast.error(error.message),
  });

  const checkInMutation = useMutation({
    mutationFn: async () => {
      if (reservation) {
        await reservationApi.updateReservationStatus(reservation.id, { status: "ARRIVED" });
        return { ...table, status: "OCCUPIED" as TableStatus };
      }
      let updatedTable = table;
      for (const st of subTables) {
        if (st.status !== "OCCUPIED") {
          updatedTable = await tableApi.updateStatus(st.id, "OCCUPIED");
        }
      }
      return updatedTable;
    },
    onSuccess: (updatedTable) => {
      toast.success(`Check-in thành công! Bàn ${table.tableNumber} đã chuyển sang Đang phục vụ.`);
      invalidateLinkedQueries();
      onTableUpdated?.(updatedTable);
    },
    onError: (error: Error) => toast.error(error.message),
  });

  const handleManualStatusSelect = (val: string) => {
    const nextStatus = val as TableStatus;
    if (nextStatus === table.status) return;
    if (nextStatus === "OCCUPIED") {
      if (reservation) {
        checkInMutation.mutate();
      } else {
        changeStatusMutation.mutate({ id: table.id, status: "OCCUPIED" });
      }
      return;
    }
    if (reservation && (nextStatus === "AVAILABLE" || nextStatus === "MAINTENANCE")) {
      const bookingDt = reservationDateTime(reservation);
      const defaultAction: "CANCEL" | "NO_SHOW" =
        bookingDt && Date.now() > bookingDt.getTime() ? "NO_SHOW" : "CANCEL";
      setConfirmStatusChange({
        status: nextStatus,
        ...(nextStatus === "AVAILABLE" ? { action: defaultAction } : {}),
      });
      return;
    }
    changeStatusMutation.mutate({ id: table.id, status: nextStatus });
  };

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle className="font-display text-3xl">
            {reservation?.tableNumber?.includes("+") ? `${reservation.tableNumber}` : table.tableNumber} · Đã đặt trước
          </DialogTitle>
          <DialogDescription>
            {reservation ? `Đơn ${bookingCode(reservation.id)} — Thông tin khách đặt bàn` : "Thông tin khách đặt bàn"}
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-2.5 rounded-lg bg-muted p-4 text-sm">
          <div className="flex items-center justify-between gap-2">
            <p className="font-display text-xl font-semibold">{reservation?.guestName ?? "Khách đặt trước"}</p>
            {reservation && (
              <span className="rounded-full bg-primary/10 px-2.5 py-0.5 font-mono text-xs font-semibold text-primary">
                {bookingCode(reservation.id)}
              </span>
            )}
          </div>
          <p className="flex items-center gap-2">
            <Clock className="h-4 w-4" /> {reservation?.reservationTime?.slice(0, 5) ?? "--:--"} · {reservation?.reservationDate ?? "Hôm nay"}
          </p>
          <p className="flex items-center gap-2">
            <Users className="h-4 w-4" /> {reservation?.partySize ?? table.capacity} khách
          </p>
          <p className="flex items-center gap-2">
            <Phone className="h-4 w-4" /> {reservation?.guestPhone ?? "Đang cập nhật SĐT"}
          </p>
          {reservation && reservation.depositAmount > 0 && (
            <p className="text-xs font-semibold text-emerald-700 dark:text-emerald-400">
              Đã thanh toán phí thu & cọc phòng VIP: {formatVND(reservation.depositAmount)}
            </p>
          )}
          {reservation?.specialRequests && <p className="text-muted-foreground">Ghi chú: {reservation.specialRequests}</p>}
        </div>

        {reservation && !checkInTiming.allowed && (
          <p className="flex items-center gap-1.5 rounded-md border border-amber-500/40 bg-amber-500/10 px-3 py-2 text-xs font-medium text-amber-700 dark:text-amber-300">
            <AlertTriangle className="h-4 w-4 shrink-0" />
            {checkInTiming.reason}
          </p>
        )}

        <div className="flex flex-wrap gap-2">
          <Button
            className="flex-1 bg-emerald-600 hover:bg-emerald-700 text-white font-semibold py-2.5"
            disabled={checkInMutation.isPending || (Boolean(reservation) && !checkInTiming.allowed)}
            onClick={() => checkInMutation.mutate()}
          >
            <UserCheck className="mr-2 h-4 w-4" />
            {checkInMutation.isPending ? "Đang xác nhận…" : "Xác nhận khách đến (Check-in & Mở bàn)"}
          </Button>
          {reservation && onReassign && (
            <Button
              type="button"
              variant="outline"
              onClick={() => onReassign(reservation)}
              disabled={checkInMutation.isPending || changeStatusMutation.isPending}
            >
              <ArrowRightLeft className="mr-1.5 h-4 w-4" />
              Đổi bàn
            </Button>
          )}
        </div>

        <div className="flex flex-wrap items-center justify-between gap-2 border-t pt-3">
          <span className="text-xs text-muted-foreground">Chuyển trạng thái bàn:</span>
          <Select
            value={table.status}
            disabled={changeStatusMutation.isPending || checkInMutation.isPending}
            onValueChange={handleManualStatusSelect}
          >
            <SelectTrigger className="h-8 w-[11rem] bg-card">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {(Object.keys(TABLE_STATUS_LABELS) as TableStatus[]).map((status) => (
                <SelectItem key={status} value={status}>
                  {TABLE_STATUS_LABELS[status]}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <AlertDialog open={Boolean(confirmStatusChange)} onOpenChange={(o) => !o && setConfirmStatusChange(null)}>
          <AlertDialogContent>
            <AlertDialogHeader>
              <AlertDialogTitle>Đồng bộ trạng thái bàn & đặt bàn</AlertDialogTitle>
              <AlertDialogDescription>
                {confirmStatusChange?.status === "MAINTENANCE"
                  ? `Chuyển ${table.tableNumber} sang Bảo trì sẽ tự động đưa đơn ${reservation ? bookingCode(reservation.id) : ""} về trạng thái Chờ xác nhận để xếp bàn mới.`
                  : `Chuyển ${table.tableNumber} về Trống sẽ tự động cập nhật đơn ${reservation ? bookingCode(reservation.id) : ""} thành ${confirmStatusChange?.action === "NO_SHOW" ? "Không đến (No-Show)" : "Đã hủy"}.`}
              </AlertDialogDescription>
            </AlertDialogHeader>
            <AlertDialogFooter>
              <AlertDialogCancel disabled={changeStatusMutation.isPending}>Quay lại</AlertDialogCancel>
              {confirmStatusChange?.status === "AVAILABLE" && (
                <AlertDialogAction
                  disabled={changeStatusMutation.isPending}
                  onClick={(e) => {
                    e.preventDefault();
                    changeStatusMutation.mutate({
                      id: table.id,
                      status: "AVAILABLE",
                      reservationAction: confirmStatusChange.action ?? "CANCEL",
                    });
                  }}
                >
                  {changeStatusMutation.isPending ? "Đang xử lý…" : "Xác nhận"}
                </AlertDialogAction>
              )}
              {confirmStatusChange?.status === "MAINTENANCE" && (
                <AlertDialogAction
                  disabled={changeStatusMutation.isPending}
                  onClick={(e) => {
                    e.preventDefault();
                    changeStatusMutation.mutate({ id: table.id, status: "MAINTENANCE" });
                  }}
                >
                  {changeStatusMutation.isPending ? "Đang xử lý…" : "Xác nhận bảo trì"}
                </AlertDialogAction>
              )}
            </AlertDialogFooter>
          </AlertDialogContent>
        </AlertDialog>
      </DialogContent>
    </Dialog>
  );
}

function OrderDialog({
  table,
  initialOrderId,
  exclusiveTableId,
  arrivedReservations = [],
  upcomingReservations = [],
  onClose,
  onBill,
  onTableUpdated,
}: {
  table: ConsolidatedTable;
  initialOrderId: string | undefined;
  exclusiveTableId: string | undefined;
  arrivedReservations?: ReservationResponse[];
  upcomingReservations?: ReservationResponse[];
  onClose: () => void;
  onBill: (order: OrderResponse) => void;
  onTableUpdated?: (table: TableResponse) => void;
}) {
  const queryClient = useQueryClient();
  const exclusive = Boolean(exclusiveTableId);
  const [selectedCategory, setSelectedCategory] = useState<string>(ALL_CATEGORIES);
  const [cartsByTarget, setCartsByTarget] = useState<Record<string, CartLine[]>>({});
  const [confirmSendOpen, setConfirmSendOpen] = useState(false);

  const menuQuery = useQuery({
    queryKey: ["menu"],
    queryFn: () => menuApi.getOverview(),
    staleTime: 60000,
  });
  const categories = useMemo(() => menuQuery.data?.categories ?? [], [menuQuery.data]);
  const dishes = useMemo(() => categories.flatMap((c) => c.items ?? []), [categories]);
  const dishMap = useMemo(() => new Map(dishes.map((d) => [d.id, d])), [dishes]);
  const activeCategory =
    selectedCategory === ALL_CATEGORIES || categories.some((c) => c.id === selectedCategory)
      ? selectedCategory
      : ALL_CATEGORIES;
  const visibleDishes = useMemo(
    () =>
      activeCategory === ALL_CATEGORIES
        ? dishes
        : (categories.find((c) => c.id === activeCategory)?.items ?? []),
    [activeCategory, categories, dishes],
  );

  const subIds = table.subTables.map((part) => part.id);
  const seatedKey = ["orders", "table", table.id, "seated", subIds.join(",")];
  const seatedQuery = useQuery({
    queryKey: seatedKey,
    queryFn: async () => {
      const results = await Promise.all(subIds.map((id) => orderApi.seatedAtTable(id)));
      return [...new Map(results.flat().map((order) => [order.id, order])).values()];
    },
    refetchInterval: 5000,
  });
  const orders = seatedQuery.data ?? [];
  const canCloseTable = seatedQuery.isSuccess && orders.length === 0;
  const [selectedOrderId, setSelectedOrderId] = useState(initialOrderId);
  const selectedOrder = selectedOrderId ? orders.find((order) => order.id === selectedOrderId)
    : orders.length === 1 ? orders[0] : undefined;
  const activeOrder = selectedOrder?.status === "IN_PROGRESS" ? selectedOrder : undefined;
  const [partySize, setPartySize] = useState("1");
  const seatingOptions = table.subTables.filter((part) => part.status === "AVAILABLE" || part.status === "OCCUPIED")
    .map((part) => {
      const used = orders.filter((order) => order.tableId === part.id)
        .reduce((sum, order) => sum + (order.partySize ?? part.capacity), 0);
      return { part, free: Math.max(0, part.capacity - used) };
    });
  const freeSeats = seatingOptions.reduce((sum, option) => sum + option.free, 0);
  const maxPartySize = Math.max(0, ...seatingOptions.map((option) => option.free));
  const openOrder = useMutation({
    mutationFn: async ({ part, size }: { part: TableResponse; size: number }) => {
      if (part.status === "AVAILABLE") {
        const opened = await tableApi.updateStatus(part.id, "OCCUPIED");
        onTableUpdated?.(opened);
      }
      return orderApi.openSeatedOrder(part.id, size);
    },
    onSuccess: (order) => {
      queryClient.setQueryData<OrderResponse[]>(seatedKey, (current) => [
        ...(current ?? []).filter((other) => other.id !== order.id), order,
      ]);
      setSelectedOrderId(order.id);
      setPartySize("1");
      void queryClient.invalidateQueries({ queryKey: ["orders"] });
      void queryClient.invalidateQueries({ queryKey: ["tables"] });
    },
    onError: (error: Error) => {
      toast.error(error.message);
      void queryClient.invalidateQueries({ queryKey: ["orders"] });
    },
  });
  const releaseSeats = useMutation({
    mutationFn: ({ orderId, reservationAction }: { orderId: string; seated: boolean; reservationAction?: "CANCEL" | "REVERT_CONFIRMED" }) =>
      reservationAction
        ? orderApi.releaseSeats(orderId, { reservationAction })
        : orderApi.releaseSeats(orderId),
    onSuccess: (_, { orderId, seated, reservationAction }) => {
      queryClient.setQueryData<OrderResponse[]>(seatedKey, (current) => current?.filter((order) => order.id !== orderId));
      void queryClient.invalidateQueries({ queryKey: ["orders"] });
      void queryClient.invalidateQueries({ queryKey: ["tables"] });
      void queryClient.invalidateQueries({ queryKey: ["staff-pos-reservations"] });
      void queryClient.invalidateQueries({ queryKey: ["admin-reservations"] });
      void queryClient.invalidateQueries({ queryKey: ["reservations"] });
      void queryClient.invalidateQueries({ queryKey: ["floor-plan-layouts"] });
      toast.success(
        reservationAction === "REVERT_CONFIRMED"
          ? `Đã hoàn tác Check-in và giữ lại đặt bàn cho đơn ${orderCode(orderId)}.`
          : seated
            ? `Đã trả chỗ cho đơn ${orderCode(orderId)}.`
            : `Đã hủy đơn trống ${orderCode(orderId)}.`,
      );
    },
    onError: (error: Error) => toast.error(error.message),
  });
  const createOrder = useCreateOrder();
  const changeItems = useChangeOrderItems(table.id, seatedKey);
  const saveNote = useChangeOrderItems(table.id, seatedKey);
  const changingQuantity = changeItems.isPending || releaseSeats.isPending || createOrder.isPending;

  const [notes, setNotes] = useState<Record<string, string>>({});
  const [cancelTarget, setCancelTarget] = useState<{ orderId: string; group: OrderItemGroup } | null>(null);
  const [cancelReason, setCancelReason] = useState("Khách đổi món");

  const updateTableStatus = useMutation({
    mutationFn: async (status: TableStatus) => {
      const parts = status === "AVAILABLE" && table.status === "OCCUPIED"
        ? table.subTables.filter((part) => part.status === "OCCUPIED")
        : [table.subTables.find((part) => part.status === table.status) ?? table];
      for (const part of parts) {
        const updated = await tableApi.updateStatus(part.id, status);
        onTableUpdated?.(updated);
      }
    },
    onSuccess: (_, status) => toast.success(`${table.tableNumber}: ${TABLE_STATUS_LABELS[status]}.`),
    onError: (error: Error) => toast.error(error.message),
    onSettled: () => void queryClient.invalidateQueries({ queryKey: ["tables"] }),
  });

  useKitchenCompleted((message) => {
    if (message.tableId !== table.id && !orders.some((order) => order.id === message.orderId)) return;
    if (message.status === "READY") {
      toast.success(`${message.tableNumber} / ${orderCode(message.orderId)}: ${message.itemName} đã sẵn sàng.`);
    }
    void queryClient.invalidateQueries({ queryKey: ["orders"] });
  });

  const isOccupied = table.status === "OCCUPIED";
  const orderId = activeOrder?.id;
  const hasOpenOrder = Boolean(orderId);
  const legacyCart = (exclusive || arrivedReservations.length > 0) && seatedQuery.isSuccess && orders.length === 0;
  const showMenu = isOccupied && seatedQuery.isSuccess && (hasOpenOrder || legacyCart);
  const cartTargetKey = orderId ?? LEGACY_CART_KEY;
  const cart = cartsByTarget[cartTargetKey] ?? [];

  const updateCart = (updater: (current: CartLine[]) => CartLine[], targetKey = cartTargetKey) =>
    setCartsByTarget((prev) => {
      const nextLines = updater(prev[targetKey] ?? []);
      if (nextLines.length === 0) {
        if (!(targetKey in prev)) return prev;
        const { [targetKey]: _removed, ...rest } = prev;
        return rest;
      }
      return { ...prev, [targetKey]: nextLines };
    });

  const clearCart = (targetKey = cartTargetKey) =>
    setCartsByTarget((prev) => {
      if (!(targetKey in prev)) return prev;
      const { [targetKey]: _removed, ...rest } = prev;
      return rest;
    });

  const addToCart = (menuItemId: string, note?: string) => {
    const normalizedNote = note?.trim() ?? "";
    const lineKey = normalizedNote ? `${menuItemId}::${normalizedNote}` : menuItemId;
    updateCart((cur) => {
      const existing = cur.find((l) => l.key === lineKey);
      if (!existing) {
        return [...cur, { key: lineKey, menuItemId, quantity: 1, note: normalizedNote }];
      }
      if (existing.quantity >= MAX_CART_QTY) return cur;
      return cur.map((l) => (l.key === lineKey ? { ...l, quantity: l.quantity + 1 } : l));
    });
  };

  const changeCartLine = (lineKey: string, delta: number) =>
    updateCart((cur) =>
      cur
        .map((l) =>
          l.key === lineKey
            ? { ...l, quantity: Math.min(MAX_CART_QTY, l.quantity + delta) }
            : l,
        )
        .filter((l) => l.quantity > 0),
    );

  const removeCartLine = (lineKey: string) =>
    updateCart((cur) => cur.filter((l) => l.key !== lineKey));

  const decrementMenuDish = (menuItemId: string) =>
    updateCart((cur) => {
      const plainLine = cur.find((l) => l.key === menuItemId);
      const targetLine =
        plainLine ?? [...cur].reverse().find((l) => l.menuItemId === menuItemId);
      if (!targetLine) return cur;
      return cur
        .map((l) => (l.key === targetLine.key ? { ...l, quantity: l.quantity - 1 } : l))
        .filter((l) => l.quantity > 0);
    });

  const setCartLineNote = (lineKey: string, note: string) =>
    updateCart((cur) => cur.map((l) => (l.key === lineKey ? { ...l, note } : l)));

  const draftQty = (menuItemId: string) =>
    cart.reduce((sum, l) => (l.menuItemId === menuItemId ? sum + l.quantity : sum), 0);

  const dishName = (menuItemId: string) => dishMap.get(menuItemId)?.itemName ?? "";
  const totalCartQty = cart.reduce((sum, l) => sum + l.quantity, 0);
  const cartSubtotal = cart.reduce(
    (sum, l) => sum + (dishMap.get(l.menuItemId)?.currentPrice ?? 0) * l.quantity,
    0,
  );
  const isSubmittingCart = createOrder.isPending || changeItems.isPending;
  const canSubmitCart = showMenu && cart.length > 0 && !isSubmittingCart;
  const hasPayableItems = activeOrder?.items.some((item) => item.cookingStatus !== "CANCELLED");
  const parsedPartySize = Number(partySize);
  const canOpenOrder = isOccupied && !exclusive && seatedQuery.isSuccess && maxPartySize > 0 && !openOrder.isPending && !updateTableStatus.isPending;

  const items = activeOrder?.items ?? [];
  const itemGroups = groupOrderItems(items);
  const orderedQty = new Map<string, number>();
  for (const item of items) {
    if (item.cookingStatus !== "CANCELLED") {
      orderedQty.set(item.menuItemId, (orderedQty.get(item.menuItemId) ?? 0) + item.quantity);
    }
  }
  const groupPayload = (group: OrderItemGroup) => ({
    itemIds: group.itemIds,
    expectedStatus: group.first.cookingStatus,
    expectedQuantity: group.quantity,
  });

  const submitCartToKitchen = () => {
    if (!canSubmitCart) return;
    const submittedTargetKey = cartTargetKey;
    const submittedCount = totalCartQty;
    const payloadItems = cart.map((line) => ({
      menuItemId: line.menuItemId,
      quantity: line.quantity,
      note: line.note.trim() || undefined,
    }));

    if (orderId) {
      changeItems.mutate(
        { orderId, action: "ADD_BATCH", payload: { items: payloadItems } },
        {
          onSuccess: () => {
            clearCart(submittedTargetKey);
            setConfirmSendOpen(false);
            toast.success(`Đã gửi ${submittedCount} phần món xuống bếp`);
          },
          onError: () => {
            setConfirmSendOpen(false);
          },
        },
      );
      return;
    }

    createOrder.mutate(
      {
        tableId: exclusiveTableId ?? table.id,
        items: payloadItems,
        submit: true,
      },
      {
        onSuccess: (order) => {
          queryClient.setQueryData<OrderResponse[]>(seatedKey, (current) => [
            ...(current ?? []).filter((other) => other.id !== order.id),
            order,
          ]);
          setSelectedOrderId(order.id);
          clearCart(submittedTargetKey);
          setConfirmSendOpen(false);
          toast.success(`Đã gửi ${submittedCount} phần món xuống bếp`);
        },
        onError: (error: Error) => {
          setConfirmSendOpen(false);
          toast.error(error.message);
        },
      },
    );
  };

  const confirmCancellation = (group: OrderItemGroup) => {
    if (!orderId) return;
    setCancelReason("Khách đổi món");
    setCancelTarget({ orderId, group });
  };

  const decrement = (group: OrderItemGroup) => {
    if (!orderId || group.first.cookingStatus !== "QUEUED") return;
    if (group.quantity === 1) {
      confirmCancellation(group);
      return;
    }
    changeItems.mutate({ orderId, action: "ADJUST", payload: { ...groupPayload(group), action: "DECREMENT" } });
  };

  const saveItemNote = (group: OrderItemGroup) => {
    if (!orderId) return;
    const { id, note } = group.first;
    const draft = notes[id];
    if (draft == null) return;
    const next = draft.trim();
    const clearDraft = () => setNotes((cur) => {
      if (cur[id] !== draft) return cur;
      const { [id]: _saved, ...rest } = cur;
      return rest;
    });
    if (next === (note ?? "").trim()) {
      clearDraft();
      return;
    }
    saveNote.mutate(
      { orderId, action: "ADJUST", payload: { ...groupPayload(group), action: "UPDATE_NOTE", note: next } },
      { onSuccess: clearDraft },
    );
  };

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-h-[90vh] max-w-3xl overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="font-display text-3xl">
            {selectedOrder?.tableNumber || table.tableNumber} · Gọi món
          </DialogTitle>
          <DialogDescription>
            {exclusive ? "Bàn dành riêng cho lượt đặt trước." : "Chọn đúng mã đơn trước khi gọi món hoặc thanh toán."}
          </DialogDescription>
        </DialogHeader>

        {(() => {
          const linkedRes =
            (selectedOrder?.reservationId
              ? arrivedReservations.find((r) => r.id === selectedOrder.reservationId)
              : undefined) ??
            (selectedOrder?.reservationId
              ? {
                  id: selectedOrder.reservationId,
                  guestName: selectedOrder.reservationGuestName ?? "Khách đặt trước",
                  guestPhone: selectedOrder.reservationGuestPhone ?? "",
                  reservationTime: selectedOrder.reservationTime ?? "",
                  specialRequests: selectedOrder.reservationSpecialRequests ?? null,
                  tableNumber: selectedOrder.reservationTableNumber ?? selectedOrder.tableNumber,
                  depositAmount: selectedOrder.reservationDepositAmount ?? 0,
                  partySize: selectedOrder.partySize ?? table.capacity,
                }
              : arrivedReservations[0]);

          if (!linkedRes) return null;
          const resCode = selectedOrder?.reservationCode || bookingCode(linkedRes.id);
          return (
            <div className="space-y-1.5 rounded-lg border border-blue-500/30 bg-blue-500/5 px-3.5 py-2.5 text-xs">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div className="flex flex-wrap items-center gap-2">
                  <Sparkles className="h-3.5 w-3.5 text-blue-600 dark:text-blue-400 shrink-0" />
                  <span className="font-mono font-bold text-blue-700 dark:text-blue-300">{resCode}</span>
                  <span className="font-semibold text-foreground">{linkedRes.guestName}</span>
                  {linkedRes.guestPhone && (
                    <span className="text-muted-foreground">· {linkedRes.guestPhone}</span>
                  )}
                </div>
                <div className="flex flex-wrap items-center gap-1.5">
                  {linkedRes.reservationTime && (
                    <span className="rounded bg-background px-2 py-0.5 font-medium text-foreground border">
                      Giờ hẹn: {linkedRes.reservationTime.slice(0, 5)}
                    </span>
                  )}
                  {Number(linkedRes.depositAmount || 0) > 0 && (
                    <span className="rounded bg-emerald-500/15 px-2 py-0.5 font-semibold text-emerald-700 dark:text-emerald-300">
                      Đã cọc VIP {formatVND(Number(linkedRes.depositAmount))}
                    </span>
                  )}
                </div>
              </div>
              {linkedRes.tableNumber?.includes("+") && (
                <p className="text-[11px] font-medium text-amber-700 dark:text-amber-300">
                  Cụm bàn gộp: {linkedRes.tableNumber}
                </p>
              )}
              {linkedRes.specialRequests && (
                <p className="text-[11px] font-medium text-foreground">
                  Lưu ý khách đặt: <span className="italic">{linkedRes.specialRequests}</span>
                </p>
              )}
            </div>
          );
        })()}

        {upcomingReservations.length > 0 && (
          <div className="flex items-center gap-2 rounded-lg border border-amber-500/40 bg-amber-500/10 px-3 py-2 text-xs text-amber-800 dark:text-amber-300">
            <AlertTriangle className="h-4 w-4 shrink-0" />
            <span>
              Lưu ý: Bàn có lịch đặt lúc{" "}
              <strong>{upcomingReservations[0]!.reservationTime.slice(0, 5)}</strong> cho{" "}
              <strong>{upcomingReservations[0]!.guestName}</strong> ({upcomingReservations[0]!.partySize} khách).
            </span>
          </div>
        )}

        <div
          className={cn(
            "space-y-2 rounded-lg border px-3 py-2 text-sm",
            isOccupied ? "border-emerald-500/30 bg-emerald-500/5" : "border-amber-500/40 bg-amber-500/10",
          )}
        >
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="flex items-center gap-2">
              <span className="text-xs text-muted-foreground">Trạng thái bàn:</span>
              <span className={cn("inline-block rounded-full px-2.5 py-0.5 text-xs font-medium", REAL_STATUS_CLASSES[table.status])}>
                {TABLE_STATUS_LABELS[table.status]}
              </span>
              {!isOccupied && table.status === "AVAILABLE" && (
                <span className="text-xs text-muted-foreground">Mở bàn trước khi xếp khách</span>
              )}
            </div>

            <div className="flex items-center gap-2">
              {table.status === "AVAILABLE" && (
                <Button
                  className="min-h-11 bg-primary hover:bg-primary-hover"
                  disabled={updateTableStatus.isPending}
                  onClick={() => updateTableStatus.mutate("OCCUPIED")}
                >
                  Mở bàn
                </Button>
              )}
              {isOccupied && canCloseTable && (
                <Button
                  className="min-h-11"
                  variant="outline"
                  disabled={updateTableStatus.isPending || openOrder.isPending}
                  onClick={() => updateTableStatus.mutate("AVAILABLE")}
                >
                  Đóng bàn (Trống)
                </Button>
              )}
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-2 border-t border-border/40 pt-2">
            <span className="text-xs text-muted-foreground">Chuyển trạng thái:</span>
            <Select
              value={table.status}
              disabled={updateTableStatus.isPending || openOrder.isPending}
              onValueChange={(value) => updateTableStatus.mutate(value as TableStatus)}
            >
              <SelectTrigger className="h-8 w-[11rem] bg-card" aria-label="Chuyển trạng thái bàn">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {(Object.keys(TABLE_STATUS_LABELS) as TableStatus[]).map((status) => (
                  <SelectItem key={status} value={status} disabled={status !== "OCCUPIED" && !canCloseTable}>
                    {TABLE_STATUS_LABELS[status]}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            {orders.length > 0 && (
              <p className="w-full text-xs text-muted-foreground">Xử lý các đơn còn mở hoặc còn khách trước khi đổi trạng thái bàn.</p>
            )}
          </div>
        </div>

        <section className="space-y-3 rounded-lg border p-3" aria-label="Đơn tại bàn">
          <div className="flex flex-wrap items-baseline justify-between gap-2">
            <h3 className="font-semibold">Đơn tại bàn</h3>
            {isOccupied && !exclusive && seatedQuery.isSuccess && (
              <span className="text-sm tabular-nums text-muted-foreground">{freeSeats}/{table.capacity} ghế trống</span>
            )}
          </div>
          {seatedQuery.isLoading && <p role="status" className="text-sm">Đang tải các đơn tại bàn…</p>}
          {seatedQuery.isError && (
            <div className="flex flex-wrap items-center gap-2">
              <p role="alert" className="text-sm text-alert">Không tải được các đơn tại bàn.</p>
              <Button className="min-h-11" variant="outline" onClick={() => void seatedQuery.refetch()}>Tải lại</Button>
            </div>
          )}
          {seatedQuery.isSuccess && orders.length === 0 && <p className="text-sm">Bàn chưa có đơn.</p>}
          {seatedQuery.isSuccess && orders.length > 0 && (
            <div className="flex flex-wrap gap-2">
              {orders.map((order) => (
                <Button
                  key={order.id}
                  type="button"
                  aria-pressed={selectedOrder?.id === order.id}
                  variant={selectedOrder?.id === order.id ? "default" : "outline"}
                  className="h-auto min-h-11 max-w-full flex-wrap justify-start gap-x-2 gap-y-0 whitespace-normal px-3 text-left"
                  onClick={() => setSelectedOrderId(order.id)}
                >
                  {orderCode(order.id)}
                  {order.partySize != null ? ` · ${order.partySize} khách` : ""}
                  {order.reservationGuestName ? ` · ${order.reservationGuestName}` : ""}
                  {order.status === "COMPLETED" ? " · Đã trả tiền, còn ngồi" : " · Đang mở"}
                </Button>
              ))}
            </div>
          )}
          {isOccupied && !exclusive && seatedQuery.isSuccess && maxPartySize > 0 && (
            <form className="flex flex-wrap items-end gap-2" onSubmit={(event) => {
              event.preventDefault();
              if (!canOpenOrder || !Number.isInteger(parsedPartySize) || parsedPartySize < 1) return;
              const fits = seatingOptions.filter((option) => option.free >= parsedPartySize);
              const target = fits.find((option) => option.part.status === "OCCUPIED") ?? fits[0];
              if (target) openOrder.mutate({ part: target.part, size: parsedPartySize });
            }}>
              <label className="space-y-1 text-sm">
                <span className="block">Số khách của đơn mới</span>
                <Input className="h-11 w-32" type="number" min={1} max={maxPartySize} required value={partySize}
                  onChange={(event) => setPartySize(event.target.value)} />
              </label>
              <Button className="min-h-11" type="submit" disabled={!canOpenOrder}>
                {openOrder.isPending ? "Đang mở đơn…" : "Mở đơn mới"}
              </Button>
            </form>
          )}
          {seatedQuery.isSuccess && orders.length > 0 && !selectedOrder && (
            <p className="text-sm text-muted-foreground">Chọn một mã đơn để xem món hoặc thanh toán.</p>
          )}
          {seatedQuery.isSuccess && selectedOrder && (selectedOrder.seated || activeOrder) && !hasPayableItems && (
            <div className="space-y-2 border-t pt-3">
              {selectedOrder.status === "COMPLETED" && (
                <p className="text-sm">Chỉ trả chỗ khi khách của đơn {orderCode(selectedOrder.id)} đã rời bàn.</p>
              )}
              <div className="flex flex-wrap gap-2">
                <Button className="min-h-11" variant="outline" disabled={changingQuantity}
                  onClick={() => releaseSeats.mutate({
                    orderId: selectedOrder.id,
                    seated: selectedOrder.seated === true,
                    ...(selectedOrder.status === "IN_PROGRESS" && selectedOrder.reservationId
                      ? { reservationAction: "CANCEL" as const }
                      : {}),
                  })}>
                  {releaseSeats.isPending
                    ? "Đang xử lý…"
                    : selectedOrder.status === "COMPLETED"
                      ? "Trả chỗ"
                      : selectedOrder.reservationId
                        ? "Hủy đơn & hủy đặt bàn"
                        : selectedOrder.seated
                          ? "Hủy đơn và trả chỗ"
                          : "Hủy đơn trống"}
                </Button>
                {selectedOrder.status === "IN_PROGRESS" && selectedOrder.reservationId && (
                  <Button className="min-h-11" variant="secondary" disabled={changingQuantity}
                    onClick={() => releaseSeats.mutate({
                      orderId: selectedOrder.id,
                      seated: selectedOrder.seated === true,
                      reservationAction: "REVERT_CONFIRMED",
                    })}>
                    Hoàn tác Check-in (giữ đặt bàn)
                  </Button>
                )}
              </div>
            </div>
          )}
        </section>

        {showMenu && <div className="grid gap-6 md:grid-cols-2">
          <div className="min-w-0 space-y-2">
            <p className="text-xs uppercase tracking-widest text-muted-foreground">Thực đơn</p>
            {categories.length > 0 && (
              <div role="group" aria-label="Lọc món theo danh mục" className="flex flex-wrap gap-1.5 pb-1">
                <button
                  type="button"
                  aria-pressed={activeCategory === ALL_CATEGORIES}
                  onClick={() => setSelectedCategory(ALL_CATEGORIES)}
                  className={cn(
                    "rounded-full border px-3 py-1 text-xs font-medium transition",
                    activeCategory === ALL_CATEGORIES
                      ? "border-moss bg-moss text-moss-foreground"
                      : "bg-card hover:border-moss/40",
                  )}
                >
                  Tất cả ({dishes.length})
                </button>
                {categories.map((category) => (
                  <button
                    key={category.id}
                    type="button"
                    aria-pressed={activeCategory === category.id}
                    onClick={() => setSelectedCategory(category.id)}
                    className={cn(
                      "rounded-full border px-3 py-1 text-xs font-medium transition",
                      activeCategory === category.id
                        ? "border-moss bg-moss text-moss-foreground"
                        : "bg-card hover:border-moss/40",
                    )}
                  >
                    {category.categoryName} ({category.items?.length ?? 0})
                  </button>
                ))}
              </div>
            )}
            {menuQuery.isLoading && <p role="status" className="text-sm text-muted-foreground">Đang tải thực đơn…</p>}
            {menuQuery.isError && <p role="alert" className="text-sm text-alert">Không tải được thực đơn. Đóng và mở lại đơn để thử lại.</p>}
            {menuQuery.isSuccess && dishes.length === 0 && <p className="text-sm">Thực đơn chưa có món.</p>}
            {menuQuery.isSuccess && dishes.length > 0 && visibleDishes.length === 0 && (
              <p className="text-sm text-muted-foreground">Danh mục này chưa có món.</p>
            )}
            {visibleDishes.map((d) => {
              const stagedQty = draftQty(d.id);
              const sentQty = orderedQty.get(d.id) ?? 0;
              return (
                <div
                  key={d.id}
                  className={cn("flex flex-wrap items-center justify-between gap-2 rounded-lg border p-3", !d.isAvailable && "opacity-70")}
                >
                  <div>
                    <p className="text-sm font-medium">{d.itemName}</p>
                    <p className="text-xs tabular-nums text-muted-foreground">
                      <span>{d.isAvailable ? formatVND(d.currentPrice) : "Hết món"}</span>
                      {hasOpenOrder && sentQty > 0 && <span> · Đã gửi: {sentQty}</span>}
                    </p>
                  </div>
                  <div className="flex items-center gap-2">
                    <Button
                      size="icon"
                      variant="outline"
                      className="size-11"
                      aria-label={`Bớt ${d.itemName} khỏi giỏ`}
                      disabled={isSubmittingCart || stagedQty === 0}
                      onClick={() => decrementMenuDish(d.id)}
                    >
                      <Minus className="h-3 w-3" />
                    </Button>
                    <span className="w-5 text-center text-sm tabular-nums">{stagedQty}</span>
                    <Button
                      size="icon"
                      variant="outline"
                      className="size-11"
                      aria-label={`Thêm ${d.itemName}`}
                      disabled={!d.isAvailable || isSubmittingCart || stagedQty >= MAX_CART_QTY}
                      onClick={() => addToCart(d.id)}
                    >
                      <Plus className="h-3 w-3" />
                    </Button>
                  </div>
                </div>
              );
            })}
          </div>

          <div className="space-y-4">
            <div>
              <p className="mb-2 text-xs uppercase tracking-widest text-muted-foreground">Đã gửi bếp</p>
              {activeOrder?.items.length === 0 && (
                <p className="text-sm text-muted-foreground">Chưa gửi món nào cho đơn {orderCode(activeOrder.id)}.</p>
              )}
              {Array.from(itemGroups.values(), (group) => {
                const item = group.first;
                const cancellable = item.cookingStatus === "QUEUED";
                const editable = cancellable || item.cookingStatus === "PREPARING";
                const cancelled = item.cookingStatus === "CANCELLED";
                const itemLabel = item.note?.trim() ? `${item.itemName} (${item.note.trim()})` : item.itemName;
                const available = dishes.some((dish) => dish.id === item.menuItemId && dish.isAvailable);
                return (
                  <div key={item.id} className="border-b py-2 text-sm">
                    <div className="flex items-center justify-between gap-3">
                      <span className={cn(cancelled && "text-muted-foreground line-through")}>
                        {group.quantity} × {item.itemName}
                      </span>
                      <span className="shrink-0 tabular-nums">{formatVND(group.lineTotal)}</span>
                    </div>
                    <div className="mt-1 flex flex-wrap items-center gap-2">
                      <span className="text-xs text-muted-foreground">
                        {COOKING_STATUS_LABELS[item.cookingStatus]}
                      </span>
                      {cancelled && item.cancelReason && (
                        <span className="text-xs text-alert">Lý do: {item.cancelReason}</span>
                      )}
                      {!editable && item.note && (
                        <span className="text-xs text-muted-foreground">Ghi chú: {item.note}</span>
                      )}
                    </div>
                    {editable && (
                      <div className="mt-1.5 flex flex-wrap items-center gap-2">
                        <Button
                          size="icon"
                          variant="outline"
                          className="size-11"
                          aria-label={`Giảm số lượng ${itemLabel}`}
                          disabled={!cancellable || changingQuantity}
                          onClick={() => decrement(group)}
                        >
                          <Minus className="h-3 w-3" />
                        </Button>
                        <span className="w-5 text-center text-sm tabular-nums">{group.quantity}</span>
                        <Button
                          size="icon"
                          variant="outline"
                          className="size-11"
                          aria-label={`Tăng số lượng ${itemLabel}`}
                          disabled={!available || changingQuantity}
                          onClick={() => addToCart(item.menuItemId, (notes[item.id] ?? item.note)?.trim() || undefined)}
                        >
                          <Plus className="h-3 w-3" />
                        </Button>
                        <Input
                          className="h-11 min-w-28 flex-1 text-xs"
                          aria-label={`Ghi chú cho bếp: ${itemLabel}`}
                          placeholder="Ghi chú cho bếp"
                          maxLength={255}
                          readOnly={saveNote.isPending}
                          value={notes[item.id] ?? item.note ?? ""}
                          onChange={(e) => setNotes((cur) => ({ ...cur, [item.id]: e.target.value }))}
                          onBlur={() => saveItemNote(group)}
                          onKeyDown={(e) => { if (e.key === "Enter") e.currentTarget.blur(); }}
                        />
                        {cancellable && (
                          <Button
                            size="icon"
                            variant="ghost"
                            className="size-11 text-alert hover:bg-alert/10"
                            aria-label={`Hủy món ${itemLabel}`}
                            disabled={changingQuantity}
                            onClick={() => confirmCancellation(group)}
                          >
                            <Trash2 className="h-4 w-4" />
                          </Button>
                        )}
                      </div>
                    )}
                  </div>
                );
              })}
            </div>

            <div className="space-y-2">
              <p className="text-xs uppercase tracking-widest text-muted-foreground">Món mới</p>
              {cart.length === 0 ? (
                <p className="text-sm text-muted-foreground">
                  Chọn món ở thực đơn bên trái, sau đó nhấn Gửi Bếp để xác nhận.
                </p>
              ) : (
                <div className="space-y-2.5">
                  {cart.map((line) => {
                    const dish = dishMap.get(line.menuItemId);
                    const name = dish?.itemName ?? "Món";
                    const lineTotal = (dish?.currentPrice ?? 0) * line.quantity;
                    return (
                      <div key={line.key} className="space-y-1.5 rounded-lg border p-2.5 text-sm">
                        <div className="flex items-center justify-between gap-2">
                          <span className="font-medium">{line.quantity} × {name}</span>
                          <span className="shrink-0 tabular-nums text-xs text-muted-foreground">
                            {formatVND(lineTotal)}
                          </span>
                        </div>
                        {dish && !dish.isAvailable && (
                          <p className="text-xs text-alert">Món này hiện đã hết hàng.</p>
                        )}
                        <div className="flex items-center gap-1.5">
                          <Button
                            type="button"
                            size="icon"
                            variant="outline"
                            className="size-8 shrink-0"
                            aria-label={`Giảm món mới ${name}`}
                            disabled={isSubmittingCart}
                            onClick={() => changeCartLine(line.key, -1)}
                          >
                            <Minus className="h-3 w-3" />
                          </Button>
                          <Button
                            type="button"
                            size="icon"
                            variant="outline"
                            className="size-8 shrink-0"
                            aria-label={`Tăng món mới ${name}`}
                            disabled={!dish?.isAvailable || line.quantity >= MAX_CART_QTY || isSubmittingCart}
                            onClick={() => changeCartLine(line.key, 1)}
                          >
                            <Plus className="h-3 w-3" />
                          </Button>
                          <Input
                            className="h-9 min-w-0 flex-1 text-xs"
                            maxLength={255}
                            aria-label={`Ghi chú món mới: ${name}`}
                            placeholder="Ghi chú cho bếp"
                            value={line.note}
                            onChange={(e) => setCartLineNote(line.key, e.target.value)}
                          />
                          <Button
                            type="button"
                            size="icon"
                            variant="ghost"
                            className="size-8 shrink-0 text-alert hover:bg-alert/10"
                            aria-label={`Xóa ${name} khỏi món mới`}
                            disabled={isSubmittingCart}
                            onClick={() => removeCartLine(line.key)}
                          >
                            <Trash2 className="h-3.5 w-3.5" />
                          </Button>
                        </div>
                      </div>
                    );
                  })}
                  <div className="flex items-center justify-between text-sm font-medium">
                    <span>Tạm tính món mới</span>
                    <span className="tabular-nums">{formatVND(cartSubtotal)}</span>
                  </div>
                </div>
              )}
            </div>

            <Button
              type="button"
              className="min-h-11 w-full bg-moss text-moss-foreground hover:bg-moss/90"
              disabled={!canSubmitCart}
              onClick={() => setConfirmSendOpen(true)}
            >
              <ChefHat className="mr-2 h-4 w-4" />
              {totalCartQty > 0 ? `Gửi Bếp (${totalCartQty} phần)` : "Gửi Bếp"}
            </Button>

            {activeOrder && (
              <div className="space-y-1">
                <Button
                  className="min-h-11 w-full bg-primary hover:bg-primary-hover"
                  disabled={!hasPayableItems || changingQuantity || cart.length > 0}
                  onClick={() => onBill(activeOrder)}
                >
                  Thanh toán đơn {orderCode(activeOrder.id)}
                </Button>
                {cart.length > 0 && (
                  <p className="text-xs text-muted-foreground">
                    Gửi bếp hoặc xóa các món mới trước khi thanh toán.
                  </p>
                )}
              </div>
            )}
          </div>
        </div>}

        <AlertDialog
          open={confirmSendOpen && cart.length > 0 && showMenu}
          onOpenChange={(open) => {
            if (!open && !isSubmittingCart) setConfirmSendOpen(false);
          }}
        >
          <AlertDialogContent>
            <AlertDialogHeader>
              <AlertDialogTitle>Xác nhận gửi đơn xuống bếp</AlertDialogTitle>
              <AlertDialogDescription className="wrap-anywhere">
                {orderId
                  ? `Kiểm tra lại ${totalCartQty} phần món cho ${activeOrder?.tableNumber || table.tableNumber} (${orderCode(orderId)}) trước khi gửi xuống bếp.`
                  : `Kiểm tra lại ${totalCartQty} phần món cho ${table.tableNumber} trước khi tạo đơn và gửi xuống bếp.`}
              </AlertDialogDescription>
            </AlertDialogHeader>
            <div className="max-h-60 space-y-2 overflow-y-auto rounded-lg border bg-muted/40 p-3 text-sm">
              {cart.map((line) => {
                const price = dishMap.get(line.menuItemId)?.currentPrice ?? 0;
                return (
                  <div key={line.key} className="space-y-0.5">
                    <div className="flex items-center justify-between gap-3">
                      <span className="font-medium">
                        {line.quantity} × {dishName(line.menuItemId)}
                      </span>
                      <span className="shrink-0 tabular-nums">{formatVND(price * line.quantity)}</span>
                    </div>
                    {line.note.trim() && (
                      <p className="text-xs text-muted-foreground">Ghi chú: {line.note.trim()}</p>
                    )}
                  </div>
                );
              })}
              <div className="flex items-center justify-between border-t pt-2 font-semibold">
                <span>Tổng món mới ({totalCartQty} phần)</span>
                <span className="tabular-nums">{formatVND(cartSubtotal)}</span>
              </div>
            </div>
            <AlertDialogFooter>
              <AlertDialogCancel disabled={isSubmittingCart}>Quay lại</AlertDialogCancel>
              <AlertDialogAction
                className="bg-moss text-moss-foreground hover:bg-moss/90"
                disabled={isSubmittingCart || cart.length === 0}
                onClick={(e) => {
                  e.preventDefault();
                  submitCartToKitchen();
                }}
              >
                {isSubmittingCart ? "Đang gửi…" : "Xác nhận gửi bếp"}
              </AlertDialogAction>
            </AlertDialogFooter>
          </AlertDialogContent>
        </AlertDialog>

        <AlertDialog open={Boolean(cancelTarget)} onOpenChange={(open) => !open && setCancelTarget(null)}>
          <AlertDialogContent>
            <AlertDialogHeader>
              <AlertDialogTitle>Huỷ món</AlertDialogTitle>
              <AlertDialogDescription className="wrap-anywhere">
                {cancelTarget && `Huỷ ${cancelTarget.group.quantity} × ${cancelTarget.group.first.itemName}${cancelTarget.group.first.note ? ` (${cancelTarget.group.first.note})` : ""} khỏi đơn?`}
              </AlertDialogDescription>
            </AlertDialogHeader>
            <Input
              value={cancelReason}
              onChange={(e) => setCancelReason(e.target.value)}
              aria-label="Lý do huỷ món"
              maxLength={255}
              placeholder="Lý do huỷ món"
            />
            <AlertDialogFooter>
              <AlertDialogCancel>Giữ món</AlertDialogCancel>
              <AlertDialogAction
                className="bg-alert text-white hover:bg-alert/90"
                disabled={!cancelReason.trim() || changingQuantity}
                onClick={(e) => {
                  e.preventDefault();
                  if (!cancelTarget) return;
                  changeItems.mutate(
                    { orderId: cancelTarget.orderId, action: "ADJUST", payload: {
                      ...groupPayload(cancelTarget.group), action: "CANCEL", reason: cancelReason.trim(),
                    } },
                    { onSuccess: () => setCancelTarget(null) },
                  );
                }}
              >
                Huỷ món
              </AlertDialogAction>
            </AlertDialogFooter>
          </AlertDialogContent>
        </AlertDialog>
      </DialogContent>
    </Dialog>
  );
}

const methods = ["Tiền mặt", "VNPAY"];

export function BillingDialog({ table, order, onClose }: { table: TableResponse; order: OrderResponse; onClose: () => void }) {
  const queryClient = useQueryClient();

  const billOrderQuery = useQuery({
    queryKey: ["orders", "bill", order.id],
    queryFn: () => orderApi.getById(order.id),
  });

  const sentItems = billOrderQuery.data?.items ?? [];
  const displayTableNumber = billOrderQuery.data?.tableNumber || order.tableNumber || table.tableNumber;

  const [code, setCode] = useState("");
  const [redeem, setRedeem] = useState(false);
  const [method, setMethod] = useState<string>("Tiền mặt");
  const [phoneNumber, setPhoneNumber] = useState(() => order.reservationGuestPhone?.trim() ?? "");
  const [phoneEdited, setPhoneEdited] = useState(false);

  const resolvedReservationPhone = billOrderQuery.data?.reservationGuestPhone?.trim() || order.reservationGuestPhone?.trim() || "";
  useEffect(() => {
    if (!phoneEdited && !phoneNumber && resolvedReservationPhone) {
      setPhoneNumber(resolvedReservationPhone);
    }
  }, [phoneEdited, phoneNumber, resolvedReservationPhone]);
  const [loyaltyBalance, setLoyaltyBalance] = useState(0);
  const [pointValueVnd, setPointValueVnd] = useState(1);
  const [loyaltyBusy, setLoyaltyBusy] = useState(false);
  const [loyaltyChecked, setLoyaltyChecked] = useState(false);
  const [voucherBusy, setVoucherBusy] = useState(false);
  const [voucherValidated, setVoucherValidated] = useState(false);
  const [voucherDiscount, setVoucherDiscount] = useState(0);

  const bill = splitBillItems(sentItems);
  const subtotal = bill.subtotal;
  const vipFee = Number(billOrderQuery.data?.vipFeeAmount ?? order.vipFeeAmount ?? 0);
  const vipDepositPaid = Number(billOrderQuery.data?.vipDepositPaid ?? order.vipDepositPaid ?? 0);
  const taxableSubtotal = subtotal + vipFee;
  const vat = Math.round(taxableSubtotal * 10) / 100;
  const discount = voucherValidated ? Math.min(voucherDiscount, taxableSubtotal + vat) : 0;
  const beforeRedeem = taxableSubtotal + vat - discount;

  const maxPointsPossible = Math.floor(beforeRedeem / (pointValueVnd || 1));
  const redeemedPoints = redeem ? Math.floor(Math.min(loyaltyBalance, maxPointsPossible)) : 0;
  const redeemedAmount = Math.round(redeemedPoints * pointValueVnd * 100) / 100;
  const total = Math.max(0, beforeRedeem - redeemedAmount);

  const Row = ({ l, v, cls }: { l: string; v: string; cls?: string }) => (
    <div className={cn("flex justify-between text-sm", cls)}>
      <span>{l}</span>
      <span className="tabular-nums">{v}</span>
    </div>
  );

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-h-[90dvh] max-w-md overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="font-display text-3xl">Hóa đơn · {displayTableNumber} / {orderCode(order.id)}</DialogTitle>
          <DialogDescription>Chỉ tính tiền món của đơn {orderCode(order.id)}.</DialogDescription>
        </DialogHeader>
        <div className="space-y-1.5 border-b pb-3">
          {bill.lines.map((line) => (
            <Row key={line.key} l={line.label} v={formatVND(line.amount)} />
          ))}
          {bill.cancelled.length > 0 && (
            <div className="pt-1">
              <Row
                l={`Phần đã huỷ (${bill.cancelled.reduce((sum, item) => sum + item.quantity, 0)}), không tính tiền`}
                v={`-${formatVND(bill.cancelledAmount)}`}
                cls="text-muted-foreground line-through"
              />
            </div>
          )}
          {billOrderQuery.isLoading && <p role="status" className="text-sm">Đang tải hóa đơn…</p>}
          {billOrderQuery.isError && <p role="alert" className="text-sm text-alert">Không tải được hóa đơn. Đóng và mở lại để thử lại.</p>}
          {billOrderQuery.data && billOrderQuery.data.status !== "IN_PROGRESS" && (
            <p className="text-sm">Đơn đã đóng, không thể thanh toán thêm.</p>
          )}
          {billOrderQuery.isSuccess && bill.lines.length === 0 && (
            <p className="text-sm text-alert">Đơn không còn món để tính tiền.</p>
          )}
        </div>

        <div className="space-y-1.5">
          <Row l="Tạm tính" v={formatVND(subtotal)} />
          {vipFee > 0 && (
            <Row
              l="Phí thu phòng VIP"
              v={`+${formatVND(vipFee)}`}
              cls="text-amber-700 dark:text-amber-400 font-medium"
            />
          )}
          {vipDepositPaid > 0 && (
            <Row
              l="Phí thu & cọc phòng VIP"
              v={`Đã thanh toán ${formatVND(vipDepositPaid)}`}
              cls="text-emerald-700 dark:text-emerald-400 text-xs"
            />
          )}
          <Row l="VAT (10%)" v={formatVND(vat)} />

          <div className="space-y-2 pt-1">
            <Input
              aria-label="Số điện thoại khách"
              className="h-11"
              placeholder="Số điện thoại khách (để dùng điểm/voucher)"
              value={phoneNumber}
              onChange={(e) => {
                setPhoneEdited(true);
                setPhoneNumber(e.target.value);
                setLoyaltyBalance(0);
                setLoyaltyChecked(false);
                setRedeem(false);
                setVoucherValidated(false);
                setVoucherDiscount(0);
              }}
              inputMode="tel"
            />

            <Input
              aria-label="Mã voucher"
              className="h-11"
              placeholder="Mã voucher"
              value={code}
              onChange={(e) => {
                setCode(e.target.value);
                setVoucherValidated(false);
                setVoucherDiscount(0);
              }}
            />

            <div className="grid grid-cols-2 gap-2">
              <Button
                type="button"
                variant="outline"
                disabled={loyaltyBusy || !phoneNumber.trim()}
                onClick={async () => {
                  setLoyaltyBusy(true);
                  setLoyaltyChecked(false);
                  try {
                    const result = await paymentApi.getLoyaltyBalance(phoneNumber.trim());
                    setLoyaltyBalance(Math.max(0, Number(result.pointsBalance) || 0));
                    setPointValueVnd(Number(result.pointValueVnd) || 1);
                    setLoyaltyChecked(true);
                    toast.success(
                      `Số dư điểm: ${Number(result.pointsBalance).toLocaleString("vi-VN")} điểm (Quy đổi: 1 điểm = ${formatVND(Number(result.pointValueVnd) || 1)})`,
                    );
                  } catch (error) {
                    setLoyaltyBalance(0);
                    setRedeem(false);
                    toast.error(error instanceof Error ? error.message : "Không tra cứu được số dư điểm.");
                  } finally {
                    setLoyaltyBusy(false);
                  }
                }}
              >
                {loyaltyBusy ? "Đang tra cứu..." : "Tra cứu điểm"}
              </Button>

              <Button
                type="button"
                variant="outline"
                disabled={voucherBusy || !code.trim() || !phoneNumber.trim()}
                onClick={async () => {
                  setVoucherBusy(true);
                  try {
                    const result = await validateVoucher(code.trim(), phoneNumber.trim(), taxableSubtotal + vat);
                    if (!result.valid) {
                      setVoucherValidated(false);
                      setVoucherDiscount(0);
                      toast.error(result.message);
                    } else {
                      setVoucherValidated(true);
                      setVoucherDiscount(result.discountAmount);
                      toast.success(`${result.message} Giảm ${formatVND(result.discountAmount)}.`);
                    }
                  } catch (error) {
                    toast.error(error instanceof Error ? error.message : "Không kiểm tra được voucher.");
                  } finally {
                    setVoucherBusy(false);
                  }
                }}
              >
                {voucherBusy ? "Đang kiểm tra..." : "Kiểm tra voucher"}
              </Button>
            </div>
          </div>

          {discount > 0 && <Row l="Voucher" v={`-${formatVND(discount)}`} cls="text-moss" />}

          <div className="flex items-center justify-between rounded-lg bg-muted p-3 text-sm">
            <span>
              Dùng điểm ({loyaltyChecked ? `${loyaltyBalance.toLocaleString("vi-VN")} điểm = ${formatVND(loyaltyBalance * pointValueVnd)}` : "0 điểm"})
            </span>
            <Switch
              checked={redeem}
              disabled={!phoneNumber.trim() || !loyaltyChecked || loyaltyBalance <= 0}
              onCheckedChange={setRedeem}
            />
          </div>

          {redeemedPoints > 0 && (
            <Row
              l={`Điểm đã dùng (${redeemedPoints.toLocaleString("vi-VN")} điểm)`}
              v={`-${formatVND(redeemedAmount)}`}
              cls="text-moss"
            />
          )}

          <Row l="Tổng thanh toán" v={formatVND(total)} cls="pt-2 font-display text-2xl font-semibold" />
        </div>

        <div className="grid grid-cols-2 gap-2">
          {methods.map((m) => (
            <Button
              key={m}
              variant="outline"
              aria-pressed={method === m}
              onClick={() => setMethod(m)}
              className={cn("h-auto min-h-11 py-2 text-xs", method === m && "border-primary bg-primary/5 text-primary font-medium")}
            >
              {m}
            </Button>
          ))}
        </div>

        <Button
          className="min-h-11 w-full bg-primary hover:bg-primary-hover"
          disabled={
            voucherBusy || loyaltyBusy ||
            billOrderQuery.data?.status !== "IN_PROGRESS" || billOrderQuery.isError ||
            bill.lines.length === 0 ||
            (!!code.trim() && !voucherValidated)
          }
          onClick={async () => {
            if (method === "VNPAY") {
              if (redeem && (!phoneNumber.trim() || !loyaltyChecked)) {
                toast.error("Vui lòng tra cứu điểm trước khi dùng.");
                return;
              }
              setVoucherBusy(true);
              try {
                const payment = await paymentApi.createVnpay({
                  tableId: order.tableId,
                  diningOrderId: order.id,
                  subtotal: Math.round((taxableSubtotal + vat) * 100) / 100,
                  phoneNumber: phoneNumber.trim(),
                  ...(code.trim() ? { voucherCode: code.trim() } : {}),
                  ...(redeem && redeemedPoints > 0 ? { pointsToUse: redeemedPoints, usePoints: true } : { usePoints: false }),
                });
                window.location.assign(payment.paymentUrl);
              } catch (error) {
                toast.error(error instanceof Error ? error.message : "Không thể khởi tạo thanh toán VNPAY.");
                setVoucherBusy(false);
              }
              return;
            }

            setVoucherBusy(true);
            try {
              const settled = await orderApi.checkoutCash(order.id, {
                phoneNumber: phoneNumber.trim() || undefined,
                voucherCode: code.trim() || undefined,
                usePoints: redeem && redeemedPoints > 0,
                ...(redeem && redeemedPoints > 0 ? { pointsToUse: redeemedPoints } : {}),
                expectedTotal: Math.round(total * 100) / 100,
              });

              await queryClient.invalidateQueries({ queryKey: ["tables"] });
              await queryClient.invalidateQueries({ queryKey: ["orders"] });
              await queryClient.invalidateQueries({ queryKey: ["invoices"] });
              await queryClient.invalidateQueries({ queryKey: ["staff-pos-reservations"] });
              await queryClient.invalidateQueries({ queryKey: ["admin-reservations"] });
              await queryClient.invalidateQueries({ queryKey: ["reservations"] });
              toast.success(
                `Đã thu ${formatVND(Number(settled.total))} cho đơn ${orderCode(order.id)}. Điểm thưởng: +${Number(settled.pointsAwarded || 0).toLocaleString("vi-VN")}.`,
              );
              onClose();
            } catch (error) {
              toast.error(error instanceof Error ? error.message : "Không thể hoàn tất thanh toán.");
            } finally {
              setVoucherBusy(false);
            }
          }}
        >
          Hoàn Tất Thanh Toán ({method})
        </Button>
      </DialogContent>
    </Dialog>
  );
}