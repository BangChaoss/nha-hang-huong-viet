import { useMutation, useQueryClient } from "@tanstack/react-query";
import { reservationApi } from "../api/reservation-api";

export function useCancelBooking() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, reason }: { id: string; reason?: string }) =>
      reservationApi.cancelBooking(id, reason),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["my-bookings"] });
      void queryClient.invalidateQueries({ queryKey: ["admin-reservations"] });
    },
  });
}
