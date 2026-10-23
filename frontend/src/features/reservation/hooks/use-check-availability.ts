import { useQuery } from "@tanstack/react-query";
import { reservationApi } from "../api/reservation-api";
import type { ZoneAvailabilityResponse } from "../types/reservation.types";

export function useCheckAvailability(date: string, partySize?: number) {
  return useQuery<ZoneAvailabilityResponse[], Error>({
    queryKey: ["reservation-availability", date, partySize],
    queryFn: () => reservationApi.checkAvailability(date, partySize),
    enabled: Boolean(date && date.length === 10),
    staleTime: 30 * 1000, // 30 seconds
  });
}
