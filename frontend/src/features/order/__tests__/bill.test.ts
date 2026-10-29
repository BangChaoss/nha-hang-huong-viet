import { describe, expect, it } from "vitest";
import { splitBillItems } from "../utils/bill";
import type { OrderItemResponse } from "../types/order.types";

const item = (overrides: Partial<OrderItemResponse>): OrderItemResponse => ({
  id: "item-1",
  menuItemId: "menu-1",
  itemName: "Phở Bò",
  quantity: 2,
  unitPrice: 55000,
  lineTotal: 110000,
  note: null,
  cookingStatus: "QUEUED",
  cancelReason: null,
  createdAt: "2026-10-24T10:00:00",
  ...overrides,
});

describe("splitBillItems", () => {
  it("cộng mọi món chưa huỷ vào tạm tính", () => {
    const bill = splitBillItems([
      item({ id: "item-1" }),
      item({ id: "item-2", cookingStatus: "PREPARING", lineTotal: 60000, quantity: 1 }),
    ]);

    expect(bill.lines).toHaveLength(2);
    expect(bill.subtotal).toBe(170000);
    expect(bill.cancelled).toHaveLength(0);
    expect(bill.cancelledAmount).toBe(0);
  });

  it("loại món đã huỷ khỏi hoá đơn và giữ lại để đối soát (BR-41)", () => {
    const bill = splitBillItems([
      item({ id: "item-1", lineTotal: 110000 }),
      item({ id: "item-2", cookingStatus: "CANCELLED", lineTotal: 85000, quantity: 1 }),
    ]);

    expect(bill.lines.map((line) => line.key)).toEqual(["item-1"]);
    expect(bill.subtotal).toBe(110000);
    expect(bill.cancelled.map((entry) => entry.id)).toEqual(["item-2"]);
    expect(bill.cancelledAmount).toBe(85000);
  });

  it("đơn chỉ có món đã huỷ thì hoá đơn bằng 0", () => {
    const bill = splitBillItems([
      item({ id: "item-1", cookingStatus: "CANCELLED" }),
      item({ id: "item-2", cookingStatus: "CANCELLED", lineTotal: 40000 }),
    ]);

    expect(bill.lines).toHaveLength(0);
    expect(bill.subtotal).toBe(0);
    expect(bill.cancelledAmount).toBe(150000);
  });

  it("hiển thị đúng số lượng và tên món trên từng dòng", () => {
    const bill = splitBillItems([
      item({ quantity: 3, itemName: "Lẩu Riêu Cua", lineTotal: 1140000 }),
    ]);

    expect(bill.lines[0]).toEqual({
      key: "item-1",
      label: "3 × Lẩu Riêu Cua",
      amount: 1140000,
    });
  });
});
