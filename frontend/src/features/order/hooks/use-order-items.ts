import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { orderApi } from "../api/order-api";
import type {
  AddOrderItemRequest,
  AddOrderItemsBatchRequest,
  AdjustOrderItemsRequest,
  OrderResponse,
} from "../types/order.types";

type OrderItemsChange = { orderId: string } & (
  | { action: "ADD"; payload: AddOrderItemRequest }
  | { action: "ADD_BATCH"; payload: AddOrderItemsBatchRequest }
  | { action: "ADJUST"; payload: AdjustOrderItemsRequest }
);

export function useChangeOrderItems(tableId: string, seatedKey: readonly string[]) {
  const queryClient = useQueryClient();

  return useMutation({
    scope: { id: `pos-order-items:${tableId}` },
    mutationFn: ({ orderId, action, payload }: OrderItemsChange) =>
      action === "ADD"
        ? orderApi.addItem(orderId, payload)
        : action === "ADD_BATCH"
          ? orderApi.addItemsBatch(orderId, payload)
          : orderApi.adjustItems(orderId, payload),
    onSuccess: async (order) => {
      await queryClient.cancelQueries({ queryKey: seatedKey });
      queryClient.setQueryData<OrderResponse[]>(seatedKey, (current) =>
        current?.map((entry) => entry.id === order.id ? order : entry),
      );
      void queryClient.invalidateQueries({ queryKey: ["orders"] });
      void queryClient.invalidateQueries({ queryKey: ["kitchen-tickets"] });
    },
    onError: (error: Error) => {
      toast.error(error.message || "Không thể cập nhật món. Vui lòng thử lại.");
      void queryClient.invalidateQueries({ queryKey: seatedKey });
    },
  });
}
