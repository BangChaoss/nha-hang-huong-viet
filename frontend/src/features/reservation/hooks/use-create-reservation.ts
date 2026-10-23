import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { reservationApi } from "../api/reservation-api";
import type { CreateReservationRequest, ReservationResponse } from "../types/reservation.types";

export function useCreateReservation() {
  const queryClient = useQueryClient();

  return useMutation<ReservationResponse, Error, CreateReservationRequest>({
    mutationFn: (payload: CreateReservationRequest) => reservationApi.create(payload),
    onSuccess: (data) => {
      // Invalidate relevant queries
      void queryClient.invalidateQueries({ queryKey: ["my-bookings"] });
      void queryClient.invalidateQueries({ queryKey: ["reservation-availability"] });
      toast.success("Đặt bàn thành công! Đang chờ nhà hàng xác nhận.");
    },
    onError: (error: Error) => {
      toast.error(error.message || "Không thể gửi yêu cầu đặt bàn. Vui lòng thử lại.");
    },
  });
}
