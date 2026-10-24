import { useEffect, useMemo, useRef, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { Client } from "@stomp/stompjs";
import { Loader2 } from "lucide-react";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Button } from "@/components/ui/button";
import { formatVND } from "@/lib/restaurant-store";
import { cn } from "@/lib/utils";
import { getSession } from "@/lib/auth-session";
import { API_BASE } from "@/lib/table-api";
import { resolveImageUrl } from "@/lib/image-url";
import { BookForm } from "@/features/reservation/components/book-form";
import { BookingHistoryList } from "@/features/reservation/components/booking-history-list";
import { useMenu } from "@/features/menu/hooks/use-menu";
import { MenuDetailDialog } from "@/features/menu/components/menu-detail-dialog";
import type {
  MenuAvailabilityMessage,
  MenuItemResponse,
  MenuOverviewResponse,
} from "@/features/menu/api/menu-api";

const ALL_CATEGORIES = "all";

export function CustomerPortal() {
  const [tab, setTab] = useState("menu");
  return (
    <Tabs value={tab} onValueChange={setTab} className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="text-xs uppercase tracking-[0.2em] text-muted-foreground">
            Kính chào quý khách
          </p>
          <h1 className="text-4xl font-semibold">Ẩm thực Cố Đô</h1>
        </div>
        <TabsList>
          <TabsTrigger value="menu">Thực Đơn</TabsTrigger>
          <TabsTrigger value="book">Đặt Bàn Trước</TabsTrigger>
          <TabsTrigger value="history">Lịch Sử Đặt Bàn</TabsTrigger>
        </TabsList>
      </div>
      <TabsContent value="menu">
        <Menu />
      </TabsContent>
      <TabsContent value="book">
        <BookForm onDone={() => setTab("history")} />
      </TabsContent>
      <TabsContent value="history">
        <BookingHistoryList />
      </TabsContent>
    </Tabs>
  );
}

function Menu() {
  const { categories, items, isLoading, isError, error, refetch } = useMenu();
  const [cat, setCat] = useState<string>(ALL_CATEGORIES);
  const [selected, setSelected] = useState<MenuItemResponse | null>(null);

  useMenuAvailabilitySubscription();

  const list = useMemo(
    () => (cat === ALL_CATEGORIES ? items : items.filter((item) => item.categoryId === cat)),
    [cat, items],
  );

  const categoryOptions = useMemo(
    () =>
      categories.map((category) => ({
        id: category.id,
        name: category.categoryName,
      })),
    [categories],
  );

  return (
    <div className="space-y-6">
      {categoryOptions.length > 0 && (
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            onClick={() => setCat(ALL_CATEGORIES)}
            className={cn(
              "rounded-full border px-4 py-1.5 text-sm transition",
              cat === ALL_CATEGORIES
                ? "border-moss bg-moss text-moss-foreground"
                : "bg-card hover:border-moss/40",
            )}
          >
            Tất cả
          </button>
          {categoryOptions.map((category) => (
            <button
              key={category.id}
              type="button"
              onClick={() => setCat(category.id)}
              className={cn(
                "rounded-full border px-4 py-1.5 text-sm transition",
                cat === category.id
                  ? "border-moss bg-moss text-moss-foreground"
                  : "bg-card hover:border-moss/40",
              )}
            >
              {category.name}
            </button>
          ))}
        </div>
      )}

      {isLoading ? (
        <div className="flex items-center justify-center gap-2 py-16 text-sm text-muted-foreground">
          <Loader2 className="h-4 w-4 animate-spin" />
          Đang tải thực đơn…
        </div>
      ) : isError ? (
        <div className="surface-card flex flex-col items-center gap-3 p-10 text-center">
          <p className="text-sm font-medium text-alert">
            {error?.message ?? "Không thể tải thực đơn. Vui lòng thử lại."}
          </p>
          <Button type="button" variant="outline" onClick={() => void refetch()}>
            Thử lại
          </Button>
        </div>
      ) : list.length === 0 ? (
        <div className="surface-card p-10 text-center text-sm text-muted-foreground">
          Chưa có món ăn nào.
        </div>
      ) : (
        <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {list.map((d) => (
            <article key={d.id} className="surface-card overflow-hidden">
              <div className="relative aspect-[4/3] bg-muted">
                {d.imageUrl ? (
                  <img
                    src={resolveImageUrl(d.imageUrl) ?? undefined}
                    alt={d.itemName}
                    loading="lazy"
                    className={cn("h-full w-full object-cover", !d.isAvailable && "grayscale")}
                  />
                ) : (
                  <div className="flex h-full w-full items-center justify-center text-sm text-muted-foreground">
                    Chưa có hình ảnh
                  </div>
                )}
                {!d.isAvailable && (
                  <span className="status-cancelled absolute left-3 top-3 rounded-full px-2.5 py-0.5 text-xs font-medium">
                    Hết món
                  </span>
                )}
              </div>
              <div className="p-5">
                <p className="text-xs uppercase tracking-widest text-muted-foreground">
                  {d.categoryName}
                </p>
                <div className="mt-1 flex items-baseline justify-between gap-3">
                  <h3 className="text-2xl font-semibold leading-tight">{d.itemName}</h3>
                  <span className="shrink-0 font-medium tabular-nums text-primary">
                    {formatVND(d.currentPrice)}
                  </span>
                </div>
                {d.description && (
                  <p className="mt-2 line-clamp-2 text-sm text-muted-foreground">{d.description}</p>
                )}
                <button
                  type="button"
                  onClick={() => setSelected(d)}
                  className="mt-4 text-sm font-medium text-primary underline-offset-4 hover:underline"
                >
                  Xem chi tiết
                </button>
              </div>
            </article>
          ))}
        </div>
      )}

      <MenuDetailDialog
        item={selected}
        open={selected !== null}
        onOpenChange={(open) => {
          if (!open) setSelected(null);
        }}
      />
    </div>
  );
}

/**
 * BR-30 / BR-50: cập nhật tình trạng còn món ngay trong cache React Query
 * khi nhận được thông báo WebSocket từ nhà hàng.
 */
function useMenuAvailabilitySubscription() {
  const queryClient = useQueryClient();
  const clientRef = useRef<Client | null>(null);

  useEffect(() => {
    if (typeof window === "undefined") {
      return;
    }

    let isDisposed = false;

    const setupConnection = async () => {
      try {
        const token = getSession()?.token;
        const connectHeaders: Record<string, string> = token
          ? { Authorization: `Bearer ${token}` }
          : {};

        const wsProto = window.location.protocol === "https:" ? "wss:" : "ws:";
        const wsHost = API_BASE.replace(/^http(s)?:\/\//, "");
        const brokerURL = `${wsProto}//${wsHost}/ws`;

        let sockJsFactory: (() => WebSocket) | undefined = undefined;
        try {
          const SockJSModule = await import("sockjs-client");
          const SockJSClass = SockJSModule.default;
          sockJsFactory = () => new SockJSClass(`${API_BASE}/ws`) as unknown as WebSocket;
        } catch {
          // Nếu SockJS không tải được, client vẫn hoạt động qua native brokerURL
        }

        if (isDisposed) return;

        const client = new Client({
          brokerURL,
          ...(sockJsFactory ? { webSocketFactory: sockJsFactory } : {}),
          connectHeaders,
          reconnectDelay: 5000,
          heartbeatIncoming: 10000,
          heartbeatOutgoing: 10000,
          onConnect: () => {
            if (isDisposed) return;
            client.subscribe("/topic/menu/availability", (message) => {
              try {
                const payload = JSON.parse(message.body) as MenuAvailabilityMessage;
                if (!payload?.menuItemId) return;
                queryClient.setQueryData<MenuOverviewResponse>(["menu"], (previous) => {
                  if (!previous) return previous;
                  return {
                    ...previous,
                    categories: previous.categories.map((category) => ({
                      ...category,
                      items: (category.items ?? []).map((item) =>
                        item.id === payload.menuItemId
                          ? { ...item, isAvailable: payload.isAvailable }
                          : item,
                      ),
                    })),
                  };
                });
              } catch {
                // Bỏ qua nếu message payload lỗi định dạng
              }
            });
          },
        });

        clientRef.current = client;
        client.activate();
      } catch {
        // Không kết nối được WebSocket thì thực đơn vẫn hiển thị từ REST API
      }
    };

    void setupConnection();

    return () => {
      isDisposed = true;
      const client = clientRef.current;
      clientRef.current = null;
      if (client) {
        try {
          void client.deactivate();
        } catch {
          // Bỏ qua lỗi ngắt kết nối khi unmount
        }
      }
    };
  }, [queryClient]);
}
