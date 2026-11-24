import type { OrderItemResponse } from "../types/order.types";

export type OrderItemGroup = {
  first: OrderItemResponse;
  itemIds: string[];
  quantity: number;
  lineTotal: number;
};

export function orderItemGroupKey(item: OrderItemResponse) {
  return JSON.stringify([
    item.menuItemId,
    item.unitPrice,
    item.cookingStatus,
    item.note?.trim() || null,
    item.cancelReason,
  ]);
}

// POS totals are grouped for display; each kitchen batch keeps its own ID and FIFO position.
export function groupOrderItems(items: OrderItemResponse[]) {
  const groups = new Map<string, OrderItemGroup>();
  for (const item of items) {
    const key = orderItemGroupKey(item);
    const group = groups.get(key) ?? { first: item, itemIds: [], quantity: 0, lineTotal: 0 };
    group.itemIds.push(item.id);
    group.quantity += item.quantity;
    group.lineTotal += item.lineTotal;
    groups.set(key, group);
  }
  return groups;
}
