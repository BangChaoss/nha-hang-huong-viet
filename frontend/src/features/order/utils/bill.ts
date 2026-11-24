import type { OrderItemResponse } from "../types/order.types";

/** Một dòng trên hoá đơn thanh toán. */
export type BillLine = {
  /** ID của lượt gọi đầu tiên trong dòng đã gom. */
  key: string;
  label: string;
  amount: number;
};

/** Kết quả tách món trong đơn thành phần tính tiền và phần đã huỷ. */
export type BillBreakdown = {
  /** Các dòng món được tính tiền (mọi trạng thái trừ CANCELLED). */
  lines: BillLine[];
  /** Các món đã huỷ — hiển thị để đối soát nhưng không cộng vào tiền. */
  cancelled: OrderItemResponse[];
  /** Tổng tiền các món chưa huỷ. */
  subtotal: number;
  /** Tổng tiền các món đã huỷ. */
  cancelledAmount: number;
};

/**
 * BR-41: món đã huỷ không bao giờ được tính vào hoá đơn.
 *
 * Tách danh sách món của đơn đang mở thành phần tính tiền và phần đã huỷ, để màn
 * thu ngân hiển thị đúng số tiền khách phải trả.
 */
export function splitBillItems(items: OrderItemResponse[]): BillBreakdown {
  const totals = new Map<string, { id: string; itemName: string; quantity: number; amount: number }>();
  const cancelled: OrderItemResponse[] = [];
  let subtotal = 0;
  let cancelledAmount = 0;

  for (const item of items) {
    if (item.cookingStatus === "CANCELLED") {
      cancelled.push(item);
      cancelledAmount += item.lineTotal;
      continue;
    }
    const key = JSON.stringify([item.menuItemId, item.unitPrice]);
    const line = totals.get(key) ?? { id: item.id, itemName: item.itemName, quantity: 0, amount: 0 };
    line.quantity += item.quantity;
    line.amount += item.lineTotal;
    totals.set(key, line);
    subtotal += item.lineTotal;
  }
  const lines = Array.from(totals.values(), (line) => ({
    key: line.id, label: `${line.quantity} × ${line.itemName}`, amount: line.amount,
  }));

  return { lines, cancelled, subtotal, cancelledAmount };
}
