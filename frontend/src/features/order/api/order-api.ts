import { API_BASE } from "@/lib/table-api";
import { getSession } from "@/lib/auth-session";
import type {
  AddOrderItemRequest,
  AddOrderItemsBatchRequest,
  AdjustOrderItemsRequest,
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
  /** Tạo đơn mới cho bàn, với các món và ghi chú được gửi trong payload. */
  create: (payload: CreateOrderRequest): Promise<OrderResponse> =>
    request<OrderResponse>("/api/orders", {
      method: "POST",
      body: JSON.stringify(payload),
    }),

  /** Lấy danh sách đơn, có thể lọc theo bàn và trạng thái. */
  list: (params?: { tableId?: string; status?: OrderStatus }): Promise<OrderResponse[]> => {
    const search = new URLSearchParams();
    if (params?.tableId) search.append("tableId", params.tableId);
    if (params?.status) search.append("status", params.status);
    const queryString = search.toString();
    return request<OrderResponse[]>(`/api/orders${queryString ? `?${queryString}` : ""}`);
  },

  /** Lấy thông tin đơn theo mã đơn. */
  getById: (id: string): Promise<OrderResponse> => request<OrderResponse>(`/api/orders/${id}`),

  /** Lấy đơn đang mở của bàn; trả về `null` nếu bàn chưa có đơn đang mở. */
  getActiveByTable: (tableId: string): Promise<OrderResponse | null> =>
    request<OrderResponse | null>(`/api/orders/table/${tableId}/active`),

  seatedAtTable: (tableId: string) =>
    request<OrderResponse[]>(`/api/orders/table/${encodeURIComponent(tableId)}/seated`),

  openSeatedOrder: (tableId: string, partySize: number) =>
    request<OrderResponse>(`/api/orders/table/${encodeURIComponent(tableId)}/seated`, {
      method: "POST",
      body: JSON.stringify({ partySize }),
    }),

  releaseSeats: (orderId: string, options?: { reservationAction?: "CANCEL" | "REVERT_CONFIRMED" }) =>
    request<void>(`/api/orders/${encodeURIComponent(orderId)}/release-seats`, {
      method: "POST",
      ...(options ? { body: JSON.stringify(options) } : {}),
    }),

  /** Thêm món vào một đơn đang mở (STT 84). */
  addItem: (orderId: string, payload: AddOrderItemRequest): Promise<OrderResponse> =>
    request<OrderResponse>(`/api/orders/${orderId}/items`, {
      method: "POST",
      body: JSON.stringify(payload),
    }),

  /** Gửi nhiều món mới cùng lúc vào đơn đang mở sau khi xác nhận gửi bếp. */
  addItemsBatch: (orderId: string, payload: AddOrderItemsBatchRequest): Promise<OrderResponse> =>
    request<OrderResponse>(`/api/orders/${orderId}/items/batch`, {
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

  adjustItems: (orderId: string, payload: AdjustOrderItemsRequest): Promise<OrderResponse> =>
    request<OrderResponse>(`/api/orders/${orderId}/items`, {
      method: "PATCH",
      body: JSON.stringify(payload),
    }),

  /** UC-18: thanh toán tiền mặt cho đơn được chọn. */
  checkoutCash: (
    orderId: string,
    payload: {
      phoneNumber?: string | undefined;
      voucherCode?: string | undefined;
      usePoints: boolean;
      pointsToUse?: number;
      expectedTotal: number;
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
    }>(`/api/orders/${encodeURIComponent(orderId)}/checkout-cash`, {
      method: "POST",
      body: JSON.stringify(payload),
    }),
};

/** API của màn hình bếp (UC-16). */
export const kitchenApi = {
  listTickets: (status?: CookingStatus, date?: string): Promise<KitchenTicketResponse[]> => {
    const search = new URLSearchParams();
    if (status) search.append("status", status);
    if (date) search.append("date", date);
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
