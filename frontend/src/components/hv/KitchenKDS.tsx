import { useEffect, useState } from "react";
import { AlertTriangle, Clock } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { useKitchenTickets } from "@/features/order/hooks/use-kitchen-tickets";
import { useUpdateTicketStatus } from "@/features/order/hooks/use-ticket-status";
import { isUserManager, useSession } from "@/lib/auth-session";
import type { CookingStatus, KitchenTicketResponse } from "@/features/order/types/order.types";
import { BackButton } from "@/components/hv/BackButton";

const cols: { s: CookingStatus; title: string }[] = [
  { s: "QUEUED", title: "Chờ Chế Biến" },
  { s: "PREPARING", title: "Đang Nấu" },
  { s: "READY", title: "Đã Xong - Chờ Phục Vụ" },
];

/** Số phút đã trôi qua kể từ khi phiếu được tạo. */
function elapsedMinutes(createdAt: string, now: number) {
  const start = Date.parse(createdAt);
  if (Number.isNaN(start)) return 0;
  return Math.max(0, Math.floor((now - start) / 60000));
}

export function KitchenKDS({ readOnly = false }: { readOnly?: boolean }) {
  const { data: tickets, isLoading, isError, error } = useKitchenTickets();
  const updateStatus = useUpdateTicketStatus();
  const { session } = useSession();
  const [now, setNow] = useState(Date.now());

  // Đầu bếp chỉ theo dõi phiếu: chỉ quản lý / quản trị viên mới được đổi trạng thái.
  const canUpdateStatus = !readOnly && isUserManager(session?.role);

  useEffect(() => {
    const i = setInterval(() => setNow(Date.now()), 30000);
    return () => clearInterval(i);
  }, []);

  const advance = (ticket: KitchenTicketResponse, next: CookingStatus) => {
    updateStatus.mutate(
      { id: ticket.id, status: next },
      {
        onSuccess: () => {
          toast.success(
            next === "PREPARING"
              ? `Bắt đầu nấu ${ticket.tableNumber}`
              : `${ticket.tableNumber}: món đã sẵn sàng phục vụ`,
          );
        },
        onError: (err: Error) => {
          toast.error(err.message || "Không cập nhật được trạng thái phiếu bếp.");
        },
      },
    );
  };

  return (
    <div className="space-y-6">
      <div className="space-y-4">
        <div>
          <BackButton />
        </div>
        <div>
          <p className="text-xs uppercase tracking-[0.2em] text-muted-foreground">
            Màn hình bếp · FIFO
          </p>
          <h1 className="text-4xl font-semibold">Phiếu chế biến</h1>
          {!canUpdateStatus && (
            <p className="mt-3 rounded-md border border-muted-foreground/20 bg-muted/60 px-3 py-2 text-sm text-muted-foreground">
              Chế độ chỉ xem — đầu bếp theo dõi phiếu tại đây. Việc cập nhật trạng thái món do quản lý
              hoặc quản trị viên thực hiện.
            </p>
          )}
        </div>
      </div>
      {isError && (
        <p className="rounded-md border border-alert/30 bg-alert/5 px-3 py-2 text-sm text-alert">
          {error instanceof Error ? error.message : "Không tải được phiếu bếp."}
        </p>
      )}
      {isLoading ? (
        <p className="py-8 text-center text-sm text-muted-foreground">Đang tải phiếu bếp…</p>
      ) : (
        <div className="grid gap-5 lg:grid-cols-3">
          {cols.map((c) => {
            // Backend đã trả về theo thứ tự FIFO (BR-35) — giữ nguyên thứ tự.
            const list = (tickets ?? []).filter((t) => t.status === c.s);
            return (
              <div key={c.s} className="rounded-xl bg-muted/60 p-4">
                <div className="mb-4 flex items-center justify-between">
                  <h2 className="text-2xl font-medium text-moss">{c.title}</h2>
                  <span className="rounded-full bg-card px-2.5 py-0.5 text-xs font-medium tabular-nums">
                    {list.length}
                  </span>
                </div>
                <div className="space-y-3">
                  {list.length === 0 && (
                    <p className="py-8 text-center text-sm text-muted-foreground">Trống</p>
                  )}
                  {list.map((t, idx) => {
                    const mins = elapsedMinutes(t.createdAt, now);
                    const isOverdue = mins > 15;
                    return (
                      <div
                        key={t.id}
                        className={cn(
                          "surface-card p-4",
                          idx === 0 && c.s !== "READY" && "border-primary/50",
                        )}
                      >
                        <div className="flex items-center justify-between">
                          <span className="font-display text-2xl font-semibold">
                            {t.tableNumber}
                          </span>
                          <span
                            className={cn(
                              "flex items-center gap-1 text-xs tabular-nums",
                              isOverdue ? "font-semibold text-alert" : "text-muted-foreground",
                            )}
                          >
                            <Clock className="h-3 w-3" />
                            {new Date(t.createdAt).toLocaleTimeString("vi-VN", {
                              hour: "2-digit",
                              minute: "2-digit",
                            })}{" "}
                            · {mins}′{isOverdue && " ⚠"}
                          </span>
                        </div>
                        <ul className="mt-3 space-y-1 text-sm">
                          <li className="flex gap-2">
                            <span className="w-6 font-semibold tabular-nums">{t.quantity}×</span>
                            {t.itemName}
                          </li>
                        </ul>
                        {t.note && (
                          <p className="mt-3 flex items-start gap-1.5 rounded-md border border-alert/30 bg-alert/5 px-2 py-1.5 text-xs font-bold uppercase text-alert">
                            <AlertTriangle className="mt-0.5 h-3.5 w-3.5 shrink-0" /> {t.note}
                          </p>
                        )}
                        {canUpdateStatus && (c.s === "QUEUED" || c.s === "PREPARING") && (
                          <Button
                            size="sm"
                            className={cn(
                              "mt-4 w-full",
                              c.s === "QUEUED"
                                ? "bg-primary hover:bg-primary-hover"
                                : "bg-moss text-moss-foreground hover:bg-moss/90",
                            )}
                            disabled={updateStatus.isPending}
                            onClick={() => advance(t, c.s === "QUEUED" ? "PREPARING" : "READY")}
                          >
                            {c.s === "QUEUED" ? "Bắt đầu nấu" : "Báo món hoàn tất"}
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
      )}
    </div>
  );
}
