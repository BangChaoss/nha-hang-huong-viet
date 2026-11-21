import { useEffect, useState } from "react";
import { AlertTriangle, Calendar, Clock, RefreshCw } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";
import { useKitchenTickets } from "@/features/order/hooks/use-kitchen-tickets";
import { useUpdateTicketStatus } from "@/features/order/hooks/use-ticket-status";
import { isUserManager, useSession } from "@/lib/auth-session";
import type { CookingStatus, KitchenTicketResponse } from "@/features/order/types/order.types";
import { orderCode } from "@/features/order/utils/order-code";

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

function getTodayString(): string {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

type PositionedTicket = { ticket: KitchenTicketResponse; position: number };

function groupTickets(list: KitchenTicketResponse[]) {
  const tables = new Map<string, Map<string, PositionedTicket[]>>();
  list.forEach((ticket, index) => {
    const orders = tables.get(ticket.tableNumber) ?? new Map<string, PositionedTicket[]>();
    const orderId = ticket.orderId || ticket.id;
    const items = orders.get(orderId) ?? [];
    items.push({ ticket, position: index + 1 });
    orders.set(orderId, items);
    tables.set(ticket.tableNumber, orders);
  });
  return tables;
}

export function KitchenKDS({ readOnly = false }: { readOnly?: boolean }) {
  const [selectedDate, setSelectedDate] = useState<string>(getTodayString);
  const { data: tickets, isLoading, isError, error, refetch, isFetching } = useKitchenTickets(
    undefined,
    selectedDate
  );
  const updateStatus = useUpdateTicketStatus();
  const { session } = useSession();
  const [now, setNow] = useState(Date.now());

  // Đầu bếp chỉ theo dõi phiếu: nhân viên điều phối (STAFF), quản lý và quản trị viên được đổi trạng thái.
  const canUpdateStatus =
    !readOnly && (session?.role === "STAFF" || isUserManager(session?.role));

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

  const isToday = selectedDate === getTodayString();

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-end justify-between gap-4">
        <div>
          <h1 className="text-4xl font-semibold">Phiếu chế biến</h1>
          <p className="mt-1 text-sm text-muted-foreground">Gom theo bàn và đơn. Số lượt cho biết thứ tự gửi bếp của tất cả món đang chờ.</p>
          {!canUpdateStatus && (
            <p className="mt-2 rounded-md border border-muted-foreground/20 bg-muted/60 px-3 py-1.5 text-xs text-muted-foreground">
              Chế độ chỉ xem. Nhân viên phục vụ hoặc quản lý cập nhật trạng thái món.
            </p>
          )}
        </div>

        {/* Bộ điều khiển chọn ngày hiển thị */}
        <div className="flex flex-wrap items-center gap-2">
          <div className="flex items-center gap-2 rounded-lg border bg-card px-3 py-1 text-sm shadow-xs">
            <Calendar className="h-4 w-4 text-muted-foreground shrink-0" />
            <Input
              type="date"
              value={selectedDate}
              onChange={(e) => setSelectedDate(e.target.value)}
              className="h-11 w-36 border-none bg-transparent p-1 text-sm font-medium"
              aria-label="Chọn ngày hiển thị phiếu bếp"
            />
          </div>
          {!isToday && (
            <Button
              type="button"
              variant="outline"
              size="sm"
              className="min-h-11 text-xs"
              onClick={() => setSelectedDate(getTodayString())}
            >
              Hôm nay
            </Button>
          )}
          <Button
            type="button"
            variant="outline"
            size="sm"
            className="min-h-11 text-xs"
            onClick={() => void refetch()}
            disabled={isFetching}
          >
            <RefreshCw className="mr-1 h-3.5 w-3.5" aria-hidden="true" />
            {isFetching ? "Đang tải…" : "Làm mới"}
          </Button>
        </div>
      </div>

      {isError && (
        <p role="alert" className="rounded-md border border-alert/30 bg-alert/5 px-3 py-2 text-sm text-alert">
          {error instanceof Error ? error.message : "Không tải được phiếu bếp."} Chọn Làm mới để thử lại.
        </p>
      )}

      {isLoading ? (
        <p role="status" className="py-8 text-center text-sm text-muted-foreground">Đang tải phiếu bếp…</p>
      ) : (!isError || tickets) && (
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
                    <p className="py-8 text-center text-sm text-muted-foreground">
                      Không có phiếu nào
                    </p>
                  )}
                  {Array.from(groupTickets(list), ([tableNumber, orders]) => (
                    <section key={tableNumber} aria-label={tableNumber} className="surface-card min-w-0 p-3 sm:p-4">
                      <h3 className="wrap-anywhere font-display text-xl font-semibold">{tableNumber}</h3>
                      {Array.from(orders, ([id, items]) => (
                        <div key={id} className="mt-3 border-t pt-2">
                          <h4 className="text-sm font-semibold">
                            {items[0].ticket.orderId ? `Đơn ${orderCode(id)}` : "Phiếu không có mã đơn"}
                          </h4>
                          <ul className="divide-y">
                            {items.map(({ ticket: t, position }) => {
                              const mins = elapsedMinutes(t.createdAt, now);
                              const waiting = c.s === "QUEUED";
                              const next = waiting && position === 1;
                              const action = waiting ? "Bắt đầu nấu" : "Báo món hoàn tất";
                              return (
                                <li key={t.id} className="space-y-2 py-2">
                                  <div className="flex flex-wrap items-baseline justify-between gap-x-2 gap-y-1 text-xs tabular-nums">
                                    {waiting && (
                                      <span className={cn("font-medium", next ? "text-primary" : "text-muted-foreground")}>
                                        Lượt {position}{next && " · Tiếp theo"}
                                      </span>
                                    )}
                                    <span className={cn("flex flex-wrap items-center gap-1", mins > 15 ? "font-semibold text-alert" : "text-muted-foreground")}>
                                      <Clock className="size-3" aria-hidden="true" />
                                      <time dateTime={t.createdAt}>{new Date(t.createdAt).toLocaleTimeString("vi-VN", {
                                        hour: "2-digit", minute: "2-digit",
                                      })}</time>
                                      · {mins} phút{mins > 15 && " · Chờ lâu"}
                                    </span>
                                  </div>
                                  <p className="wrap-anywhere text-sm"><strong>{t.quantity}×</strong> {t.itemName}</p>
                                  {t.additional && <p className="text-xs font-medium text-muted-foreground">Gọi thêm</p>}
                                  {t.note && (
                                    <p className="wrap-anywhere flex items-start gap-1.5 rounded-md border border-alert/30 bg-alert/5 px-2 py-1.5 text-xs text-alert">
                                      <AlertTriangle aria-hidden="true" className="mt-0.5 size-3.5 shrink-0" /> {t.note}
                                    </p>
                                  )}
                                  {canUpdateStatus && (waiting || c.s === "PREPARING") && (
                                    <Button
                                      variant={next ? "default" : "outline"}
                                      className="min-h-11 w-full"
                                      aria-label={`${action}: ${t.itemName}, ${tableNumber}${t.orderId ? `, đơn ${orderCode(t.orderId)}` : ""}`}
                                      disabled={updateStatus.isPending}
                                      onClick={() => advance(t, waiting ? "PREPARING" : "READY")}
                                    >
                                      {action}
                                    </Button>
                                  )}
                                </li>
                              );
                            })}
                          </ul>
                        </div>
                      ))}
                    </section>
                  ))}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}