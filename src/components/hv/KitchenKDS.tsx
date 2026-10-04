import { useEffect, useState } from "react";
import { AlertTriangle, Clock } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { useRestaurant, type TicketStatus } from "@/lib/restaurant-store";
import { cn } from "@/lib/utils";

const cols: { s: TicketStatus; title: string }[] = [
  { s: "queued", title: "Chờ Chế Biến" },
  { s: "preparing", title: "Đang Nấu" },
  { s: "ready", title: "Đã Xong - Chờ Phục Vụ" },
];

export function KitchenKDS() {
  const { tickets, dish, advanceTicket } = useRestaurant();
  const [now, setNow] = useState(Date.now());
  useEffect(() => { const i = setInterval(() => setNow(Date.now()), 30000); return () => clearInterval(i); }, []);

  return (
    <div className="space-y-6">
      <div>
        <p className="text-xs uppercase tracking-[0.2em] text-muted-foreground">Màn hình bếp · FIFO</p>
        <h1 className="text-4xl font-semibold">Phiếu chế biến</h1>
      </div>
      <div className="grid gap-5 lg:grid-cols-3">
        {cols.map((c) => {
          const list = tickets.filter((t) => t.status === c.s).sort((a, b) => a.createdAt - b.createdAt);
          return (
            <div key={c.s} className="rounded-xl bg-muted/60 p-4">
              <div className="mb-4 flex items-center justify-between">
                <h2 className="text-2xl font-medium text-moss">{c.title}</h2>
                <span className="rounded-full bg-card px-2.5 py-0.5 text-xs font-medium tabular-nums">{list.length}</span>
              </div>
              <div className="space-y-3">
                {list.length === 0 && <p className="py-8 text-center text-sm text-muted-foreground">Trống</p>}
                {list.map((t, idx) => {
                  const mins = Math.floor((now - t.createdAt) / 60000);
                  return (
                    <div key={t.id} className={cn("surface-card p-4", idx === 0 && c.s !== "ready" && "border-primary/50")}>
                      <div className="flex items-center justify-between">
                        <span className="font-display text-2xl font-semibold">{t.table}</span>
                        <span className={cn("flex items-center gap-1 text-xs tabular-nums", mins > 15 ? "font-semibold text-alert" : "text-muted-foreground")}>
                          <Clock className="h-3 w-3" />
                          {new Date(t.createdAt).toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" })} · {mins}′
                        </span>
                      </div>
                      <ul className="mt-3 space-y-1 text-sm">
                        {t.items.map((i) => (
                          <li key={i.dishId} className="flex gap-2"><span className="w-6 font-semibold tabular-nums">{i.qty}×</span>{dish(i.dishId)?.name}</li>
                        ))}
                      </ul>
                      {t.note && (
                        <p className="mt-3 flex items-start gap-1.5 rounded-md border border-alert/30 bg-alert/5 px-2 py-1.5 text-xs font-bold uppercase text-alert">
                          <AlertTriangle className="mt-0.5 h-3.5 w-3.5 shrink-0" /> {t.note}
                        </p>
                      )}
                      {c.s !== "ready" && (
                        <Button size="sm" className={cn("mt-4 w-full", c.s === "queued" ? "bg-primary hover:bg-primary-hover" : "bg-moss text-moss-foreground hover:bg-moss/90")}
                          onClick={() => { advanceTicket(t.id); toast.success(c.s === "queued" ? `Bắt đầu nấu ${t.table}` : `${t.table}: món đã sẵn sàng phục vụ`); }}>
                          {c.s === "queued" ? "Bắt đầu nấu" : "Báo món hoàn tất"}
                        </Button>
                      )}
                    </div>
                  );
                })}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
