import { API_BASE } from "@/lib/table-api";
import { getSession } from "@/lib/auth-session";
import type {
  AddOrderItemRequest,
  CookingStatus,
  CreateOrderRequest,
  KitchenTicketResponse,
  OrderResponse,
  OrderStatus,
  UpdateOrderItemRequest,
} from "../types/order.types";

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
    if (response.status === 404 && path.includes("/table/") && path.endsWith("/active")
        && message.includes("Bàn này chưa có đơn hàng đang mở")) {
      return null as T;
    }
    throw new Error(message);
  }

  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

/** API của nghiệp vụ Order (UC-15, UC-17). */
export const orderApi = {
  create: (payload: CreateOrderRequest): Promise<OrderResponse> =>
    request<OrderResponse>("/api/orders", {
      method: "POST",
      body: JSON.stringify(payload),
    }),

  list: (params?: { tableId?: string; status?: OrderStatus }): Promise<OrderResponse[]> => {
    const search = new URLSearchParams();
    if (params?.tableId) search.append("tableId", params.tableId);
    if (params?.status) search.append("status", params.status);
    const queryString = search.toString();
    return request<OrderResponse[]>(`/api/orders${queryString ? `?${queryString}` : ""}`);
  },

  getById: (id: string): Promise<OrderResponse> => request<OrderResponse>(`/api/orders/${id}`),

  getActiveByTable: (tableId: string): Promise<OrderResponse | null> =>
    request<OrderResponse | null>(`/api/orders/table/${tableId}/active`),

  /** Thêm món vào một đơn đang mở (STT 84). */
  addItem: (orderId: string, payload: AddOrderItemRequest): Promise<OrderResponse> =>
    request<OrderResponse>(`/api/orders/${orderId}/items`, {
      method: "POST",
      body: JSON.stringify(payload),
    }),

  /** Sửa số lượng / ghi chú của một món trong đơn đang mở (STT 84). */
  updateItem: (
    orderId: string,
    itemId: string,
    payload: UpdateOrderItemRequest,
  ): Promise<OrderResponse> =>
    request<OrderResponse>(`/api/orders/${orderId}/items/${itemId}`, {
      method: "PATCH",
      body: JSON.stringify(payload),
    }),

  /** Huỷ một món trong đơn đang mở (STT 85). */
  cancelItem: (orderId: string, itemId: string, reason: string): Promise<OrderResponse> =>
    request<OrderResponse>(`/api/orders/${orderId}/items/${itemId}`, {
      method: "DELETE",
      body: JSON.stringify({ reason }),
    }),

  /** UC-18: thu ngân thanh toán tiền mặt cho đơn đang mở của bàn. */
  checkoutCash: (
    tableId: string,
    payload: {
      phoneNumber?: string | undefined;
      voucherCode?: string | undefined;
      usePoints: boolean;
    },
  ) =>
    request<{
      tableId: string;
      subtotal: number;
      vat: number;
      discountAmount: number;
      pointsRedeemed: number;
      total: number;
      pointsAwarded: number;
      tableNumber: string;
    }>(`/api/orders/table/${encodeURIComponent(tableId)}/checkout-cash`, {
      method: "POST",
      body: JSON.stringify(payload),
    }),
};

/** API của màn hình bếp (UC-16). */
export const kitchenApi = {
  listTickets: (status?: CookingStatus): Promise<KitchenTicketResponse[]> => {
    const search = new URLSearchParams();
    if (status) search.append("status", status);
    const queryString = search.toString();
    return request<KitchenTicketResponse[]>(
      `/api/kitchen/tickets${queryString ? `?${queryString}` : ""}`,
    );
  },

  updateStatus: (id: string, status: CookingStatus): Promise<KitchenTicketResponse> =>
    request<KitchenTicketResponse>(`/api/kitchen/tickets/${id}/status`, {
      method: "PATCH",
      body: JSON.stringify({ status }),
    }),
};
