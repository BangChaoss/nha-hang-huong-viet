import { useMutation, useQueryClient } from "@tanstack/react-query";
import { orderApi } from "../api/order-api";
import type { CreateOrderRequest, OrderResponse } from "../types/order.types";

/** Tạo đơn mới và gửi xuống bếp (UC-15). */
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
