import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Minus, Plus, Users, Clock, Phone, ChefHat, Receipt, Wrench, Trash2 } from "lucide-react";
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
import {
  formatVND,
  useRestaurant,
  type Table,
  type TableStatus as MockTableStatus,
  type Zone,
} from "@/lib/restaurant-store";
import { cn } from "@/lib/utils";
import { validateVoucher } from "@/lib/voucher-api";
import { paymentApi } from "@/lib/payment-api";
import { tableApi, type TableStatus, type TableResponse } from "@/lib/table-api";
import { reservationApi } from "@/features/reservation/api/reservation-api";
import type { ReservationResponse } from "@/features/reservation/types/reservation.types";
import { useTableWebSocket } from "@/lib/use-table-websocket";
import { menuApi } from "@/features/menu/api/menu-api";
import { useActiveOrder } from "@/features/order/hooks/use-orders";
import { useCreateOrder } from "@/features/order/hooks/use-create-order";
import { BackButton } from "@/components/hv/BackButton";
import { orderApi } from "@/features/order/api/order-api";
import { useKitchenCompleted } from "@/features/order/hooks/use-kitchen-tickets";
import {
  useAddOrderItem,
  useCancelOrderItem,
  useUpdateOrderItem,
} from "@/features/order/hooks/use-order-items";
import { COOKING_STATUS_LABELS, type OrderItemResponse } from "@/features/order/types/order.types";
import { splitBillItems } from "@/features/order/utils/bill";

/** Một dòng món trong giỏ gọi món (chưa gửi xuống bếp). */
type CartLine = { menuItemId: string; quantity: number; note: string };

/**
 * Nhãn tiếng Việt của các trạng thái bàn (theo backend).
 * - AVAILABLE: bàn trống, sẵn sàng đón khách
 * - RESERVED:  khách đã đặt trước, chờ tới giờ
 * - OCCUPIED:  khách đang ngồi — chỉ trạng thái này mới gọi món được
 * - CLEANING:  bàn cần dọn dẹp
 * - MAINTENANCE: bàn đang bảo trì, không phục vụ
 */
const TABLE_STATUS_LABELS: Record<TableStatus, string> = {
  AVAILABLE: "Trống",
  RESERVED: "Đã đặt trước",
  OCCUPIED: "Đang phục vụ",
  CLEANING: "Cần dọn dẹp",
  MAINTENANCE: "Bảo trì",
};

/** Màu badge cho từng trạng thái bàn thật (dùng lại class của design system). */
const REAL_STATUS_CLASSES: Record<TableStatus, string> = {
  AVAILABLE: "status-available",
  RESERVED: "status-reserved",
  OCCUPIED: "status-occupied",
  CLEANING: "status-available",
  MAINTENANCE: "status-maintenance",
};

export const tableStatusMeta: Record<MockTableStatus, { label: string; cls: string }> = {
  available: { label: "Trống", cls: "status-available" },
  reserved: { label: "Đã Đặt", cls: "status-reserved" },
  occupied: { label: "Đang Ăn", cls: "status-occupied" },
  maintenance: { label: "Bảo Trì", cls: "status-maintenance" },
};

const zones: Zone[] = ["Trong Nhà", "Sân Vườn", "Phòng VIP"];

/**
 * POS giữ nguyên bố cục và các dialog hiện có.
 * Nguồn dữ liệu chính của danh sách bàn, trạng thái, sức chứa và đặt bàn là API backend.
 * restaurant-store chỉ còn được dùng để tương thích với giỏ món cũ khi có bàn khớp tên.
 */
function toMockStatus(status: TableStatus): MockTableStatus {
  switch (status) {
    case "RESERVED": return "reserved";
    case "OCCUPIED": return "occupied";
    case "MAINTENANCE":
    case "CLEANING": return "maintenance";
    default: return "available";
  }
}

function getZoneFromDatabase(table: TableResponse): Zone {
  if (table.zoneIsVip) return "Phòng VIP";
  const name = (table.zoneName ?? "").toLocaleLowerCase("vi-VN");
  if (name.includes("sân") || name.includes("vườn") || name.includes("ngoài")) return "Sân Vườn";
  return "Trong Nhà";
}

function localDateString(date = new Date()): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

export function StaffPOS() {
  const { tables: mockTables, dish } = useRestaurant();
  const queryClient = useQueryClient();
  const [active, setActive] = useState<number | null>(null);
  const [billing, setBilling] = useState<number | null>(null);

  // Danh sách bàn và trạng thái luôn lấy từ database thông qua API hiện có.
  const realTablesQuery = useQuery({
    queryKey: ["tables"],
    queryFn: () => tableApi.list(),
    staleTime: 0,
    refetchInterval: 5000,
    refetchOnWindowFocus: true,
  });

  // Thông tin khách/giờ đặt lấy từ reservation API, không lấy từ dữ liệu mẫu.
  const today = localDateString();
  const reservationsQuery = useQuery({
    queryKey: ["staff-pos-reservations", today],
    queryFn: () => reservationApi.listByDate(today),
    staleTime: 0,
    refetchInterval: 10000,
    refetchOnWindowFocus: true,
  });

  // WebSocket giúp cập nhật gần thời gian thực; polling ở trên là đường dự phòng.
  useTableWebSocket({
    onTableStatusChange: () => {
      void queryClient.invalidateQueries({ queryKey: ["tables"] });
      void queryClient.invalidateQueries({ queryKey: ["staff-pos-reservations"] });
    },
    onFloorPlanChange: () => void queryClient.invalidateQueries({ queryKey: ["tables"] }),
  });

  const reservationsByTable = useMemo(() => {
    const result = new Map<string, ReservationResponse>();
    for (const reservation of reservationsQuery.data ?? []) {
      if (!reservation.tableNumber || !["CONFIRMED", "ARRIVED"].includes(reservation.status)) continue;
      const key = reservation.tableNumber.trim().toLocaleLowerCase("vi-VN");
      const current = result.get(key);
      const candidateTime = `${reservation.reservationDate}T${reservation.reservationTime}`;
      const currentTime = current ? `${current.reservationDate}T${current.reservationTime}` : "";
      if (!current || candidateTime < currentTime) result.set(key, reservation);
    }
    return result;
  }, [reservationsQuery.data]);

  // Giữ ID cũ nếu có bàn khớp tên để không phá các dialog hiện hữu.
  // Bàn chỉ tồn tại trong database được cấp ID giao diện riêng; API vẫn dùng realTable.id.
  const displayTables: Table[] = useMemo(() => {
    const realTables = realTablesQuery.data ?? [];
    return realTables.map((realTable, index) => {
      const normalizedNumber = realTable.tableNumber.trim().toLocaleLowerCase("vi-VN");
      const mock = mockTables.find(
        (item) => item.name.trim().toLocaleLowerCase("vi-VN") === normalizedNumber,
      );
      const reservation = reservationsByTable.get(normalizedNumber);
      return {
        id: mock?.id ?? 100000 + index,
        name: realTable.tableNumber,
        zone: getZoneFromDatabase(realTable),
        seats: realTable.capacity,
        status: toMockStatus(realTable.status),
        guest: reservation?.guestName,
        time: reservation?.reservationTime?.slice(0, 5),
        phone: reservation?.guestPhone,
        partySize: reservation?.partySize,
        // Giữ giỏ cũ nếu bàn đã có trong store; không dùng store làm nguồn trạng thái.
        order: mock?.order ?? [],
      };
    });
  }, [realTablesQuery.data, mockTables, reservationsByTable]);

  const table = displayTables.find((item) => item.id === active);
  const realTables = realTablesQuery.data ?? [];
  const realStatusByNumber = useMemo(() => {
    const result = new Map<string, TableStatus>();
    realTables.forEach((item) => result.set(item.tableNumber.trim().toLocaleLowerCase("vi-VN"), item.status));
    return result;
  }, [realTables]);
  const counts = (status: MockTableStatus) => displayTables.filter((item) => item.status === status).length;

  return (
    <div className="space-y-8">
      <div className="space-y-4">
        <div>
          <BackButton />
        </div>
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="text-xs uppercase tracking-[0.2em] text-muted-foreground">Sơ đồ bàn ăn</p>
            <h1 className="text-4xl font-semibold">Phục vụ & Thu ngân</h1>
          </div>
          <div className="flex flex-wrap gap-2">
            {(Object.keys(tableStatusMeta) as MockTableStatus[]).map((status) => (
              <span
                key={status}
                className={cn("rounded-full px-3 py-1 text-xs font-medium", tableStatusMeta[status].cls)}
              >
                {tableStatusMeta[status].label} · {counts(status)}
              </span>
            ))}
          </div>
        </div>
      </div>

      {realTablesQuery.isError && (
        <p role="alert" className="rounded-md border border-alert/30 bg-alert/5 px-3 py-2 text-sm text-alert">
          {realTablesQuery.error instanceof Error
            ? realTablesQuery.error.message
            : "Không tải được danh sách bàn từ cơ sở dữ liệu."}
        </p>
      )}
      {realTablesQuery.isLoading && (
        <p className="text-sm text-muted-foreground">Đang tải danh sách bàn từ cơ sở dữ liệu…</p>
      )}
      {reservationsQuery.isError && (
        <p role="alert" className="rounded-md border border-alert/30 bg-alert/5 px-3 py-2 text-sm text-alert">
          Không tải được thông tin đặt bàn từ cơ sở dữ liệu.
        </p>
      )}

      {zones.map((zone) => (
        <section key={zone}>
          <h2 className="mb-3 text-2xl font-medium text-moss">{zone}</h2>
          <div className="grid grid-cols-2 gap-5 sm:grid-cols-3 lg:grid-cols-5">
            {displayTables
              .filter((item) => item.zone === zone)
              .map((item) => {
                const total = item.order.reduce((sum, order) => sum + (dish(order.dishId)?.price ?? 0) * order.qty, 0);
                const normalizedNumber = item.name.trim().toLocaleLowerCase("vi-VN");
                const realStatus = realStatusByNumber.get(normalizedNumber);
                const reservation = reservationsByTable.get(normalizedNumber);
                const isMaintenance = realStatus === "MAINTENANCE" || realStatus === "CLEANING";
                const statusLabel = realStatus ? TABLE_STATUS_LABELS[realStatus] : tableStatusMeta[item.status].label;
                const statusClass = realStatus
                  ? REAL_STATUS_CLASSES[realStatus]
                  : tableStatusMeta[item.status].cls;
                return (
                  <button
                    key={item.id}
                    disabled={isMaintenance}
                    onClick={() => setActive(item.id)}
                    className="surface-card group p-5 text-left transition hover:-translate-y-0.5 hover:border-primary/40 disabled:cursor-not-allowed disabled:opacity-60"
                  >
                    <div className="flex items-start justify-between">
                      <span className="font-display text-2xl font-semibold">{item.name}</span>
                      {isMaintenance && <Wrench className="h-4 w-4 text-muted-foreground" />}
                    </div>
                    <p className="mt-1 flex items-center gap-1 text-xs text-muted-foreground">
                      <Users className="h-3 w-3" /> {item.seats} ghế
                    </p>
                    <span className={cn("mt-4 inline-block rounded-full px-2.5 py-0.5 text-xs font-medium", statusClass)}>
                      {statusLabel}
                    </span>
                    {realStatus === "RESERVED" && reservation && (
                      <p className="mt-2 text-xs text-muted-foreground">
                        <span className="block">{reservation.guestName} · {reservation.partySize} khách</span>
                        <span className="block">{reservation.reservationTime.slice(0, 5)} · {reservation.guestPhone}</span>
                      </p>
                    )}
                    {realStatus === "RESERVED" && !reservation && (
                      <p className="mt-2 text-xs text-muted-foreground">Đã đặt trước</p>
                    )}
                    {item.status === "occupied" && (
                      <p className="mt-2 text-xs font-medium tabular-nums">{formatVND(total)}</p>
                    )}
                  </button>
                );
              })}
          </div>
        </section>
      ))}

      {table?.status === "reserved" && (
        <ReservationDialog
          table={table}
          reservation={reservationsByTable.get(table.name.trim().toLocaleLowerCase("vi-VN"))}
          onClose={() => setActive(null)}
        />
      )}
      {table && table.status !== "reserved" && (
        <OrderDialog
          table={table}
          onClose={() => setActive(null)}
          onBill={() => {
            setBilling(table.id);
            setActive(null);
          }}
        />
      )}
      {billing !== null && <BillingDialog tableId={billing} onClose={() => setBilling(null)} />}
    </div>
  );
}

function ReservationDialog({
  table,
  reservation,
  onClose,
}: {
  table: Table;
  reservation?: ReservationResponse;
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const checkInMutation = useMutation({
    mutationFn: async () => {
      if (!reservation) throw new Error("Không tìm thấy thông tin đặt bàn trong cơ sở dữ liệu.");
      const updated = await reservationApi.updateReservationStatus(reservation.id, { status: "ARRIVED" });
      const databaseTables = await tableApi.list();
      const realTable = databaseTables.find(
        (item) => item.tableNumber.trim().toLocaleLowerCase("vi-VN") ===
          (reservation.tableNumber ?? table.name).trim().toLocaleLowerCase("vi-VN"),
      );
      if (!realTable) throw new Error("Không tìm thấy bàn trong cơ sở dữ liệu để cập nhật check-in.");
      if (realTable.status !== "OCCUPIED") await tableApi.updateStatus(realTable.id, "OCCUPIED");
      return updated;
    },
    onSuccess: (updated) => {
      toast.success(`Đã check-in ${updated.guestName} vào ${updated.tableNumber ?? table.name}`);
      void queryClient.invalidateQueries({ queryKey: ["tables"] });
      void queryClient.invalidateQueries({ queryKey: ["staff-pos-reservations"] });
      onClose();
    },
    onError: (error: Error) => toast.error(error.message),
  });

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle className="font-display text-3xl">{table.name} · Đặt trước</DialogTitle>
          <DialogDescription>Thông tin đặt bàn của khách</DialogDescription>
        </DialogHeader>
        <div className="space-y-3 rounded-lg bg-muted p-4 text-sm">
          <p className="font-display text-xl font-semibold">{reservation?.guestName ?? "Chưa có thông tin khách"}</p>
          <p className="flex items-center gap-2">
            <Clock className="h-4 w-4" /> {reservation?.reservationTime?.slice(0, 5) ?? "--:--"} · {reservation?.reservationDate ?? "—"}
          </p>
          <p className="flex items-center gap-2">
            <Users className="h-4 w-4" /> {reservation?.partySize ?? "—"} khách
          </p>
          <p className="flex items-center gap-2">
            <Phone className="h-4 w-4" /> {reservation?.guestPhone ?? "Chưa có số điện thoại"}
          </p>
          {reservation?.specialRequests && <p className="text-muted-foreground">Ghi chú: {reservation.specialRequests}</p>}
        </div>
        <Button
          className="w-full bg-primary hover:bg-primary-hover"
          disabled={!reservation || reservation.status !== "CONFIRMED" || checkInMutation.isPending}
          onClick={() => checkInMutation.mutate()}
        >
          {checkInMutation.isPending ? "Đang xác nhận…" : "Xác nhận khách đến (Check-in)"}
        </Button>
      </DialogContent>
    </Dialog>
  );
}

function OrderDialog({
  table,
  onClose,
  onBill,
}: {
  table: Table;
  onClose: () => void;
  onBill: () => void;
}) {
  const queryClient = useQueryClient();
  const [cart, setCart] = useState<CartLine[]>([]);

  // Bàn thật trong hệ thống — ánh xạ theo số bàn để áp dụng đúng quy tắc nghiệp vụ.
  const tablesQuery = useQuery({
    queryKey: ["tables"],
    queryFn: () => tableApi.list(),
    staleTime: 15_000,
  });
  const realTable = tablesQuery.data?.find((t) => t.tableNumber === table.name);

  const menuQuery = useQuery({
    queryKey: ["menu"],
    queryFn: () => menuApi.getOverview(),
    staleTime: 5 * 60 * 1000,
  });
  const dishes = menuQuery.data?.categories.flatMap((c) => c.items ?? []) ?? [];

  const activeOrder = useActiveOrder(realTable?.id);
  const createOrder = useCreateOrder();

  // Sửa / huỷ món trong đơn đang mở (STT 84, STT 85).
  const addItem = useAddOrderItem();
  const updateItem = useUpdateOrderItem();
  const cancelItem = useCancelOrderItem();

  // Ghi chú đang sửa của từng món (chỉ gửi API khi blur khỏi ô nhập).
  const [notes, setNotes] = useState<Record<string, string>>({});
  const [cancelTarget, setCancelTarget] = useState<OrderItemResponse | null>(null);
  const [cancelReason, setCancelReason] = useState("Khách đổi món");

  const updateTableStatus = useMutation({
    mutationFn: (payload: { id: string; status: TableStatus }) =>
      tableApi.updateStatus(payload.id, payload.status),
    onSuccess: (updated) => {
      toast.success(`${updated.tableNumber}: ${TABLE_STATUS_LABELS[updated.status]}.`);
      void queryClient.invalidateQueries({ queryKey: ["tables"] });
    },
    onError: (error: Error) => toast.error(error.message),
  });

  // STT 90: bếp báo món xong thì phục vụ nhận thông báo ngay.
  useKitchenCompleted((message) => {
    if (message.tableId !== realTable?.id) return;
    toast.success(`${message.tableNumber}: ${message.itemName} đã xong, sẵn sàng phục vụ.`);
    void queryClient.invalidateQueries({ queryKey: ["orders"] });
  });

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
  const isOccupied = realTable?.status === "OCCUPIED";
  const canSubmit = Boolean(realTable) && isOccupied && cart.length > 0 && !createOrder.isPending;

  // Đơn đang mở của bàn — khi có đơn, chọn món sẽ thêm thẳng vào đơn (STT 84).
  const orderId = activeOrder.data?.id;
  const hasOpenOrder = Boolean(orderId);

  /** Số lượng đang có trong đơn (bỏ qua món đã huỷ). */
  const orderedQty = (menuItemId: string) =>
    activeOrder.data?.items
      .filter((i) => i.menuItemId === menuItemId && i.cookingStatus !== "CANCELLED")
      .reduce((sum, i) => sum + i.quantity, 0) ?? 0;

  /** Chọn món: có đơn đang mở thì thêm vào đơn, ngược lại cộng vào giỏ. */
  const selectDish = (menuItemId: string) => {
    if (orderId) {
      addItem.mutate({ orderId, payload: { menuItemId, quantity: 1 } });
      return;
    }
    change(menuItemId, 1);
  };

  /** Tăng/giảm số lượng của một món đã có trong đơn (không cho về 0 — phải huỷ món). */
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

  /** Lưu ghi chú của món khi người dùng rời khỏi ô nhập. */
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

  const openCancelDialog = (item: OrderItemResponse) => {
    setCancelReason("Khách đổi món");
    setCancelTarget(item);
  };

  /** Món đã nấu xong (READY/SERVED) hoặc đã huỷ thì không sửa/huỷ được (BR-39). */
  const isLockedItem = (item: OrderItemResponse) =>
    item.cookingStatus === "READY" ||
    item.cookingStatus === "SERVED" ||
    item.cookingStatus === "CANCELLED";

  /**
   * BR-41: chỉ món còn ở mục "Chờ chế biến" mới huỷ được — món đã được xác nhận
   * đang nấu thì phải xử lý khi thanh toán.
   */
  const isCancellable = (item: OrderItemResponse) => item.cookingStatus === "QUEUED";

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-h-[90vh] max-w-3xl overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="font-display text-3xl">{table.name} · Gọi món</DialogTitle>
          <DialogDescription>Món hết hàng sẽ không thể chọn.</DialogDescription>
        </DialogHeader>

        {tablesQuery.isLoading && (
          <p className="text-sm text-muted-foreground">Đang tải thông tin bàn…</p>
        )}

        {realTable && !isOccupied && (
          <div className="space-y-2 rounded-lg border border-amber-500/40 bg-amber-500/10 px-3 py-2 text-sm">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <span>
                <strong>{realTable.tableNumber}</strong> đang ở trạng thái{" "}
                <strong>{TABLE_STATUS_LABELS[realTable.status]}</strong>. Cần mở bàn (đang phục vụ)
                trước khi gọi món.
              </span>
              <Button
                size="sm"
                className="bg-primary hover:bg-primary-hover"
                disabled={updateTableStatus.isPending}
                onClick={() => updateTableStatus.mutate({ id: realTable.id, status: "OCCUPIED" })}
              >
                Mở bàn
              </Button>
            </div>

            <div className="flex flex-wrap items-center gap-2 border-t border-amber-500/20 pt-2">
              <span className="text-xs text-muted-foreground">Đổi trạng thái bàn:</span>
              <Select
                value={realTable.status}
                onValueChange={(value) =>
                  updateTableStatus.mutate({
                    id: realTable.id,
                    status: value as TableStatus,
                  })
                }
              >
                <SelectTrigger className="h-8 w-[10rem] bg-card">
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
        )}

        {tablesQuery.isSuccess && !realTable && (
          <p className="rounded-md border border-alert/30 bg-alert/5 px-3 py-2 text-sm text-alert">
            Không tìm thấy bàn này trong hệ thống. Vui lòng kiểm tra lại danh sách bàn.
          </p>
        )}

        <div className="grid gap-6 md:grid-cols-2">
          <div className="space-y-2">
            <p className="text-xs uppercase tracking-widest text-muted-foreground">Thực đơn</p>
            {menuQuery.isLoading && (
              <p className="text-sm text-muted-foreground">Đang tải thực đơn…</p>
            )}
            {menuQuery.isError && (
              <p className="text-sm text-alert">
                {menuQuery.error instanceof Error
                  ? menuQuery.error.message
                  : "Không tải được thực đơn."}
              </p>
            )}
            {dishes.map((d) => {
              const shownQty = hasOpenOrder ? orderedQty(d.id) : qty(d.id);
              return (
                <div
                  key={d.id}
                  className={cn(
                    "flex items-center justify-between rounded-lg border p-3",
                    !d.isAvailable && "opacity-50",
                  )}
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
                      className="h-7 w-7"
                      disabled={!d.isAvailable || hasOpenOrder || qty(d.id) === 0}
                      onClick={() => change(d.id, -1)}
                    >
                      <Minus className="h-3 w-3" />
                    </Button>
                    <span className="w-5 text-center text-sm tabular-nums">{shownQty}</span>
                    <Button
                      size="icon"
                      variant="outline"
                      className="h-7 w-7"
                      disabled={!d.isAvailable || addItem.isPending}
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
              <p className="mb-2 text-xs uppercase tracking-widest text-muted-foreground">
                Đã gửi bếp
              </p>
              {activeOrder.isLoading && (
                <p className="text-sm text-muted-foreground">Đang tải đơn hàng…</p>
              )}
              {activeOrder.data && activeOrder.data.items.length === 0 && (
                <p className="text-sm text-muted-foreground">Chưa có món.</p>
              )}
              {activeOrder.data?.items.map((item) => {
                const locked = isLockedItem(item);
                const cancelled = item.cookingStatus === "CANCELLED";
                const cooked = item.cookingStatus === "READY" || item.cookingStatus === "SERVED";
                const preparing = item.cookingStatus === "PREPARING";
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
                      {cooked && (
                        <span className="text-xs text-muted-foreground">
                          · Bếp đã nấu xong, không sửa/huỷ được
                        </span>
                      )}
                      {preparing && (
                        <span className="text-xs text-muted-foreground">
                          · Đang nấu, không huỷ được nữa
                        </span>
                      )}
                      {cancelled && item.cancelReason && (
                        <span className="text-xs text-alert">Lý do: {item.cancelReason}</span>
                      )}
                    </div>
                    {!locked && (
                      <div className="mt-1.5 flex items-center gap-2">
                        <Button
                          size="icon"
                          variant="outline"
                          className="h-7 w-7"
                          disabled={item.quantity <= 1 || updateItem.isPending}
                          onClick={() => changeItemQty(item, -1)}
                        >
                          <Minus className="h-3 w-3" />
                        </Button>
                        <span className="w-5 text-center text-sm tabular-nums">
                          {item.quantity}
                        </span>
                        <Button
                          size="icon"
                          variant="outline"
                          className="h-7 w-7"
                          disabled={updateItem.isPending}
                          onClick={() => changeItemQty(item, 1)}
                        >
                          <Plus className="h-3 w-3" />
                        </Button>
                        <Input
                          className="h-8 min-w-0 flex-1 text-xs"
                          placeholder="Ghi chú cho bếp"
                          value={notes[item.id] ?? item.note ?? ""}
                          onChange={(e) =>
                            setNotes((cur) => ({ ...cur, [item.id]: e.target.value }))
                          }
                          onBlur={() => saveItemNote(item)}
                        />
                        {isCancellable(item) && (
                          <Button
                            size="icon"
                            variant="ghost"
                            className="h-8 w-8 text-alert hover:bg-alert/10 hover:text-alert"
                            disabled={cancelItem.isPending}
                            onClick={() => openCancelDialog(item)}
                            aria-label="Huỷ món"
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
              <p className="mb-2 text-xs uppercase tracking-widest text-muted-foreground">
                Món mới
              </p>
              {hasOpenOrder ? (
                <p className="text-sm text-muted-foreground">
                  Bàn đang có đơn mở. Chọn món ở bên trái để thêm thẳng vào đơn.
                </p>
              ) : cart.length === 0 ? (
                <p className="text-sm text-muted-foreground">Chọn món ở bên trái.</p>
              ) : (
                <div className="space-y-2">
                  {cart.map((line) => (
                    <div key={line.menuItemId} className="space-y-1">
                      <p className="text-sm">
                        {line.quantity} × {dishName(line.menuItemId)}
                      </p>
                      <Input
                        className="h-8 text-xs"
                        placeholder="Ghi chú cho bếp (vd: Ít cay, không hành)"
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
                  if (!realTable) return;
                  createOrder.mutate(
                    {
                      tableId: realTable.id,
                      items: cart.map((line) => ({
                        menuItemId: line.menuItemId,
                        quantity: line.quantity,
                        note: line.note.trim() || undefined,
                      })),
                      submit: true,
                    },
                    {
                      onSuccess: () => {
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
            <Button
              className="w-full bg-primary hover:bg-primary-hover"
              disabled={!activeOrder.data || activeOrder.data.items.length === 0}
              onClick={onBill}
            >
              <Receipt className="mr-2 h-4 w-4" /> Tạo Hoá Đơn / Thanh Toán
            </Button>
          </div>
        </div>

        <AlertDialog
          open={Boolean(cancelTarget)}
          onOpenChange={(open) => {
            if (!open) setCancelTarget(null);
          }}
        >
          <AlertDialogContent>
            <AlertDialogHeader>
              <AlertDialogTitle className="font-display text-2xl">Huỷ món</AlertDialogTitle>
              <AlertDialogDescription>
                Huỷ món {cancelTarget?.itemName} khỏi đơn? Bếp sẽ nhận được yêu cầu huỷ món này.
              </AlertDialogDescription>
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
                  // Giữ hộp thoại mở cho tới khi API trả về kết quả.
                  e.preventDefault();
                  if (!orderId || !cancelTarget) return;
                  cancelItem.mutate(
                    {
                      orderId,
                      itemId: cancelTarget.id,
                      reason: cancelReason.trim(),
                    },
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

function BillingDialog({ tableId, onClose }: { tableId: number; onClose: () => void }) {
  const { tables, completePayment } = useRestaurant();
  const queryClient = useQueryClient();
  const realTablesQuery = useQuery({
    queryKey: ["tables"],
    queryFn: () => tableApi.list(),
    staleTime: 0,
    refetchInterval: 5000,
  });
  const mockTable = tables.find((item) => item.id === tableId);
  const fallbackRealTable = tableId >= 100000
    ? realTablesQuery.data?.[tableId - 100000]
    : undefined;
  const table: Table | undefined = mockTable ?? (fallbackRealTable ? {
    id: tableId,
    name: fallbackRealTable.tableNumber,
    zone: getZoneFromDatabase(fallbackRealTable),
    seats: fallbackRealTable.capacity,
    status: toMockStatus(fallbackRealTable.status),
    order: [],
  } : undefined);
  const realTable = realTablesQuery.data?.find((item) =>
    item.tableNumber.trim().toLocaleLowerCase("vi-VN") === table?.name.trim().toLocaleLowerCase("vi-VN")
  );
  const sentOrdersQuery = useQuery({
    queryKey: ["orders", "table", realTable?.id, "unpaid"],
    queryFn: () => orderApi.list({ tableId: realTable!.id, status: "IN_PROGRESS" }),
    enabled: Boolean(realTable?.id),
    staleTime: 0,
  });
  const sentItems = (sentOrdersQuery.data ?? []).flatMap((order) => order.items);
  const [code, setCode] = useState("");
  const [redeem, setRedeem] = useState(false);
  const [method, setMethod] = useState<string>("Tiền mặt");
  const [phoneNumber, setPhoneNumber] = useState("");
  const [loyaltyBalance, setLoyaltyBalance] = useState(0);
  const [loyaltyBusy, setLoyaltyBusy] = useState(false);
  const [loyaltyChecked, setLoyaltyChecked] = useState(false);
  const [voucherBusy, setVoucherBusy] = useState(false);
  const [voucherValidated, setVoucherValidated] = useState(false);
  const [voucherDiscount, setVoucherDiscount] = useState(0);
  // Hóa đơn chỉ lấy món đã được lưu và gửi bếp từ backend; không lấy giỏ nháp/local mock.
  // BR-41: món đã huỷ vẫn hiển thị để đối soát nhưng không được tính tiền.
  const bill = splitBillItems(sentItems);
  const subtotal = bill.subtotal;
  const vat = subtotal * 0.1;
  // Chỉ áp dụng giảm giá sau khi backend đã xác nhận voucher hợp lệ.
  const discount = voucherValidated ? Math.min(voucherDiscount, subtotal + vat) : 0;
  const beforeRedeem = subtotal + vat - discount;
  // Điểm chỉ dùng theo bội số 100, không vượt số dư hoặc số tiền còn lại.
  const redeemed = redeem ? Math.floor(Math.min(loyaltyBalance, beforeRedeem) / 100) * 100 : 0;
  const total = beforeRedeem - redeemed;

  const Row = ({ l, v, cls }: { l: string; v: string; cls?: string }) => (
    <div className={cn("flex justify-between text-sm", cls)}>
      <span>{l}</span>
      <span className="tabular-nums">{v}</span>
    </div>
  );

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle className="font-display text-3xl">Hoá đơn · {table?.name ?? "Bàn"}</DialogTitle>
        </DialogHeader>
        <div className="space-y-1.5 border-b pb-3">
          {bill.lines.map((line) => (
            <Row key={line.key} l={line.label} v={formatVND(line.amount)} />
          ))}
          {bill.cancelled.length > 0 && (
            <div className="pt-1">
              <Row
                l={`Món đã huỷ (${bill.cancelled.length}) — không tính tiền`}
                v={`-${formatVND(bill.cancelledAmount)}`}
                cls="text-muted-foreground line-through"
              />
              <p className="text-xs text-muted-foreground">
                Món huỷ khi còn ở mục chờ chế biến được loại khỏi hoá đơn.
              </p>
            </div>
          )}
          {!realTable && (
            <p className="text-sm text-alert">Không tìm thấy bàn trong dữ liệu hệ thống.</p>
          )}
          {realTable && sentOrdersQuery.isLoading && (
            <p className="text-sm text-muted-foreground">Đang tải món đã gửi bếp…</p>
          )}
          {realTable && sentOrdersQuery.isSuccess && bill.lines.length === 0 && (
            <p className="text-sm text-alert">Bàn chưa có món nào đã gửi bếp để tính hóa đơn.</p>
          )}
        </div>
        <div className="space-y-1.5">
          <Row l="Tạm tính" v={formatVND(subtotal)} />
          <Row l="VAT (10%)" v={formatVND(vat)} />
          <div className="space-y-2 pt-1">
            <Input
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
              placeholder="Mã voucher"
              value={code}
              onChange={(e) => {
                setCode(e.target.value);
                setVoucherValidated(false);
                setVoucherDiscount(0);
              }}
            />
            <Button
              type="button"
              variant="outline"
              className="w-full"
              disabled={loyaltyBusy || !phoneNumber.trim()}
              onClick={async () => {
                setLoyaltyBusy(true);
                setLoyaltyChecked(false);
                try {
                  const result = await paymentApi.getLoyaltyBalance(phoneNumber.trim());
                  setLoyaltyBalance(Math.max(0, Number(result.pointsBalance) || 0));
                  setLoyaltyChecked(true);
                  toast.success(
                    `Số dư điểm: ${formatVND(Math.max(0, Number(result.pointsBalance) || 0))}`,
                  );
                } catch (error) {
                  setLoyaltyBalance(0);
                  setRedeem(false);
                  toast.error(
                    error instanceof Error ? error.message : "Không tra cứu được số dư điểm.",
                  );
                } finally {
                  setLoyaltyBusy(false);
                }
              }}
            >
              {loyaltyBusy ? "Đang tra cứu điểm..." : "Tra cứu số dư điểm"}
            </Button>
            <Button
              type="button"
              variant="outline"
              className="w-full"
              disabled={voucherBusy || !code.trim() || !phoneNumber.trim()}
              onClick={async () => {
                setVoucherBusy(true);
                try {
                  const result = await validateVoucher(
                    code.trim(),
                    phoneNumber.trim(),
                    subtotal + vat,
                  );
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
                  toast.error(
                    error instanceof Error ? error.message : "Không kiểm tra được voucher.",
                  );
                } finally {
                  setVoucherBusy(false);
                }
              }}
            >
              {voucherBusy ? "Đang kiểm tra..." : "Kiểm tra voucher"}
            </Button>
          </div>
          {code && (
            <p className={cn("text-xs", voucherValidated ? "text-moss" : "text-muted-foreground")}>
              {voucherValidated
                ? "Voucher đã được kiểm tra hợp lệ."
                : "Nhập số điện thoại và kiểm tra voucher trước khi thanh toán."}
            </p>
          )}
          {discount > 0 && <Row l="Voucher" v={`-${formatVND(discount)}`} cls="text-moss" />}
          <div className="flex items-center justify-between rounded-lg bg-muted p-3 text-sm">
            <span>Dùng điểm tích luỹ ({formatVND(loyaltyChecked ? loyaltyBalance : 0)})</span>
            <Switch
              checked={redeem}
              disabled={!phoneNumber.trim() || !loyaltyChecked || loyaltyBalance <= 0}
              onCheckedChange={setRedeem}
            />
          </div>
          {!phoneNumber.trim() && (
            <p className="text-xs text-muted-foreground">
              Nhập số điện thoại để tra cứu điểm và dùng voucher.
            </p>
          )}
          {phoneNumber.trim() && !loyaltyChecked && (
            <p className="text-xs text-muted-foreground">
              Nhấn “Tra cứu số dư điểm” trước khi sử dụng điểm tích luỹ.
            </p>
          )}
          {redeemed > 0 && <Row l="Điểm đã dùng" v={`-${formatVND(redeemed)}`} cls="text-moss" />}
          <Row
            l="Tổng thanh toán"
            v={formatVND(total)}
            cls="pt-2 font-display text-2xl font-semibold"
          />
          <p className="text-xs text-muted-foreground">
            {method === "VNPAY"
              ? "Điểm VNPAY sẽ được cộng theo tỷ lệ do Manager cấu hình sau khi giao dịch thành công."
              : `Điểm thưởng sẽ được cộng vào ví của số điện thoại đã nhập sau khi thanh toán thành công.`}
          </p>
        </div>
        <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
          {methods.map((m) => (
            <Button
              key={m}
              variant="outline"
              onClick={() => setMethod(m)}
              className={cn(
                "h-auto py-2 text-xs",
                method === m && "border-primary bg-primary/5 text-primary",
              )}
            >
              {m}
            </Button>
          ))}
        </div>
        <Button
          className="w-full bg-primary hover:bg-primary-hover"
          disabled={
            voucherBusy ||
            sentOrdersQuery.isLoading ||
            !realTable ||
            bill.lines.length === 0 ||
            (!!code.trim() && !voucherValidated)
          }
          onClick={async () => {
            if (method === "VNPAY") {
              if (redeem && (!phoneNumber.trim() || !loyaltyChecked)) {
                toast.error("Nhập số điện thoại và tra cứu số dư điểm trước khi sử dụng điểm.");
                return;
              }
              if (code.trim() && !voucherValidated) {
                toast.error("Vui lòng kiểm tra voucher trước khi thanh toán.");
                return;
              }
              setVoucherBusy(true);
              try {
                if (!realTable?.id) {
                  toast.error("Không tìm thấy bàn trong dữ liệu backend.");
                  return;
                }
                const payment = await paymentApi.createVnpay({
                  tableId: String(realTable.id),
                  subtotal: subtotal + vat,
                  phoneNumber: phoneNumber.trim(),
                  ...(code.trim() ? { voucherCode: code.trim() } : {}),
                  ...(redeem && redeemed > 0 ? { pointsToUse: redeemed, usePoints: true } : { usePoints: false }),
                });
                window.location.assign(payment.paymentUrl);
              } catch (error) {
                toast.error(
                  error instanceof Error ? error.message : "Không thể khởi tạo thanh toán VNPAY.",
                );
                setVoucherBusy(false);
              }
              return;
            }
            if (redeem && (!phoneNumber.trim() || !loyaltyChecked)) {
              toast.error("Nhập số điện thoại và tra cứu số dư điểm trước khi sử dụng điểm.");
              return;
            }
            if (!realTable?.id) {
              toast.error("Không tìm thấy bàn trong dữ liệu backend.");
              return;
            }
            if (sentOrdersQuery.isLoading || bill.lines.length === 0) {
              toast.error("Chưa tải được các món đã gửi bếp của bàn.");
              return;
            }
            if (code.trim() && (!phoneNumber.trim() || !voucherValidated)) {
              toast.error(
                "Vui lòng nhập số điện thoại và kiểm tra voucher hợp lệ trước khi thanh toán.",
              );
              return;
            }
            setVoucherBusy(true);
            try {
              const settled = await orderApi.checkoutCash(realTable.id, {
                phoneNumber: phoneNumber.trim() || undefined,
                voucherCode: code.trim() || undefined,
                usePoints: redeem,
              });
              // Backend đã chốt các order, cộng/trừ điểm và giải phóng bàn trong một transaction.
              completePayment(
                table?.id ?? tableId,
                Number(settled.total),
                Number(settled.pointsAwarded),
                0,
                method,
              );
              await queryClient.invalidateQueries({ queryKey: ["tables"] });
              await queryClient.invalidateQueries({ queryKey: ["orders"] });
              toast.success(
                `Thanh toán ${formatVND(Number(settled.total))} thành công — ${table?.name ?? realTable.tableNumber} đã trống. Điểm thưởng: ${formatVND(Number(settled.pointsAwarded))}`,
              );
              onClose();
            } catch (error) {
              toast.error(
                error instanceof Error ? error.message : "Không thể hoàn tất thanh toán tiền mặt.",
              );
            } finally {
              setVoucherBusy(false);
            }
          }}
        >
          Hoàn Tất Thanh Toán
        </Button>
      </DialogContent>
    </Dialog>
  );
}
