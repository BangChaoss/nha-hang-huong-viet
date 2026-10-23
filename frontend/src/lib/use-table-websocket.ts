import { useEffect, useRef, useState } from "react";
import { Client } from "@stomp/stompjs";
import { getSession } from "@/lib/auth-session";
import { API_BASE, type FloorPlanLayoutResponse, type TableStatusMessage } from "@/lib/table-api";

// Polyfill Node.js `global` variable in the browser for libraries like sockjs-client
if (typeof window !== "undefined" && !(window as unknown as { global?: unknown }).global) {
  (window as unknown as { global?: unknown }).global = window;
}

export type UseTableWebSocketOptions = {
  onTableStatusChange?: (message: TableStatusMessage) => void;
  onFloorPlanChange?: (layouts: FloorPlanLayoutResponse[]) => void;
  enabled?: boolean;
};

export function useTableWebSocket(options: UseTableWebSocketOptions = {}) {
  const { onTableStatusChange, onFloorPlanChange, enabled = true } = options;
  const [isConnected, setIsConnected] = useState(false);

  // Giữ ref callback mới nhất để tránh reconnect không cần thiết khi callback thay đổi
  const onTableStatusRef = useRef(onTableStatusChange);
  const onFloorPlanRef = useRef(onFloorPlanChange);

  useEffect(() => {
    onTableStatusRef.current = onTableStatusChange;
  }, [onTableStatusChange]);

  useEffect(() => {
    onFloorPlanRef.current = onFloorPlanChange;
  }, [onFloorPlanChange]);

  useEffect(() => {
    if (!enabled || typeof window === "undefined") {
      setIsConnected(false);
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
            setIsConnected(true);

            client?.subscribe("/topic/table-status", (message) => {
              try {
                const data = JSON.parse(message.body) as TableStatusMessage;
                onTableStatusRef.current?.(data);
              } catch {
                // Bỏ qua nếu message payload lỗi định dạng
              }
            });

            client?.subscribe("/topic/floor-plan", (message) => {
              try {
                const data = JSON.parse(message.body) as FloorPlanLayoutResponse[];
                onFloorPlanRef.current?.(data);
              } catch {
                // Bỏ qua nếu message payload lỗi định dạng
              }
            });
          },
          onDisconnect: () => {
            if (!isDisposed) setIsConnected(false);
          },
          onStompError: () => {
            if (!isDisposed) setIsConnected(false);
          },
          onWebSocketClose: () => {
            if (!isDisposed) setIsConnected(false);
          },
          onWebSocketError: () => {
            if (!isDisposed) setIsConnected(false);
          },
        });

        client.activate();
      } catch {
        if (!isDisposed) setIsConnected(false);
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
      setIsConnected(false);
    };
  }, [enabled]);

  return { isConnected };
}
