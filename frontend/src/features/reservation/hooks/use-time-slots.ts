import { useQuery } from "@tanstack/react-query";
import { reservationApi } from "../api/reservation-api";

export function useTimeSlots() {
  return useQuery<string[], Error>({
    queryKey: ["reservation-time-slots"],
    queryFn: () => reservationApi.getTimeSlots(),
    staleTime: 5 * 60 * 1000, // 5 minutes (slots rarely change)
  });
}
