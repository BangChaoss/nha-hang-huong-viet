import { useMutation, useQueryClient } from "@tanstack/react-query";
import { reservationApi } from "../api/reservation-api";

export function useCancelBooking() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, reason, phone }: { id: string; reason?: string; phone?: string }) =>
      reservationApi.cancelBooking(id, reason, phone),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["my-bookings"] });
      void queryClient.invalidateQueries({ queryKey: ["admin-reservations"] });
    },
  });
}
