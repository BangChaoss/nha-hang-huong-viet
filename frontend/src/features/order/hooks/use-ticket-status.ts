import { useMutation, useQueryClient } from "@tanstack/react-query";
import { kitchenApi } from "../api/order-api";
import type { CookingStatus, KitchenTicketResponse } from "../types/order.types";

/**
 * Bếp chuyển trạng thái phiếu (BR-37, BR-39):
 * QUEUED → PREPARING → READY. Phiếu đã SERVED/CANCELLED không được đổi trạng thái.
 */
export function useUpdateTicketStatus() {
  const queryClient = useQueryClient();

  return useMutation<KitchenTicketResponse, Error, { id: string; status: CookingStatus }>({
    mutationFn: ({ id, status }: { id: string; status: CookingStatus }) =>
      kitchenApi.updateStatus(id, status),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["kitchen-tickets"] });
      void queryClient.invalidateQueries({ queryKey: ["orders"] });
    },
  });
}
