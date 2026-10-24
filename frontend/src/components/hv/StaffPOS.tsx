import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Minus, Plus, Users, Clock, Phone, ChefHat, Receipt, Wrench } from "lucide-react";
import { toast } from "sonner";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from "@/components/ui/dialog";
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
import { tableApi, type TableStatus } from "@/lib/table-api";
import { menuApi } from "@/features/menu/api/menu-api";
import { useActiveOrder } from "@/features/order/hooks/use-orders";
import { useCreateOrder } from "@/features/order/hooks/use-create-order";
import { orderApi } from "@/features/order/api/order-api";
import { useKitchenCompleted } from "@/features/order/hooks/use-kitchen-tickets";
import { COOKING_STATUS_LABELS } from "@/features/order/types/order.types";

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

export function StaffPOS() {
  const { tables, dish } = useRestaurant();
  const [active, setActive] = useState<number | null>(null);
  const [billing, setBilling] = useState<number | null>(null);
  const table = tables.find((t) => t.id === active);

  // Trạng thái bàn lấy từ hệ thống thật — lưới bàn và hộp thoại gọi món
  // phải thống nhất, nếu không nhân viên sẽ thấy hai trạng thái khác nhau.
  const realTablesQuery = useQuery({
    queryKey: ["tables"],
    queryFn: () => tableApi.list(),
    staleTime: 15_000,
  });
  const realStatusByNumber = useMemo(() => {
    const map = new Map<string, TableStatus>();
    (realTablesQuery.data ?? []).forEach((t) => map.set(t.tableNumber, t.status));
    return map;
  }, [realTablesQuery.data]);

  const counts = (s: MockTableStatus) => tables.filter((t) => t.status === s).length;

  return (
    <div className="space-y-8">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="text-xs uppercase tracking-[0.2em] text-muted-foreground">Sơ đồ bàn ăn</p>
          <h1 className="text-4xl font-semibold">Phục vụ & Thu ngân</h1>
        </div>
        <div className="flex flex-wrap gap-2">
          {(Object.keys(tableStatusMeta) as MockTableStatus[]).map((s) => (
            <span
              key={s}
              className={cn("rounded-full px-3 py-1 text-xs font-medium", tableStatusMeta[s].cls)}
            >
              {tableStatusMeta[s].label} · {counts(s)}
            </span>
          ))}
        </div>
      </div>

      {zones.map((z) => (
        <section key={z}>
          <h2 className="mb-3 text-2xl font-medium text-moss">{z}</h2>
          <div className="grid grid-cols-2 gap-5 sm:grid-cols-3 lg:grid-cols-5">
            {tables
              .filter((t) => t.zone === z)
              .map((t) => {
                const total = t.order.reduce((s, o) => s + (dish(o.dishId)?.price ?? 0) * o.qty, 0);
                const realStatus = realStatusByNumber.get(t.name);
                const isMaintenance = realStatus === "MAINTENANCE" || t.status === "maintenance";
                const statusLabel = realStatus
                  ? TABLE_STATUS_LABELS[realStatus]
                  : tableStatusMeta[t.status].label;
                const statusClass = realStatus
                  ? (REAL_STATUS_CLASSES[realStatus] ?? tableStatusMeta[t.status].cls)
                  : tableStatusMeta[t.status].cls;
                return (
                  <button
                    key={t.id}
                    disabled={isMaintenance}
                    onClick={() => setActive(t.id)}
                    className="surface-card group p-5 text-left transition hover:-translate-y-0.5 hover:border-primary/40 disabled:cursor-not-allowed disabled:opacity-60"
                  >
                    <div className="flex items-start justify-between">
                      <span className="font-display text-2xl font-semibold">{t.name}</span>
                      {isMaintenance && <Wrench className="h-4 w-4 text-muted-foreground" />}
                    </div>
                    <p className="mt-1 flex items-center gap-1 text-xs text-muted-foreground">
                      <Users className="h-3 w-3" /> {t.seats} ghế
                    </p>
                    <span
                      className={cn(
                        "mt-4 inline-block rounded-full px-2.5 py-0.5 text-xs font-medium",
                        statusClass,
                      )}
                    >
                      {statusLabel}
                    </span>
                    {t.status === "reserved" && (
                      <p className="mt-2 text-xs text-muted-foreground">
                        {t.guest} · {t.time}
                      </p>
                    )}
                    {t.status === "occupied" && (
                      <p className="mt-2 text-xs font-medium tabular-nums">{formatVND(total)}</p>
                    )}
                  </button>
                );
              })}
          </div>
        </section>
      ))}

      {table?.status === "reserved" && (
        <ReservationDialog table={table} onClose={() => setActive(null)} />
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

function ReservationDialog({ table, onClose }: { table: Table; onClose: () => void }) {
  const { checkIn } = useRestaurant();
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle className="font-display text-3xl">{table.name} · Đặt trước</DialogTitle>
          <DialogDescription>Thông tin đặt bàn của khách</DialogDescription>
        </DialogHeader>
        <div className="space-y-3 rounded-lg bg-muted p-4 text-sm">
          <p className="font-display text-xl font-semibold">{table.guest}</p>
          <p className="flex items-center gap-2">
            <Clock className="h-4 w-4" /> {table.time} hôm nay
          </p>
          <p className="flex items-center gap-2">
            <Users className="h-4 w-4" /> {table.partySize} khách
          </p>
          <p className="flex items-center gap-2">
            <Phone className="h-4 w-4" /> {table.phone}
          </p>
        </div>
        <Button
          className="w-full bg-primary hover:bg-primary-hover"
          onClick={() => {
            checkIn(table.id);
            toast.success(`Đã check-in ${table.guest} vào ${table.name}`);
            onClose();
          }}
        >
          Xác nhận khách đến (Check-in)
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
            {dishes.map((d) => (
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
                    disabled={!d.isAvailable || qty(d.id) === 0}
                    onClick={() => change(d.id, -1)}
                  >
                    <Minus className="h-3 w-3" />
                  </Button>
                  <span className="w-5 text-center text-sm tabular-nums">{qty(d.id)}</span>
                  <Button
                    size="icon"
                    variant="outline"
                    className="h-7 w-7"
                    disabled={!d.isAvailable}
                    onClick={() => change(d.id, 1)}
                  >
                    <Plus className="h-3 w-3" />
                  </Button>
                </div>
              </div>
            ))}
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
              {activeOrder.data?.items.map((item) => (
                <div key={item.id} className="flex justify-between gap-3 border-b py-1.5 text-sm">
                  <span>
                    {item.quantity} × {item.itemName}
                    {item.note && <span className="ml-2 text-xs text-alert">({item.note})</span>}
                    <span className="ml-2 text-xs text-muted-foreground">
                      {COOKING_STATUS_LABELS[item.cookingStatus]}
                    </span>
                  </span>
                  <span className="shrink-0 tabular-nums">{formatVND(item.lineTotal)}</span>
                </div>
              ))}
            </div>
            <div>
              <p className="mb-2 text-xs uppercase tracking-widest text-muted-foreground">
                Món mới
              </p>
              {cart.length === 0 ? (
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
            <Button
              className="w-full bg-primary hover:bg-primary-hover"
              disabled={!activeOrder.data || activeOrder.data.items.length === 0}
              onClick={onBill}
            >
              <Receipt className="mr-2 h-4 w-4" /> Tạo Hoá Đơn / Thanh Toán
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}

const methods = ["Tiền mặt", "VNPAY"];

function BillingDialog({ tableId, onClose }: { tableId: number; onClose: () => void }) {
  const { tables, completePayment } = useRestaurant();
  const queryClient = useQueryClient();
  const table = tables.find((t) => t.id === tableId)!;
  const realTablesQuery = useQuery({ queryKey: ["tables"], queryFn: () => tableApi.list(), staleTime: 0 });
  const realTable = realTablesQuery.data?.find((t) => t.tableNumber === table.name);
  const sentOrdersQuery = useQuery({
    queryKey: ["orders", "table", realTable?.id, "unpaid"],
    queryFn: () => orderApi.list({ tableId: realTable!.id, status: "IN_PROGRESS" }),
    enabled: Boolean(realTable?.id),
    staleTime: 0,
  });
  const sentItems = (sentOrdersQuery.data ?? []).flatMap((order) => order.items)
    .filter((item) => item.cookingStatus !== "CANCELLED");
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
  const subtotal = sentItems.reduce((sum, item) => sum + Number(item.lineTotal || 0), 0);
  const vat = subtotal * 0.1;
  // Chỉ áp dụng giảm giá sau khi backend đã xác nhận voucher hợp lệ.
  const discount = voucherValidated ? Math.min(voucherDiscount, subtotal + vat) : 0;
  const beforeRedeem = subtotal + vat - discount;
  const redeemed = redeem ? Math.min(loyaltyBalance, beforeRedeem) : 0;
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
          <DialogTitle className="font-display text-3xl">Hoá đơn · {table.name}</DialogTitle>
        </DialogHeader>
        <div className="space-y-1.5 border-b pb-3">
          {sentItems.map((item) => (
            <Row
              key={item.id}
              l={`${item.quantity} × ${item.itemName ?? "Món ăn"}`}
              v={formatVND(Number(item.lineTotal || 0))}
            />
          ))}
          {!realTable && <p className="text-sm text-alert">Không tìm thấy bàn trong dữ liệu hệ thống.</p>}
          {realTable && sentOrdersQuery.isLoading && <p className="text-sm text-muted-foreground">Đang tải món đã gửi bếp…</p>}
          {realTable && sentOrdersQuery.isSuccess && sentItems.length === 0 && <p className="text-sm text-alert">Bàn chưa có món nào đã gửi bếp để tính hóa đơn.</p>}
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
          disabled={voucherBusy || sentOrdersQuery.isLoading || !realTable || sentItems.length === 0 || (!!code.trim() && !voucherValidated)}
          onClick={async () => {
            if (method === "VNPAY") {
              if (redeem) {
                toast.error(
                  "Chưa hỗ trợ thanh toán VNPay kèm sử dụng điểm. Vui lòng tắt sử dụng điểm hoặc chọn tiền mặt.",
                );
                return;
              }
              if (code.trim() && !voucherValidated) {
                toast.error("Vui lòng kiểm tra voucher trước khi thanh toán.");
                return;
              }
              setVoucherBusy(true);
              try {
                const payment = await paymentApi.createVnpay({
                  subtotal: subtotal + vat,
                  phoneNumber: phoneNumber.trim(),
                  ...(code.trim() ? { voucherCode: code.trim() } : {}),
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
            if (sentOrdersQuery.isLoading || sentItems.length === 0) {
              toast.error("Chưa tải được các món đã gửi bếp của bàn.");
              return;
            }
            if (code.trim() && (!phoneNumber.trim() || !voucherValidated)) {
              toast.error("Vui lòng nhập số điện thoại và kiểm tra voucher hợp lệ trước khi thanh toán.");
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
              completePayment(table.id, Number(settled.total), Number(settled.pointsAwarded), 0, method);
              await queryClient.invalidateQueries({ queryKey: ["tables"] });
              await queryClient.invalidateQueries({ queryKey: ["orders"] });
              toast.success(`Thanh toán ${formatVND(Number(settled.total))} thành công — ${table.name} đã trống. Điểm thưởng: ${formatVND(Number(settled.pointsAwarded))}`);
              onClose();
            } catch (error) {
              toast.error(error instanceof Error ? error.message : "Không thể hoàn tất thanh toán tiền mặt.");
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
