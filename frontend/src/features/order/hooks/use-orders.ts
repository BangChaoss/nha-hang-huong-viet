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
 * Bàn chưa có đơn đang mở được chuẩn hoá thành null; lỗi 404 khác vẫn được ném ra.
 */
export function useActiveOrder(tableId: string | undefined) {
  return useQuery<OrderResponse | null, Error>({
    queryKey: ["orders", "active", tableId ?? ""],
    queryFn: () => orderApi.getActiveByTable(tableId as string),
    enabled: Boolean(tableId),
    retry: false,
    staleTime: 10_000,
  });
}
