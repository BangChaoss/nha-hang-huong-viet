import { API_BASE } from "@/lib/table-api";
import { getSession } from "@/lib/auth-session";
import type {
  AvailableTable,
  CreateReservationRequest,
  ReservationResponse,
  ReservationStatus,
  UpdateReservationStatusRequest,
  ZoneAvailabilityResponse,
  ZoneOption,
} from "../types/reservation.types";

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const token = getSession()?.token;
  const response = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(init?.headers ?? {}),
    },
  });

  if (!response.ok) {
    let message = `Yêu cầu thất bại (${response.status})`;
    try {
      const body = (await response.json()) as { message?: string; detail?: string } | null;
      if (body) {
        message = body.message ?? body.detail ?? message;
      }
    } catch {
      // Fallback khi body không phải JSON
    }
    throw new Error(message);
  }

  return response.json() as Promise<T>;
}

export const reservationApi = {
  create: (payload: CreateReservationRequest): Promise<ReservationResponse> =>
    request<ReservationResponse>("/api/reservations", {
      method: "POST",
      body: JSON.stringify(payload),
    }),

  checkAvailability: (date: string, partySize?: number): Promise<ZoneAvailabilityResponse[]> => {
    const params = new URLSearchParams({ date });
    if (partySize !== undefined && partySize !== null) {
      params.append("partySize", partySize.toString());
    }
    return request<ZoneAvailabilityResponse[]>(
      `/api/reservations/availability?${params.toString()}`,
    );
  },

  getTimeSlots: (): Promise<string[]> => request<string[]>("/api/reservations/time-slots"),

  getZones: (): Promise<ZoneOption[]> => request<ZoneOption[]>("/api/reservations/zones"),

  getMyBookings: (): Promise<ReservationResponse[]> =>
    request<ReservationResponse[]>("/api/reservations/my-bookings"),

  guestLookup: (phone: string, code?: string): Promise<ReservationResponse[]> => {
    const params = new URLSearchParams({
      phone: phone.trim(),
      ...(code?.trim() ? { code: code.trim() } : {}),
    });
    return request<ReservationResponse[]>(
      `/api/reservations/guest-lookup?${params.toString()}`,
    );
  },

  // === UC12: Customer cancel API ===
  cancelBooking: (id: string, reason?: string, phone?: string): Promise<ReservationResponse> =>
    request<ReservationResponse>(`/api/reservations/${id}/cancel`, {
      method: "POST",
      body: JSON.stringify({ reason, phone: phone?.trim() }),
    }),

  // === UC09, UC10, UC13: Staff/Admin APIs ===
  listByDate: (date: string, status?: ReservationStatus): Promise<ReservationResponse[]> => {
    const params = new URLSearchParams({ date });
    if (status) {
      params.append("status", status);
    }
    return request<ReservationResponse[]>(`/api/admin/reservations?${params.toString()}`);
  },

  getById: (id: string): Promise<ReservationResponse> =>
    request<ReservationResponse>(`/api/admin/reservations/${id}`),

  getAvailableTables: (reservationId: string): Promise<AvailableTable[]> =>
    request<AvailableTable[]>(`/api/admin/reservations/${reservationId}/available-tables`),

  updateReservationStatus: (
    id: string,
    payload: UpdateReservationStatusRequest,
  ): Promise<ReservationResponse> =>
    request<ReservationResponse>(`/api/admin/reservations/${id}/status`, {
      method: "PATCH",
      body: JSON.stringify(payload),
    }),
};
