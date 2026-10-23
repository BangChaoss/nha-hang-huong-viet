export type ReservationStatus =
  | "PENDING_HOLD"
  | "PENDING_DEPOSIT"
  | "CONFIRMED"
  | "ARRIVED"
  | "CANCELLED"
  | "NO_SHOW";

export interface CreateReservationRequest {
  guestName: string;
  guestPhone: string;
  guestEmail?: string | null;
  reservationDate: string; // Format: "YYYY-MM-DD"
  reservationTime: string; // Format: "HH:mm"
  partySize: number;
  zoneId: string;
  specialRequests?: string | null;
}

export interface ReservationResponse {
  id: string;
  guestName: string;
  guestPhone: string;
  guestEmail?: string | null;
  partySize: number;
  reservationDate: string;
  reservationTime: string;
  specialRequests?: string | null;
  status: ReservationStatus;
  zoneId?: string | null;
  zoneName?: string | null;
  zoneIsVip?: boolean | null;
  tableId?: string | null;
  tableNumber?: string | null;
  depositAmount: number;
  cancellationReason?: string | null;
  createdAt: string;
}

export interface CancelReservationRequest {
  reason?: string;
}

export interface UpdateReservationStatusRequest {
  status: ReservationStatus;
  tableId?: string;
  reason?: string;
}

export interface AvailableTable {
  id: string;
  tableNumber: string;
  capacity: number;
  status: string;
  zoneId?: string | null;
  zoneName?: string | null;
  zoneIsVip?: boolean | null;
}

export interface TimeSlotAvailability {
  time: string; // e.g. "11:00" or "11:00:00"
  label: string; // e.g. "11:00"
  confirmedSeats: number;
  remainingCapacity: number;
  available: boolean;
}

export interface ZoneAvailabilityResponse {
  zoneId: string;
  zoneName: string;
  isVip: boolean;
  totalCapacity: number;
  timeSlots: TimeSlotAvailability[];
}

export interface ZoneOption {
  id: string;
  zoneName: string;
  isVip: boolean;
  tableCount: number;
}

export const RESERVATION_STATUS_CONFIG: Record<
  ReservationStatus,
  { label: string; badgeCls: string }
> = {
  PENDING_HOLD: {
    label: "Chờ xác nhận",
    badgeCls: "bg-amber-500/15 text-amber-700 border-amber-500/30 dark:text-amber-400",
  },
  PENDING_DEPOSIT: {
    label: "Chờ đặt cọc",
    badgeCls: "bg-orange-500/15 text-orange-700 border-orange-500/30 dark:text-orange-400",
  },
  CONFIRMED: {
    label: "Đã xác nhận",
    badgeCls: "bg-emerald-500/15 text-emerald-700 border-emerald-500/30 dark:text-emerald-400",
  },
  ARRIVED: {
    label: "Đã đến nhận bàn",
    badgeCls: "bg-blue-500/15 text-blue-700 border-blue-500/30 dark:text-blue-400",
  },
  CANCELLED: {
    label: "Đã hủy",
    badgeCls: "bg-rose-500/15 text-rose-700 border-rose-500/30 dark:text-rose-400",
  },
  NO_SHOW: {
    label: "Không đến",
    badgeCls: "bg-slate-500/15 text-slate-700 border-slate-500/30 dark:text-slate-400",
  },
};
