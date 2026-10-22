import { getSession } from "@/lib/auth-session";

export type TableStatus = "AVAILABLE" | "RESERVED" | "OCCUPIED" | "CLEANING" | "MAINTENANCE";

export type FloorPlanShape = "RECTANGLE" | "CIRCLE";

export type ZoneResponse = {
  id: string;
  zoneName: string;
  isVip: boolean;
  tableCount: number;
};

export type ZoneRequest = {
  zoneName: string;
  isVip: boolean;
};

export type TableResponse = {
  id: string;
  tableNumber: string;
  capacity: number;
  status: TableStatus;
  zoneId: string;
  zoneName: string | null;
  zoneIsVip: boolean | null;
};

export type TableRequest = {
  tableNumber: string;
  capacity: number;
  zoneId: string;
};

export type UpdateTableStatusRequest = {
  status: TableStatus;
};

export type FloorPlanLayoutResponse = {
  id: string;
  tableId: string;
  positionX: number;
  positionY: number;
  width: number;
  height: number;
  rotation: number;
  shape: FloorPlanShape;
  tableNumber: string;
  capacity: number;
  status: TableStatus;
  zoneName: string | null;
  zoneIsVip: boolean | null;
};

export type FloorPlanLayoutRequest = {
  tableId: string;
  positionX: number;
  positionY: number;
  width: number;
  height: number;
  rotation: number;
  shape: FloorPlanShape;
};

export type TableStatusMessage = {
  tableId: string;
  tableNumber: string;
  oldStatus: TableStatus;
  newStatus: TableStatus;
  zoneName: string | null;
};

export const STATUS_COLORS: Record<
  TableStatus,
  { bg: string; text: string; label: string; badgeCls: string; dotCls: string }
> = {
  AVAILABLE: {
    bg: "#22c55e",
    text: "#ffffff",
    label: "Trống",
    badgeCls: "bg-emerald-500/15 text-emerald-700 border-emerald-500/30",
    dotCls: "bg-emerald-500",
  },
  RESERVED: {
    bg: "#eab308",
    text: "#ffffff",
    label: "Đã đặt",
    badgeCls: "bg-amber-500/15 text-amber-700 border-amber-500/30",
    dotCls: "bg-amber-500",
  },
  OCCUPIED: {
    bg: "#ef4444",
    text: "#ffffff",
    label: "Đang ăn",
    badgeCls: "bg-rose-500/15 text-rose-700 border-rose-500/30",
    dotCls: "bg-rose-500",
  },
  CLEANING: {
    bg: "#38bdf8",
    text: "#ffffff",
    label: "Dọn dẹp",
    badgeCls: "bg-sky-500/15 text-sky-700 border-sky-500/30",
    dotCls: "bg-sky-400",
  },
  MAINTENANCE: {
    bg: "#94a3b8",
    text: "#ffffff",
    label: "Bảo trì",
    badgeCls: "bg-slate-500/15 text-slate-700 border-slate-500/30",
    dotCls: "bg-slate-400",
  },
};

export const API_BASE = (import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080").replace(
  /\/$/,
  "",
);

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
      // Một số phản hồi lỗi không có nội dung JSON.
    }
    throw new Error(message);
  }

  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

export const zoneApi = {
  list: () => request<ZoneResponse[]>("/api/zones"),
  listAdmin: () => request<ZoneResponse[]>("/api/admin/zones"),
  getById: (id: string) => request<ZoneResponse>(`/api/admin/zones/${id}`),
  create: (payload: ZoneRequest) =>
    request<ZoneResponse>("/api/admin/zones", {
      method: "POST",
      body: JSON.stringify(payload),
    }),
  update: (id: string, payload: ZoneRequest) =>
    request<ZoneResponse>(`/api/admin/zones/${id}`, {
      method: "PUT",
      body: JSON.stringify(payload),
    }),
  remove: (id: string) => request<void>(`/api/admin/zones/${id}`, { method: "DELETE" }),
};

export const tableApi = {
  list: () => request<TableResponse[]>("/api/tables"),
  getById: (id: string) => request<TableResponse>(`/api/tables/${id}`),
  listAdmin: () => request<TableResponse[]>("/api/admin/tables"),
  getAdminById: (id: string) => request<TableResponse>(`/api/admin/tables/${id}`),
  create: (payload: TableRequest) =>
    request<TableResponse>("/api/admin/tables", {
      method: "POST",
      body: JSON.stringify(payload),
    }),
  update: (id: string, payload: TableRequest) =>
    request<TableResponse>(`/api/admin/tables/${id}`, {
      method: "PUT",
      body: JSON.stringify(payload),
    }),
  updateStatus: (id: string, status: TableStatus) =>
    request<TableResponse>(`/api/admin/tables/${id}/status`, {
      method: "PATCH",
      body: JSON.stringify({ status }),
    }),
  remove: (id: string) => request<void>(`/api/admin/tables/${id}`, { method: "DELETE" }),
};

export const floorPlanApi = {
  getAll: () => request<FloorPlanLayoutResponse[]>("/api/floor-plan"),
  saveAll: (payloads: FloorPlanLayoutRequest[]) =>
    request<FloorPlanLayoutResponse[]>("/api/admin/floor-plan", {
      method: "PUT",
      body: JSON.stringify(payloads),
    }),
};
