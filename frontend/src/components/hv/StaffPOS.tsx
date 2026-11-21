import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Minus, Plus, Users, Clock, Phone, ChefHat, Wrench, Trash2, UserCheck } from "lucide-react";
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
import type { ReservationResponse } from "@/features/reservation/types/reservation.types";
import { useTableWebSocket } from "@/lib/use-table-websocket";
import { menuApi } from "@/features/menu/api/menu-api";
import { useCreateOrder } from "@/features/order/hooks/use-create-order";
import { orderApi } from "@/features/order/api/order-api";
import { useKitchenCompleted } from "@/features/order/hooks/use-kitchen-tickets";
import {
  useAddOrderItem,
  useCancelOrderItem,
  useUpdateOrderItem,
} from "@/features/order/hooks/use-order-items";
import { COOKING_STATUS_LABELS, type OrderItemResponse, type OrderResponse } from "@/features/order/types/order.types";
import { orderCode } from "@/features/order/utils/order-code";
import { splitBillItems } from "@/features/order/utils/bill";

/** Món được chọn tạm thời trước khi tạo đơn mới cho bàn. */
type CartLine = { menuItemId: string; quantity: number; note: string };

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

/**
 * Màn hình phục vụ: chọn bàn để xem thực đơn, gọi món và chuyển sang thanh toán.
 */
export function StaffPOS() {
  const queryClient = useQueryClient();
  const [tableSelection, setSelectedTable] = useState<TableResponse | null>(null);
  const [bill, setBill] = useState<{ table: TableResponse; order: OrderResponse } | null>(null);

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

  useTableWebSocket({
    onTableStatusChange: () => {
      void queryClient.invalidateQueries({ queryKey: ["tables"] });
      void queryClient.invalidateQueries({ queryKey: ["staff-pos-reservations"] });
    },
    onFloorPlanChange: () => void queryClient.invalidateQueries({ queryKey: ["tables"] }),
  });

  // Map reservation theo tableId HOẶC các id trong mergedTableIds
  const findReservationForTable = (table: TableResponse): ReservationResponse | undefined => {
    const list = reservationsQuery.data ?? [];
    return list.find((r) => {
      if (!["CONFIRMED", "ARRIVED"].includes(r.status)) return false;
      if (r.tableId === table.id) return true;
      if (r.mergedTableIds && r.mergedTableIds.split(",").map((s) => s.trim()).includes(table.id)) return true;
      if (r.tableNumber && r.tableNumber.includes(table.tableNumber)) return true;
      return false;
    });
  };

  const zones: ZoneResponse[] = zonesQuery.data ?? [];
  const tables: TableResponse[] = tablesQuery.data ?? [];
  const selectedTable = tables.find((table) => table.id === tableSelection?.id) ?? tableSelection;
  const selectedReservation = selectedTable ? findReservationForTable(selectedTable) : undefined;
  const updateSelectedTable = (updated: TableResponse) => {
    queryClient.setQueryData<TableResponse[]>(["tables"], (current) =>
      current?.map((table) => (table.id === updated.id ? updated : table)),
    );
    setSelectedTable(updated);
  };

  const tablesByZone = useMemo(() => {
    const map = new Map<string, TableResponse[]>();
    for (const z of zones) {
      map.set(z.id, []);
    }
    const defaultList: TableResponse[] = [];
    for (const t of tables) {
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
  }, [zones, tables]);

  const counts = (status: TableStatus) => tables.filter((item) => item.status === status).length;

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
                const reservation = findReservationForTable(item);
                const isMaintenance = item.status === "MAINTENANCE";
                const statusLabel = TABLE_STATUS_LABELS[item.status] ?? item.status;
                const statusClass = REAL_STATUS_CLASSES[item.status] ?? "status-available";

                return (
                  <button
                    key={item.id}
                    onClick={() => setSelectedTable(item)}
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
                        <span className="block font-medium">{reservation.guestName} · {reservation.partySize} khách</span>
                        <span className="block">{reservation.reservationTime.slice(0, 5)} · {reservation.guestPhone}</span>
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
          reservation={selectedReservation}
          onClose={() => setSelectedTable(null)}
          onTableUpdated={updateSelectedTable}
        />
      )}

      {selectedTable && selectedTable.status !== "RESERVED" && (
        <OrderDialog
          key={selectedTable.id}
          table={selectedTable}
          exclusive={selectedTable.availableSeats == null && !!selectedReservation}
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
          setSelectedTable(bill.table);
          setBill(null);
        }} />
      )}
    </div>
  );
}

function ReservationDialog({
  table,
  reservation,
  onClose,
  onTableUpdated,
}: {
  table: TableResponse;
  reservation?: ReservationResponse | undefined;
  onClose: () => void;
  onTableUpdated?: (table: TableResponse) => void;
}) {
  const queryClient = useQueryClient();

  const changeStatusMutation = useMutation({
    mutationFn: (payload: { id: string; status: TableStatus }) =>
      tableApi.updateStatus(payload.id, payload.status),
    onSuccess: (updated) => {
      toast.success(`${updated.tableNumber}: ${TABLE_STATUS_LABELS[updated.status]}.`);
      void queryClient.invalidateQueries({ queryKey: ["tables"] });
      if (onTableUpdated) onTableUpdated(updated);
    },
    onError: (error: Error) => toast.error(error.message),
  });

  const checkInMutation = useMutation({
    mutationFn: async () => {
      let updatedTable = table;
      if (reservation) {
        await reservationApi.updateReservationStatus(reservation.id, { status: "ARRIVED" });
      }
      if (table.status !== "OCCUPIED") {
        updatedTable = await tableApi.updateStatus(table.id, "OCCUPIED");
      }
      return { updatedTable };
    },
    onSuccess: ({ updatedTable }) => {
      toast.success(`Check-in thành công! Bàn ${updatedTable.tableNumber} đã chuyển sang Đang phục vụ.`);
      void queryClient.invalidateQueries({ queryKey: ["tables"] });
      void queryClient.invalidateQueries({ queryKey: ["staff-pos-reservations"] });
      onTableUpdated?.(updatedTable);
    },
    onError: (error: Error) => toast.error(error.message),
  });

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle className="font-display text-3xl">{table.tableNumber} · Đã đặt trước</DialogTitle>
          <DialogDescription>Thông tin khách đặt bàn</DialogDescription>
        </DialogHeader>
        <div className="space-y-3 rounded-lg bg-muted p-4 text-sm">
          <p className="font-display text-xl font-semibold">{reservation?.guestName ?? "Khách đặt trước"}</p>
          <p className="flex items-center gap-2">
            <Clock className="h-4 w-4" /> {reservation?.reservationTime?.slice(0, 5) ?? "--:--"} · {reservation?.reservationDate ?? "Hôm nay"}
          </p>
          <p className="flex items-center gap-2">
            <Users className="h-4 w-4" /> {reservation?.partySize ?? table.capacity} khách
          </p>
          <p className="flex items-center gap-2">
            <Phone className="h-4 w-4" /> {reservation?.guestPhone ?? "Đang cập nhật SĐT"}
          </p>
          {reservation?.specialRequests && <p className="text-muted-foreground">Ghi chú: {reservation.specialRequests}</p>}
        </div>

        <Button
          className="w-full bg-emerald-600 hover:bg-emerald-700 text-white font-semibold py-2.5"
          disabled={checkInMutation.isPending}
          onClick={() => checkInMutation.mutate()}
        >
          <UserCheck className="mr-2 h-4 w-4" />
          {checkInMutation.isPending ? "Đang xác nhận…" : "Xác nhận khách đến (Check-in & Mở bàn)"}
        </Button>

        <div className="flex flex-wrap items-center justify-between gap-2 border-t pt-3">
          <span className="text-xs text-muted-foreground">Chuyển trạng thái bàn:</span>
          <Select
            value={table.status}
            disabled={changeStatusMutation.isPending}
            onValueChange={(val) =>
              changeStatusMutation.mutate({ id: table.id, status: val as TableStatus })
            }
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
      </DialogContent>
    </Dialog>
  );
}

function OrderDialog({
  table,
  exclusive,
  onClose,
  onBill,
  onTableUpdated,
}: {
  table: TableResponse;
  exclusive: boolean;
  onClose: () => void;
  onBill: (order: OrderResponse) => void;
  onTableUpdated?: (table: TableResponse) => void;
}) {
  const queryClient = useQueryClient();
  const [cart, setCart] = useState<CartLine[]>([]);

  const menuQuery = useQuery({
    queryKey: ["menu"],
    queryFn: () => menuApi.getOverview(),
    staleTime: 60000,
  });
  const dishes = menuQuery.data?.categories.flatMap((c) => c.items ?? []) ?? [];

  const seatedKey = ["orders", "table", table.id, "seated"];
  const seatedQuery = useQuery({
    queryKey: seatedKey,
    queryFn: () => orderApi.seatedAtTable(table.id),
    refetchInterval: 5000,
  });
  const orders = seatedQuery.data ?? [];
  const [selectedOrderId, setSelectedOrderId] = useState<string>();
  const selectedOrder = selectedOrderId ? orders.find((order) => order.id === selectedOrderId)
    : orders.length === 1 ? orders[0] : undefined;
  const activeOrder = selectedOrder?.status === "IN_PROGRESS" ? selectedOrder : undefined;
  const [partySize, setPartySize] = useState("1");
  const occupiedSeats = orders.reduce((used, order) => used + (order.partySize ?? table.capacity), 0);
  const freeSeats = Math.max(0, table.capacity - occupiedSeats);
  const openOrder = useMutation({
    mutationFn: () => orderApi.openSeatedOrder(table.id, Number(partySize)),
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
    mutationFn: orderApi.releaseSeats,
    onSuccess: (_, orderId) => {
      queryClient.setQueryData<OrderResponse[]>(seatedKey, (current) => current?.filter((order) => order.id !== orderId));
      void queryClient.invalidateQueries({ queryKey: ["orders"] });
      void queryClient.invalidateQueries({ queryKey: ["tables"] });
      toast.success(`Đã trả chỗ cho đơn ${orderCode(orderId)}.`);
    },
    onError: (error: Error) => toast.error(error.message),
  });
  const createOrder = useCreateOrder();
  const addItem = useAddOrderItem();
  const updateItem = useUpdateOrderItem();
  const cancelItem = useCancelOrderItem();

  const [notes, setNotes] = useState<Record<string, string>>({});
  const [cancelTarget, setCancelTarget] = useState<OrderItemResponse | null>(null);
  const [cancelReason, setCancelReason] = useState("Khách đổi món");

  const updateTableStatus = useMutation({
    mutationFn: (payload: { id: string; status: TableStatus }) =>
      tableApi.updateStatus(payload.id, payload.status),
    onSuccess: (updated) => {
      toast.success(`${updated.tableNumber}: ${TABLE_STATUS_LABELS[updated.status]}.`);
      void queryClient.invalidateQueries({ queryKey: ["tables"] });
      if (onTableUpdated) onTableUpdated(updated);
    },
    onError: (error: Error) => toast.error(error.message),
  });

  useKitchenCompleted((message) => {
    if (message.tableId !== table.id && !orders.some((order) => order.id === message.orderId)) return;
    if (message.status === "READY") {
      toast.success(`${message.tableNumber} / ${orderCode(message.orderId)}: ${message.itemName} đã sẵn sàng.`);
    }
    void queryClient.invalidateQueries({ queryKey: ["orders"] });
  });

  /** Điều chỉnh số lượng món trong giỏ nháp; bỏ món khi số lượng về 0. */
  const change = (menuItemId: string, delta: number) =>
    setCart((cur) => {
      const existing = cur.find((l) => l.menuItemId === menuItemId);
      if (!existing) return delta > 0 ? [...cur, { menuItemId, quantity: 1, note: "" }] : cur;
      return cur
        .map((l) => (l.menuItemId === menuItemId ? { ...l, quantity: l.quantity + delta } : l))
        .filter((l) => l.quantity > 0);
    });

  const setNote = (menuItemId: string, note: string) =>
    setCart((cur) => cur.map((l) => (l.menuItemId === menuItemId ? { ...l, note } : l)));

  const qty = (menuItemId: string) => cart.find((l) => l.menuItemId === menuItemId)?.quantity ?? 0;
  const dishName = (menuItemId: string) => dishes.find((d) => d.id === menuItemId)?.itemName ?? "";
  const isOccupied = table.status === "OCCUPIED";
  const orderId = activeOrder?.id;
  const hasOpenOrder = Boolean(orderId);
  const legacyCart = exclusive && seatedQuery.isSuccess && orders.length === 0;
  const showMenu = isOccupied && seatedQuery.isSuccess && (hasOpenOrder || legacyCart);
  const canSubmit = showMenu && legacyCart && cart.length > 0 && !createOrder.isPending;
  const hasPayableItems = activeOrder?.items.some((item) => item.cookingStatus !== "CANCELLED");
  const parsedPartySize = Number(partySize);
  const canOpenOrder = isOccupied && !exclusive && seatedQuery.isSuccess && freeSeats > 0 && !openOrder.isPending;

  const orderedQty = (menuItemId: string) =>
    activeOrder?.items
      .filter((i) => i.menuItemId === menuItemId && i.cookingStatus !== "CANCELLED")
      .reduce((sum, i) => sum + i.quantity, 0) ?? 0;

  /** Chọn món: thêm vào đơn đang mở hoặc tăng số lượng trong giỏ nháp. */
  const selectDish = (menuItemId: string) => {
    if (orderId) {
      addItem.mutate({ orderId, payload: { menuItemId, quantity: 1 } });
      return;
    }
    change(menuItemId, 1);
  };

  const changeItemQty = (item: OrderItemResponse, delta: number) => {
    if (!orderId) return;
    const next = item.quantity + delta;
    if (next < 1) return;
    updateItem.mutate({
      orderId,
      itemId: item.id,
      payload: { quantity: next, note: item.note ?? undefined },
    });
  };

  const saveItemNote = (item: OrderItemResponse) => {
    if (!orderId) return;
    const next = (notes[item.id] ?? item.note ?? "").trim();
    if (next === (item.note ?? "").trim()) return;
    updateItem.mutate({
      orderId,
      itemId: item.id,
      payload: { quantity: item.quantity, note: next },
    });
  };

  const isLockedItem = (item: OrderItemResponse) =>
    item.cookingStatus === "READY" ||
    item.cookingStatus === "SERVED" ||
    item.cookingStatus === "CANCELLED";

  const isCancellable = (item: OrderItemResponse) => item.cookingStatus === "QUEUED";

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
                  onClick={() => updateTableStatus.mutate({ id: table.id, status: "OCCUPIED" })}
                >
                  Mở bàn
                </Button>
              )}
              {isOccupied && seatedQuery.isSuccess && orders.length === 0 && (
                <Button
                  className="min-h-11"
                  variant="outline"
                  disabled={updateTableStatus.isPending}
                  onClick={() => updateTableStatus.mutate({ id: table.id, status: "AVAILABLE" })}
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
              disabled={updateTableStatus.isPending}
              onValueChange={(value) =>
                updateTableStatus.mutate({ id: table.id, status: value as TableStatus })
              }
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
                  {orderCode(order.id)}{order.partySize != null ? ` · ${order.partySize} khách` : ""}
                  {order.status === "COMPLETED" ? " · Đã trả tiền, còn ngồi" : " · Đang mở"}
                </Button>
              ))}
            </div>
          )}
          {isOccupied && !exclusive && seatedQuery.isSuccess && freeSeats > 0 && (
            <form className="flex flex-wrap items-end gap-2" onSubmit={(event) => {
              event.preventDefault();
              if (canOpenOrder && Number.isInteger(parsedPartySize) && parsedPartySize >= 1 && parsedPartySize <= freeSeats) {
                openOrder.mutate();
              }
            }}>
              <label className="space-y-1 text-sm">
                <span className="block">Số khách của đơn mới</span>
                <Input className="h-11 w-32" type="number" min={1} max={freeSeats} required value={partySize}
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
          {seatedQuery.isSuccess && selectedOrder?.seated && !hasPayableItems && (
            <div className="space-y-2 border-t pt-3">
              {selectedOrder.status === "COMPLETED" && (
                <p className="text-sm">Chỉ trả chỗ khi khách của đơn {orderCode(selectedOrder.id)} đã rời bàn.</p>
              )}
              <Button className="min-h-11" variant="outline" disabled={releaseSeats.isPending || addItem.isPending}
                onClick={() => releaseSeats.mutate(selectedOrder.id)}>
                {releaseSeats.isPending ? "Đang trả chỗ…" : selectedOrder.status === "COMPLETED" ? "Trả chỗ" : "Hủy đơn và trả chỗ"}
              </Button>
            </div>
          )}
        </section>

        {showMenu && <div className="grid gap-6 md:grid-cols-2">
          <div className="min-w-0 space-y-2">
            <p className="text-xs uppercase tracking-widest text-muted-foreground">Thực đơn</p>
            {menuQuery.isLoading && <p role="status" className="text-sm text-muted-foreground">Đang tải thực đơn…</p>}
            {menuQuery.isError && <p role="alert" className="text-sm text-alert">Không tải được thực đơn. Đóng và mở lại đơn để thử lại.</p>}
            {menuQuery.isSuccess && dishes.length === 0 && <p className="text-sm">Thực đơn chưa có món.</p>}
            {dishes.map((d) => {
              const shownQty = hasOpenOrder ? orderedQty(d.id) : qty(d.id);
              return (
                <div
                  key={d.id}
                  className={cn("flex flex-wrap items-center justify-between gap-2 rounded-lg border p-3", !d.isAvailable && "opacity-70")}
                >
                  <div>
                    <p className="text-sm font-medium">{d.itemName}</p>
                    <p className="text-xs tabular-nums text-muted-foreground">
                      {d.isAvailable ? formatVND(d.currentPrice) : "Hết món"}
                    </p>
                  </div>
                  <div className="flex items-center gap-2">
                    <Button
                      size="icon"
                      variant="outline"
                      className="size-11"
                      aria-label={`Bớt ${d.itemName} khỏi giỏ`}
                      disabled={!d.isAvailable || hasOpenOrder || qty(d.id) === 0}
                      onClick={() => change(d.id, -1)}
                    >
                      <Minus className="h-3 w-3" />
                    </Button>
                    <span className="w-5 text-center text-sm tabular-nums">{shownQty}</span>
                    <Button
                      size="icon"
                      variant="outline"
                      className="size-11"
                      aria-label={`Thêm ${d.itemName}`}
                      disabled={!d.isAvailable || addItem.isPending || releaseSeats.isPending || createOrder.isPending}
                      onClick={() => selectDish(d.id)}
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
              {activeOrder?.items.map((item) => {
                const locked = isLockedItem(item);
                const cancelled = item.cookingStatus === "CANCELLED";
                return (
                  <div key={item.id} className="border-b py-2 text-sm">
                    <div className="flex items-center justify-between gap-3">
                      <span className={cn(cancelled && "text-muted-foreground line-through")}>
                        {item.quantity} × {item.itemName}
                      </span>
                      <span className="shrink-0 tabular-nums">{formatVND(item.lineTotal)}</span>
                    </div>
                    <div className="mt-1 flex flex-wrap items-center gap-2">
                      <span className="text-xs text-muted-foreground">
                        {COOKING_STATUS_LABELS[item.cookingStatus]}
                      </span>
                      {cancelled && item.cancelReason && (
                        <span className="text-xs text-alert">Lý do: {item.cancelReason}</span>
                      )}
                    </div>
                    {!locked && (
                      <div className="mt-1.5 flex flex-wrap items-center gap-2">
                        <Button
                          size="icon"
                          variant="outline"
                          className="size-11"
                          aria-label={`Giảm số lượng ${item.itemName}`}
                          disabled={item.quantity <= 1 || updateItem.isPending}
                          onClick={() => changeItemQty(item, -1)}
                        >
                          <Minus className="h-3 w-3" />
                        </Button>
                        <span className="w-5 text-center text-sm tabular-nums">{item.quantity}</span>
                        <Button
                          size="icon"
                          variant="outline"
                          className="size-11"
                          aria-label={`Tăng số lượng ${item.itemName}`}
                          disabled={updateItem.isPending}
                          onClick={() => changeItemQty(item, 1)}
                        >
                          <Plus className="h-3 w-3" />
                        </Button>
                        <Input
                          className="h-11 min-w-28 flex-1 text-xs"
                          aria-label={`Ghi chú cho bếp: ${item.itemName}`}
                          placeholder="Ghi chú cho bếp"
                          value={notes[item.id] ?? item.note ?? ""}
                          onChange={(e) => setNotes((cur) => ({ ...cur, [item.id]: e.target.value }))}
                          onBlur={() => saveItemNote(item)}
                        />
                        {isCancellable(item) && (
                          <Button
                            size="icon"
                            variant="ghost"
                            className="size-11 text-alert hover:bg-alert/10"
                            aria-label={`Hủy món ${item.itemName}`}
                            disabled={cancelItem.isPending}
                            onClick={() => {
                              setCancelReason("Khách đổi món");
                              setCancelTarget(item);
                            }}
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

            <div>
              <p className="mb-2 text-xs uppercase tracking-widest text-muted-foreground">Món mới</p>
              {orderId ? (
                <p className="text-sm text-muted-foreground">
                  Chọn món trong thực đơn để gửi thêm cho đơn {orderCode(orderId)}.
                </p>
              ) : cart.length === 0 ? (
                <p className="text-sm text-muted-foreground">Chọn món ở bên trái.</p>
              ) : (
                <div className="space-y-2">
                  {cart.map((line) => (
                    <div key={line.menuItemId} className="space-y-1">
                      <p className="text-sm">{line.quantity} × {dishName(line.menuItemId)}</p>
                      <Input
                        className="h-8 text-xs"
                        placeholder="Ghi chú cho bếp"
                        value={line.note}
                        onChange={(e) => setNote(line.menuItemId, e.target.value)}
                      />
                    </div>
                  ))}
                </div>
              )}
            </div>

            {!hasOpenOrder && (
              <Button
                className="w-full bg-moss text-moss-foreground hover:bg-moss/90"
                disabled={!canSubmit}
                onClick={() => {
                  createOrder.mutate(
                    {
                      tableId: table.id,
                      items: cart.map((line) => ({
                        menuItemId: line.menuItemId,
                        quantity: line.quantity,
                        note: line.note.trim() || undefined,
                      })),
                      submit: true,
                    },
                    {
                      onSuccess: (order) => {
                        setSelectedOrderId(order.id);
                        toast.success(`Đã gửi ${cart.length} món xuống bếp`);
                        setCart([]);
                      },
                      onError: (error: Error) => toast.error(error.message),
                    },
                  );
                }}
              >
                <ChefHat className="mr-2 h-4 w-4" /> Gửi Bếp
              </Button>
            )}

            {activeOrder && (
              <Button
                className="min-h-11 w-full bg-primary hover:bg-primary-hover"
                disabled={!hasPayableItems || addItem.isPending || updateItem.isPending || cancelItem.isPending}
                onClick={() => onBill(activeOrder)}
              >
                Thanh toán đơn {orderCode(activeOrder.id)}
              </Button>
            )}
          </div>
        </div>}

        <AlertDialog open={Boolean(cancelTarget)} onOpenChange={(open) => !open && setCancelTarget(null)}>
          <AlertDialogContent>
            <AlertDialogHeader>
              <AlertDialogTitle>Huỷ món</AlertDialogTitle>
              <AlertDialogDescription>Huỷ món {cancelTarget?.itemName} khỏi đơn?</AlertDialogDescription>
            </AlertDialogHeader>
            <Input
              value={cancelReason}
              onChange={(e) => setCancelReason(e.target.value)}
              placeholder="Lý do huỷ món"
            />
            <AlertDialogFooter>
              <AlertDialogCancel>Giữ món</AlertDialogCancel>
              <AlertDialogAction
                className="bg-alert text-white hover:bg-alert/90"
                disabled={!cancelReason.trim() || cancelItem.isPending}
                onClick={(e) => {
                  e.preventDefault();
                  if (!orderId || !cancelTarget) return;
                  cancelItem.mutate(
                    { orderId, itemId: cancelTarget.id, reason: cancelReason.trim() },
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
  const [phoneNumber, setPhoneNumber] = useState("");
  const [loyaltyBalance, setLoyaltyBalance] = useState(0);
  const [pointValueVnd, setPointValueVnd] = useState(1);
  const [loyaltyBusy, setLoyaltyBusy] = useState(false);
  const [loyaltyChecked, setLoyaltyChecked] = useState(false);
  const [voucherBusy, setVoucherBusy] = useState(false);
  const [voucherValidated, setVoucherValidated] = useState(false);
  const [voucherDiscount, setVoucherDiscount] = useState(0);

  const bill = splitBillItems(sentItems);
  const subtotal = bill.subtotal;
  const vat = Math.round(subtotal * 10) / 100;
  const discount = voucherValidated ? Math.min(voucherDiscount, subtotal + vat) : 0;
  const beforeRedeem = subtotal + vat - discount;

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
                l={`Món đã huỷ (${bill.cancelled.length}), không tính tiền`}
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
          <Row l="VAT (10%)" v={formatVND(vat)} />

          <div className="space-y-2 pt-1">
            <Input
              aria-label="Số điện thoại khách"
              className="h-11"
              placeholder="Số điện thoại khách (để dùng điểm/voucher)"
              value={phoneNumber}
              onChange={(e) => {
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
                    const result = await validateVoucher(code.trim(), phoneNumber.trim(), subtotal + vat);
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
                  subtotal: Math.round((subtotal + vat) * 100) / 100,
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