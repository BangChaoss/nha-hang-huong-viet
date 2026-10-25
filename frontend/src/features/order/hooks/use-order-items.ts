import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { orderApi } from "../api/order-api";
import type {
  AddOrderItemRequest,
  OrderResponse,
  UpdateOrderItemRequest,
} from "../types/order.types";

/**
 * Thêm món vào đơn đang mở (STT 84).
 * Món hết hàng sẽ bị backend từ chối — thông báo lỗi được hiển thị nguyên văn.
 */
export function useAddOrderItem() {
  const queryClient = useQueryClient();

  return useMutation<OrderResponse, Error, { orderId: string; payload: AddOrderItemRequest }>({
    mutationFn: ({ orderId, payload }) => orderApi.addItem(orderId, payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["orders"] });
      toast.success("Đã thêm món vào đơn.");
    },
    onError: (error: Error) => {
      toast.error(error.message || "Không thể thêm món vào đơn. Vui lòng thử lại.");
    },
  });
}

/**
 * Sửa số lượng hoặc ghi chú của một món trong đơn đang mở (STT 84).
 * Món đã READY/SERVED/CANCELLED không được sửa (BR-39).
 */
export function useUpdateOrderItem() {
  const queryClient = useQueryClient();

  return useMutation<
    OrderResponse,
    Error,
    { orderId: string; itemId: string; payload: UpdateOrderItemRequest }
  >({
    mutationFn: ({ orderId, itemId, payload }) => orderApi.updateItem(orderId, itemId, payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["orders"] });
      toast.success("Đã cập nhật món trong đơn.");
    },
    onError: (error: Error) => {
      toast.error(error.message || "Không thể cập nhật món. Vui lòng thử lại.");
    },
  });
}

/**
 * Huỷ một món trong đơn đang mở (STT 85).
 * Bếp cũng cần nhận lại thay đổi nên cache phiếu bếp được invalidate.
 */
export function useCancelOrderItem() {
  const queryClient = useQueryClient();

  return useMutation<OrderResponse, Error, { orderId: string; itemId: string; reason: string }>({
    mutationFn: ({ orderId, itemId, reason }) => orderApi.cancelItem(orderId, itemId, reason),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["orders"] });
      void queryClient.invalidateQueries({ queryKey: ["kitchen-tickets"] });
      toast.success("Đã huỷ món trong đơn.");
    },
    onError: (error: Error) => {
      toast.error(error.message || "Không thể huỷ món. Vui lòng thử lại.");
    },
  });
}
