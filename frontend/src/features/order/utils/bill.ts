import type { OrderItemResponse } from "../types/order.types";

/** Một dòng trên hoá đơn thanh toán. */
export type BillLine = {
  /** Khoá React — id dòng món thật, hoặc id món của dữ liệu demo. */
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
  const lines: BillLine[] = [];
  const cancelled: OrderItemResponse[] = [];
  let subtotal = 0;
  let cancelledAmount = 0;

  for (const item of items) {
    if (item.cookingStatus === "CANCELLED") {
      cancelled.push(item);
      cancelledAmount += item.lineTotal;
      continue;
    }
    lines.push({
      key: item.id,
      label: `${item.quantity} × ${item.itemName}`,
      amount: item.lineTotal,
    });
    subtotal += item.lineTotal;
  }

  return { lines, cancelled, subtotal, cancelledAmount };
}
