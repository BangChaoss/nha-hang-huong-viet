import { useQuery } from "@tanstack/react-query";
import { reservationApi } from "../api/reservation-api";
import type { ReservationResponse, ReservationStatus } from "../types/reservation.types";

export function useAdminReservations(date: string, status?: ReservationStatus) {
  return useQuery<ReservationResponse[], Error>({
    queryKey: ["admin-reservations", date, status],
    queryFn: () => reservationApi.listByDate(date, status),
    staleTime: 30_000,
    refetchInterval: 60_000,
  });
}
