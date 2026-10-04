import { useState } from "react";
import { Minus, Plus, Users, Clock, Phone, ChefHat, Receipt, Wrench } from "lucide-react";
import { toast } from "sonner";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Switch } from "@/components/ui/switch";
import { formatVND, useRestaurant, type OrderLine, type Table, type TableStatus, type Zone } from "@/lib/restaurant-store";
import { cn } from "@/lib/utils";

export const tableStatusMeta: Record<TableStatus, { label: string; cls: string }> = {
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

  const counts = (s: TableStatus) => tables.filter((t) => t.status === s).length;

  return (
    <div className="space-y-8">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="text-xs uppercase tracking-[0.2em] text-muted-foreground">Sơ đồ bàn ăn</p>
          <h1 className="text-4xl font-semibold">Phục vụ & Thu ngân</h1>
        </div>
        <div className="flex flex-wrap gap-2">
          {(Object.keys(tableStatusMeta) as TableStatus[]).map((s) => (
            <span key={s} className={cn("rounded-full px-3 py-1 text-xs font-medium", tableStatusMeta[s].cls)}>
              {tableStatusMeta[s].label} · {counts(s)}
            </span>
          ))}
        </div>
      </div>

      {zones.map((z) => (
        <section key={z}>
          <h2 className="mb-3 text-2xl font-medium text-moss">{z}</h2>
          <div className="grid grid-cols-2 gap-5 sm:grid-cols-3 lg:grid-cols-5">
            {tables.filter((t) => t.zone === z).map((t) => {
              const total = t.order.reduce((s, o) => s + (dish(o.dishId)?.price ?? 0) * o.qty, 0);
              return (
                <button
                  key={t.id}
                  disabled={t.status === "maintenance"}
                  onClick={() => (t.status === "available" ? toast.info(`${t.name} đang trống — sẵn sàng đón khách`) : setActive(t.id))}
                  className="surface-card group p-5 text-left transition hover:-translate-y-0.5 hover:border-primary/40 disabled:cursor-not-allowed disabled:opacity-60"
                >
                  <div className="flex items-start justify-between">
                    <span className="font-display text-2xl font-semibold">{t.name}</span>
                    {t.status === "maintenance" && <Wrench className="h-4 w-4 text-muted-foreground" />}
                  </div>
                  <p className="mt-1 flex items-center gap-1 text-xs text-muted-foreground"><Users className="h-3 w-3" /> {t.seats} ghế</p>
                  <span className={cn("mt-4 inline-block rounded-full px-2.5 py-0.5 text-xs font-medium", tableStatusMeta[t.status].cls)}>
                    {tableStatusMeta[t.status].label}
                  </span>
                  {t.status === "reserved" && <p className="mt-2 text-xs text-muted-foreground">{t.guest} · {t.time}</p>}
                  {t.status === "occupied" && <p className="mt-2 text-xs font-medium tabular-nums">{formatVND(total)}</p>}
                </button>
              );
            })}
          </div>
        </section>
      ))}

      {table?.status === "reserved" && <ReservationDialog table={table} onClose={() => setActive(null)} />}
      {table?.status === "occupied" && (
        <OrderDialog table={table} onClose={() => setActive(null)} onBill={() => { setBilling(table.id); setActive(null); }} />
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
          <p className="flex items-center gap-2"><Clock className="h-4 w-4" /> {table.time} hôm nay</p>
          <p className="flex items-center gap-2"><Users className="h-4 w-4" /> {table.partySize} khách</p>
          <p className="flex items-center gap-2"><Phone className="h-4 w-4" /> {table.phone}</p>
        </div>
        <Button className="w-full bg-primary hover:bg-primary-hover" onClick={() => { checkIn(table.id); toast.success(`Đã check-in ${table.guest} vào ${table.name}`); onClose(); }}>
          Xác nhận khách đến (Check-in)
        </Button>
      </DialogContent>
    </Dialog>
  );
}

function OrderDialog({ table, onClose, onBill }: { table: Table; onClose: () => void; onBill: () => void }) {
  const { dishes, dish, sendToKitchen } = useRestaurant();
  const [draft, setDraft] = useState<OrderLine[]>([]);
  const [note, setNote] = useState("");
  const change = (dishId: string, d: number) =>
    setDraft((cur) => {
      const ex = cur.find((l) => l.dishId === dishId);
      if (!ex) return d > 0 ? [...cur, { dishId, qty: 1 }] : cur;
      return cur.map((l) => (l.dishId === dishId ? { ...l, qty: l.qty + d } : l)).filter((l) => l.qty > 0);
    });
  const qty = (id: string) => draft.find((l) => l.dishId === id)?.qty ?? 0;

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-h-[90vh] max-w-3xl overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="font-display text-3xl">{table.name} · Gọi món</DialogTitle>
          <DialogDescription>Món hết hàng sẽ không thể chọn.</DialogDescription>
        </DialogHeader>
        <div className="grid gap-6 md:grid-cols-2">
          <div className="space-y-2">
            <p className="text-xs uppercase tracking-widest text-muted-foreground">Thực đơn</p>
            {dishes.map((d) => (
              <div key={d.id} className={cn("flex items-center justify-between rounded-lg border p-3", !d.inStock && "opacity-50")}>
                <div>
                  <p className="text-sm font-medium">{d.name}</p>
                  <p className="text-xs tabular-nums text-muted-foreground">{d.inStock ? formatVND(d.price) : "Hết món"}</p>
                </div>
                <div className="flex items-center gap-2">
                  <Button size="icon" variant="outline" className="h-7 w-7" disabled={!d.inStock || qty(d.id) === 0} onClick={() => change(d.id, -1)}><Minus className="h-3 w-3" /></Button>
                  <span className="w-5 text-center text-sm tabular-nums">{qty(d.id)}</span>
                  <Button size="icon" variant="outline" className="h-7 w-7" disabled={!d.inStock} onClick={() => change(d.id, 1)}><Plus className="h-3 w-3" /></Button>
                </div>
              </div>
            ))}
          </div>
          <div className="space-y-4">
            <div>
              <p className="mb-2 text-xs uppercase tracking-widest text-muted-foreground">Đã gửi bếp</p>
              {table.order.length === 0 && <p className="text-sm text-muted-foreground">Chưa có món.</p>}
              {table.order.map((o) => (
                <div key={o.dishId} className="flex justify-between border-b py-1.5 text-sm">
                  <span>{o.qty} × {dish(o.dishId)?.name}</span>
                  <span className="tabular-nums">{formatVND((dish(o.dishId)?.price ?? 0) * o.qty)}</span>
                </div>
              ))}
            </div>
            <div>
              <p className="mb-2 text-xs uppercase tracking-widest text-muted-foreground">Món mới</p>
              {draft.length === 0 ? <p className="text-sm text-muted-foreground">Chọn món ở bên trái.</p> :
                draft.map((l) => <p key={l.dishId} className="text-sm">{l.qty} × {dish(l.dishId)?.name}</p>)}
            </div>
            <Input placeholder="Ghi chú cho bếp (vd: Ít cay, không hành)" value={note} onChange={(e) => setNote(e.target.value)} />
            <Button
              className="w-full bg-moss text-moss-foreground hover:bg-moss/90"
              disabled={draft.length === 0}
              onClick={() => { sendToKitchen(table.id, draft, note); toast.success(`Đã gửi ${draft.length} món xuống bếp`); setDraft([]); setNote(""); }}
            >
              <ChefHat className="mr-2 h-4 w-4" /> Gửi Bếp
            </Button>
            <Button className="w-full bg-primary hover:bg-primary-hover" disabled={table.order.length === 0} onClick={onBill}>
              <Receipt className="mr-2 h-4 w-4" /> Tạo Hoá Đơn / Thanh Toán
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}

const vouchers: Record<string, { label: string; calc: (s: number) => number }> = {
  HUONGVIET10: { label: "Giảm 10%", calc: (s) => s * 0.1 },
  CODO50K: { label: "Giảm 50.000đ", calc: () => 50000 },
};
const methods = ["Tiền mặt", "Chuyển khoản QR", "Thẻ"];

function BillingDialog({ tableId, onClose }: { tableId: number; onClose: () => void }) {
  const { tables, dish, loyaltyBalance, completePayment } = useRestaurant();
  const table = tables.find((t) => t.id === tableId)!;
  const [code, setCode] = useState("");
  const [redeem, setRedeem] = useState(false);
  const [method, setMethod] = useState(methods[0]);
  const subtotal = table.order.reduce((s, o) => s + (dish(o.dishId)?.price ?? 0) * o.qty, 0);
  const vat = subtotal * 0.1;
  const v = vouchers[code.trim().toUpperCase()];
  const discount = v ? Math.min(v.calc(subtotal), subtotal) : 0;
  const beforeRedeem = subtotal + vat - discount;
  const redeemed = redeem ? Math.min(loyaltyBalance, beforeRedeem) : 0;
  const total = beforeRedeem - redeemed;
  const cashback = Math.round(total * 0.05);

  const Row = ({ l, v, cls }: { l: string; v: string; cls?: string }) => (
    <div className={cn("flex justify-between text-sm", cls)}><span>{l}</span><span className="tabular-nums">{v}</span></div>
  );

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle className="font-display text-3xl">Hoá đơn · {table.name}</DialogTitle>
        </DialogHeader>
        <div className="space-y-1.5 border-b pb-3">
          {table.order.map((o) => (
            <Row key={o.dishId} l={`${o.qty} × ${dish(o.dishId)?.name}`} v={formatVND((dish(o.dishId)?.price ?? 0) * o.qty)} />
          ))}
        </div>
        <div className="space-y-1.5">
          <Row l="Tạm tính" v={formatVND(subtotal)} />
          <Row l="VAT (10%)" v={formatVND(vat)} />
          <div className="flex gap-2 pt-1">
            <Input placeholder="Mã voucher (HUONGVIET10, CODO50K)" value={code} onChange={(e) => setCode(e.target.value)} />
          </div>
          {code && <p className={cn("text-xs", v ? "text-moss" : "text-alert")}>{v ? `Áp dụng: ${v.label}` : "Mã không hợp lệ"}</p>}
          {discount > 0 && <Row l="Voucher" v={`-${formatVND(discount)}`} cls="text-moss" />}
          <div className="flex items-center justify-between rounded-lg bg-muted p-3 text-sm">
            <span>Dùng điểm tích luỹ ({formatVND(loyaltyBalance)})</span>
            <Switch checked={redeem} onCheckedChange={setRedeem} />
          </div>
          {redeemed > 0 && <Row l="Điểm đã dùng" v={`-${formatVND(redeemed)}`} cls="text-moss" />}
          <Row l="Tổng thanh toán" v={formatVND(total)} cls="pt-2 font-display text-2xl font-semibold" />
          <p className="text-xs text-muted-foreground">Hoàn điểm 5%: +{formatVND(cashback)} vào tài khoản khách</p>
        </div>
        <div className="grid grid-cols-3 gap-2">
          {methods.map((m) => (
            <Button key={m} variant="outline" onClick={() => setMethod(m)} className={cn("h-auto py-2 text-xs", method === m && "border-primary bg-primary/5 text-primary")}>{m}</Button>
          ))}
        </div>
        <Button className="w-full bg-primary hover:bg-primary-hover" onClick={() => {
          completePayment(table.id, total, cashback, redeemed, method);
          toast.success(`Thanh toán ${formatVND(total)} thành công — ${table.name} đã trống`);
          onClose();
        }}>Hoàn Tất Thanh Toán</Button>
      </DialogContent>
    </Dialog>
  );
}
