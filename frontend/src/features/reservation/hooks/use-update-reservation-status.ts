import { useMutation, useQueryClient } from "@tanstack/react-query";
import { reservationApi } from "../api/reservation-api";
import type { UpdateReservationStatusRequest } from "../types/reservation.types";

export function useUpdateReservationStatus() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: UpdateReservationStatusRequest }) =>
      reservationApi.updateReservationStatus(id, payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["admin-reservations"] });
      void queryClient.invalidateQueries({ queryKey: ["my-bookings"] });
    },
  });
}
