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
      void queryClient.invalidateQueries({ queryKey: ["tables"] });
      void queryClient.invalidateQueries({ queryKey: ["reservation-availability"] });
      void queryClient.invalidateQueries({ queryKey: ["staff-pos-reservations"] });
    },
  });
}