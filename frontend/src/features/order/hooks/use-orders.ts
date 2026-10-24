import { useQuery } from "@tanstack/react-query";
import { orderApi } from "../api/order-api";
import type { OrderResponse, OrderStatus } from "../types/order.types";

/** Danh sách đơn, có thể lọc theo bàn hoặc trạng thái. */
export function useOrders(params?: { tableId?: string; status?: OrderStatus }) {
  return useQuery<OrderResponse[], Error>({
    queryKey: ["orders", params?.tableId ?? "ALL", params?.status ?? "ALL"],
    queryFn: () => orderApi.list(params),
    staleTime: 15_000,
  });
}

/**
 * Đơn đang mở của một bàn.
 * `retry: false` vì bàn chưa có đơn sẽ trả về 404 — không cần thử lại.
 */
export function useActiveOrder(tableId: string | undefined) {
  return useQuery<OrderResponse, Error>({
    queryKey: ["orders", "active", tableId ?? ""],
    queryFn: () => orderApi.getActiveByTable(tableId as string),
    enabled: Boolean(tableId),
    retry: false,
    staleTime: 10_000,
  });
}
