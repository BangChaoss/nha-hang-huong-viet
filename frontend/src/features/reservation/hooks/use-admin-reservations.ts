import { useQuery } from "@tanstack/react-query";
import { reservationApi } from "../api/reservation-api";
import type {
  AdminReservationQueryParams,
  ReservationResponse,
  ReservationStatus,
} from "../types/reservation.types";

export interface UseAdminReservationsOptions {
  date?: string;
  scope?: "upcoming" | "pending" | "date";
  status?: ReservationStatus;
  refetchInterval?: number;
}

export function useAdminReservations(
  dateOrParams?: string | UseAdminReservationsOptions,
  legacyStatus?: ReservationStatus,
) {
  const params: AdminReservationQueryParams =
    typeof dateOrParams === "string"
      ? { date: dateOrParams, status: legacyStatus }
      : dateOrParams
        ? {
            date: dateOrParams.date,
            scope: dateOrParams.scope,
            status: dateOrParams.status,
          }
        : { scope: "upcoming" };

  const refetchInterval =
    typeof dateOrParams === "object" && dateOrParams?.refetchInterval !== undefined
      ? dateOrParams.refetchInterval
      : 20_000;

  return useQuery<ReservationResponse[], Error>({
    queryKey: ["admin-reservations", params.date, params.scope, params.status],
    queryFn: () => reservationApi.listAdminBookings(params),
    staleTime: 15_000,
    refetchInterval,
  });
}
