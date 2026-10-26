import { useEffect, useRef } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Client } from "@stomp/stompjs";
import { API_BASE } from "@/lib/table-api";
import { getSession } from "@/lib/auth-session";
import { kitchenApi } from "../api/order-api";
import type {
  CookingStatus,
  KitchenCompletedMessage,
  KitchenTicketResponse,
} from "../types/order.types";

// Polyfill Node.js `global` variable in the browser for libraries like sockjs-client
if (typeof window !== "undefined" && !(window as unknown as { global?: unknown }).global) {
  (window as unknown as { global?: unknown }).global = window;
}

type TopicHandler = {
  topic: string;
  onMessage: (body: string) => void;
};

/**
 * Đăng ký WebSocket STOMP tới các topic của bếp.
 * Lấy cảm hứng từ `src/lib/use-table-websocket.ts` (không sửa file đó).
 */
function useKitchenSocket(handlers: TopicHandler[], enabled = true) {
  const handlersRef = useRef(handlers);

  useEffect(() => {
    handlersRef.current = handlers;
  });

  useEffect(() => {
    if (!enabled || typeof window === "undefined") {
      return;
    }

    let isDisposed = false;
    let client: Client | null = null;

    const setupConnection = async () => {
      try {
        const token = getSession()?.token;
        const connectHeaders: Record<string, string> = token
          ? { Authorization: `Bearer ${token}` }
          : {};

        // Xây dựng URL WebSocket chuẩn (ws:// hoặc wss://)
        const wsProto = window.location.protocol === "https:" ? "wss:" : "ws:";
        const wsHost = API_BASE.replace(/^http(s)?:\/\//, "");
        const brokerURL = `${wsProto}//${wsHost}/ws`;

        // Tải động SockJS fallback an toàn cho trình duyệt
        let sockJsFactory: (() => WebSocket) | undefined = undefined;
        try {
          const SockJSModule = await import("sockjs-client");
          const SockJSClass = SockJSModule.default;
          sockJsFactory = () => new SockJSClass(`${API_BASE}/ws`) as unknown as WebSocket;
        } catch {
          // Nếu SockJS không tải được, client vẫn hoạt động qua native brokerURL
        }

        if (isDisposed) return;

        client = new Client({
          brokerURL,
          ...(sockJsFactory ? { webSocketFactory: sockJsFactory } : {}),
          connectHeaders,
          reconnectDelay: 5000,
          heartbeatIncoming: 10000,
          heartbeatOutgoing: 10000,
          onConnect: () => {
            if (isDisposed) return;
            for (const handler of handlersRef.current) {
              client?.subscribe(handler.topic, (message) => {
                handler.onMessage(message.body);
              });
            }
          },
        });

        client.activate();
      } catch {
        // Không kết nối được WebSocket thì vẫn dùng được refetchInterval.
      }
    };

    void setupConnection();

    return () => {
      isDisposed = true;
      if (client) {
        try {
          void client.deactivate();
        } catch {
          // Bỏ qua lỗi ngắt kết nối khi unmount
        }
      }
    };
  }, [enabled]);
}

/**
 * Danh sách phiếu bếp (UC-16).
 * Backend trả về theo thứ tự FIFO (BR-35) — không sắp xếp lại ở client.
 */
export function useKitchenTickets(status?: CookingStatus) {
  const queryClient = useQueryClient();

  const query = useQuery<KitchenTicketResponse[], Error>({
    queryKey: ["kitchen-tickets", status ?? "ALL"],
    queryFn: () => kitchenApi.listTickets(status),
    refetchInterval: 15000,
  });

  useKitchenSocket([
    {
      topic: "/topic/kitchen/tickets",
      onMessage: () => {
        void queryClient.invalidateQueries({ queryKey: ["kitchen-tickets"] });
      },
    },
  ]);

  return query;
}

/**
 * Đăng ký nghe topic `/topic/kitchen/completed` (STT 90).
 * Callback được gọi khi một món chuyển sang READY hoặc SERVED.
 */
export function useKitchenCompleted(
  onCompleted: (message: KitchenCompletedMessage) => void,
  enabled = true,
) {
  const onCompletedRef = useRef(onCompleted);

  useEffect(() => {
    onCompletedRef.current = onCompleted;
  });

  useKitchenSocket(
    [
      {
        topic: "/topic/kitchen/completed",
        onMessage: (body) => {
          try {
            onCompletedRef.current(JSON.parse(body) as KitchenCompletedMessage);
          } catch {
            // Bỏ qua nếu message payload lỗi định dạng
          }
        },
      },
    ],
    enabled,
  );
}
