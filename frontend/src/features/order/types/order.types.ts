/** Kiểu dữ liệu của module Order + Kitchen KDS (đồng bộ với backend). */

/** Loại hình đơn: tại bàn hoặc mang đi. */
export type OrderType = "DINE_IN" | "TAKE_AWAY";

/** Trạng thái của cả đơn. */
export type OrderStatus = "IN_PROGRESS" | "COMPLETED" | "CANCELLED";

/** Trạng thái chế biến của một món trong đơn (dùng chung cho order item và phiếu bếp). */
export type CookingStatus = "QUEUED" | "PREPARING" | "READY" | "SERVED" | "CANCELLED";

/** Một dòng món trong đơn. */
export type OrderItemResponse = {
  id: string;
  menuItemId: string;
  itemName: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
  note: string | null;
  cookingStatus: CookingStatus;
  cancelReason: string | null;
  createdAt: string;
};

/** Một đơn của bàn. */
export type OrderResponse = {
  id: string;
  tableId: string;
  tableNumber: string;
  staffId: string | null;
  orderType: OrderType;
  status: OrderStatus;
  createdAt: string;
  closedAt: string | null;
  /**
   * Tạm tính của đơn do backend tính: chỉ cộng các món **chưa huỷ**.
   * Món đã huỷ (CANCELLED) không bao giờ vào hoá đơn (BR-41).
   */
  subtotal: number;
  /** Phần tiền của các món đã huỷ — giữ lại để đối soát, không tính vào hoá đơn. */
  cancelledAmount: number;
  items: OrderItemResponse[];
};

/** Một phiếu bếp (một dòng món được đẩy xuống bếp). */
export type KitchenTicketResponse = {
  id: string;
  orderId: string;
  orderItemId: string;
  tableNumber: string;
  itemName: string;
  quantity: number;
  note: string | null;
  status: CookingStatus;
  createdAt: string;
  completedAt: string | null;
};

/** Dòng món gửi lên khi tạo đơn. */
export type OrderItemRequest = {
  menuItemId: string;
  quantity: number;
  note?: string | undefined;
};

/** Body của `POST /api/orders/{orderId}/items` (thêm món vào đơn đang mở). */
export type AddOrderItemRequest = {
  menuItemId: string;
  quantity: number;
  note?: string | undefined;
};

/** Body của `PATCH /api/orders/{orderId}/items/{itemId}` (sửa số lượng / ghi chú món). */
export type UpdateOrderItemRequest = {
  quantity: number;
  note?: string | undefined;
};

/** Body của `POST /api/orders`. */
export type CreateOrderRequest = {
  tableId: string;
  orderType?: OrderType;
  items: OrderItemRequest[];
  submit?: boolean;
};

/** Message WebSocket `/topic/kitchen/tickets`. */
export type KitchenTicketEvent = {
  action: "CREATED" | "UPDATED";
  ticket: KitchenTicketResponse;
};

/** Message WebSocket `/topic/kitchen/completed` (STT 90). */
export type KitchenCompletedMessage = {
  orderId: string;
  orderItemId: string;
  tableId: string;
  tableNumber: string;
  itemName: string;
  quantity: number;
  status: CookingStatus;
};

/** Nhãn tiếng Việt của trạng thái chế biến. */
export const COOKING_STATUS_LABELS: Record<CookingStatus, string> = {
  QUEUED: "Chờ chế biến",
  PREPARING: "Đang nấu",
  READY: "Sẵn sàng",
  SERVED: "Đã phục vụ",
  CANCELLED: "Đã hủy",
};

/** Nhãn tiếng Việt của trạng thái đơn. */
export const ORDER_STATUS_LABELS: Record<OrderStatus, string> = {
  IN_PROGRESS: "Đang phục vụ",
  COMPLETED: "Hoàn tất",
  CANCELLED: "Đã hủy",
};
