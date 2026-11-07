import { useMutation, useQueryClient } from "@tanstack/react-query";
import { orderApi } from "../api/order-api";
import type { CreateOrderRequest, OrderResponse } from "../types/order.types";

/**
 * Tạo đơn từ danh sách món đã chọn (UC-15).
 * Khi thành công, làm mới dữ liệu đơn và phiếu bếp để đồng bộ các màn hình liên quan.
 */
export function useCreateOrder() {
  const queryClient = useQueryClient();

  return useMutation<OrderResponse, Error, CreateOrderRequest>({
    mutationFn: (payload: CreateOrderRequest) => orderApi.create(payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["orders"] });
      void queryClient.invalidateQueries({ queryKey: ["kitchen-tickets"] });
    },
  });
}
