import { useQuery } from "@tanstack/react-query";
import { reservationApi } from "../api/reservation-api";
import type { ZoneOption } from "../types/reservation.types";

export function useZones() {
  return useQuery<ZoneOption[], Error>({
    queryKey: ["reservation-zones"],
    queryFn: () => reservationApi.getZones(),
    staleTime: 5 * 60 * 1000, // 5 minutes
  });
}
