import { useMutation, useQuery } from "@tanstack/react-query";
import { useSession } from "@/lib/auth-session";
import { reservationApi } from "../api/reservation-api";
import type { ReservationResponse } from "../types/reservation.types";

export function useBookingHistory() {
  const { session, ready } = useSession();
  const isAuthenticated = ready && Boolean(session?.token);

  return useQuery<ReservationResponse[], Error>({
    queryKey: ["my-bookings", session?.id ?? "guest"],
    queryFn: () => reservationApi.getMyBookings(),
    enabled: isAuthenticated,
    staleTime: 60 * 1000,
  });
}

export function useGuestLookup() {
  return useMutation<ReservationResponse[], Error, { phone: string; code: string }>({
    mutationFn: ({ phone, code }: { phone: string; code: string }) =>
      reservationApi.guestLookup(phone, code),
  });
}
