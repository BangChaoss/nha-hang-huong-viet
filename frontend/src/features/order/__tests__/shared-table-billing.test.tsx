import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import { BillingDialog } from "@/components/hv/StaffPOS";
import { orderApi } from "../api/order-api";
import type { TableResponse } from "@/lib/table-api";
import type { OrderResponse } from "../types/order.types";

vi.mock("../api/order-api", () => ({
  orderApi: { getById: vi.fn(), checkoutCash: vi.fn() },
}));

it("loads and settles only the selected order on a shared table", async () => {
  const table: TableResponse = {
    id: "table-05", tableNumber: "Bàn 05", capacity: 4, status: "OCCUPIED",
    zoneId: "zone-01", zoneName: "Sảnh", zoneIsVip: false, availableSeats: 2,
  };
  const order: OrderResponse = {
    id: "bbbb0002-order", tableId: table.id, tableNumber: table.tableNumber,
    staffId: null, orderType: "DINE_IN", status: "IN_PROGRESS", createdAt: "2026-10-28T12:00:00",
    closedAt: null, subtotal: 45000, cancelledAmount: 0, partySize: 1, seated: true,
    items: [{
      id: "item-b", menuItemId: "menu-b", itemName: "Bún chả", quantity: 1,
      unitPrice: 45000, lineTotal: 45000, note: null, cookingStatus: "SERVED",
      cancelReason: null, createdAt: "2026-10-28T12:00:00",
    }],
  };
  vi.mocked(orderApi.getById).mockResolvedValue(order);
  vi.mocked(orderApi.checkoutCash).mockResolvedValue({
    tableId: table.id, tableNumber: table.tableNumber, subtotal: 45000, vat: 4500,
    discountAmount: 0, pointsRedeemed: 0, total: 49500, pointsAwarded: 0,
  });
  const onClose = vi.fn();
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <BillingDialog table={table} order={order} onClose={onClose} />
    </QueryClientProvider>,
  );

  const bill = within(screen.getByRole("dialog", { name: /Bàn 05 \/ #BBBB0002/ }));
  expect(await bill.findByText("1 × Bún chả")).toBeInTheDocument();
  expect(orderApi.getById).toHaveBeenCalledWith(order.id);
  fireEvent.click(bill.getByRole("button", { name: "Hoàn Tất Thanh Toán (Tiền mặt)" }));
  await waitFor(() => expect(onClose).toHaveBeenCalledOnce());
  expect(orderApi.checkoutCash).toHaveBeenCalledExactlyOnceWith(order.id,
    expect.objectContaining({ expectedTotal: 49500, usePoints: false }),
  );
});

it("adds 250.000đ VIP room fee into taxable subtotal (275.000đ after 10% VAT) for VIP walk-in orders", async () => {
  const table: TableResponse = {
    id: "vip-1", tableNumber: "VIP 01", capacity: 10, status: "OCCUPIED",
    zoneId: "zone-vip", zoneName: "Phòng VIP", zoneIsVip: true,
  };
  const order: OrderResponse = {
    id: "vip-walkin-order", tableId: table.id, tableNumber: table.tableNumber,
    staffId: null, orderType: "DINE_IN", status: "IN_PROGRESS", createdAt: "2026-11-25T12:00:00",
    closedAt: null, subtotal: 45000, cancelledAmount: 0, vipFeeAmount: 250000, vipDepositPaid: 0,
    items: [{
      id: "item-b", menuItemId: "menu-b", itemName: "Bún chả", quantity: 1,
      unitPrice: 45000, lineTotal: 45000, note: null, cookingStatus: "SERVED",
      cancelReason: null, createdAt: "2026-11-25T12:00:00",
    }],
  };
  vi.mocked(orderApi.getById).mockResolvedValue(order);
  vi.mocked(orderApi.checkoutCash).mockResolvedValue({
    tableId: table.id, tableNumber: table.tableNumber, subtotal: 45000, vat: 29500,
    discountAmount: 0, pointsRedeemed: 0, total: 324500, pointsAwarded: 0,
  });
  const onClose = vi.fn();
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <BillingDialog table={table} order={order} onClose={onClose} />
    </QueryClientProvider>,
  );

  const bill = within(screen.getByRole("dialog", { name: /VIP 01 \/ #VIP-WALK/i }));
  expect(await bill.findByText("1 × Bún chả")).toBeInTheDocument();
  expect(bill.getByText("Phí thu phòng VIP (tính VAT 10%)")).toBeInTheDocument();
  expect(bill.getByText("+250.000đ")).toBeInTheDocument();
  expect(bill.getByText("29.500đ")).toBeInTheDocument();
  expect(bill.getByText("324.500đ")).toBeInTheDocument();

  fireEvent.click(bill.getByRole("button", { name: "Hoàn Tất Thanh Toán (Tiền mặt)" }));
  await waitFor(() => expect(onClose).toHaveBeenCalledOnce());
  expect(orderApi.checkoutCash).toHaveBeenCalledWith(order.id,
    expect.objectContaining({ expectedTotal: 324500, usePoints: false }),
  );
});

it("displays paid VIP fee & deposit notice without deducting or double-charging for pre-booked VIP orders", async () => {
  const table: TableResponse = {
    id: "vip-1", tableNumber: "VIP 01", capacity: 10, status: "OCCUPIED",
    zoneId: "zone-vip", zoneName: "Phòng VIP", zoneIsVip: true,
  };
  const order: OrderResponse = {
    id: "vip-prebook-order", tableId: table.id, tableNumber: table.tableNumber,
    staffId: null, orderType: "DINE_IN", status: "IN_PROGRESS", createdAt: "2026-11-25T12:00:00",
    closedAt: null, subtotal: 45000, cancelledAmount: 0, vipFeeAmount: 0, vipDepositPaid: 250000,
    items: [{
      id: "item-b", menuItemId: "menu-b", itemName: "Bún chả", quantity: 1,
      unitPrice: 45000, lineTotal: 45000, note: null, cookingStatus: "SERVED",
      cancelReason: null, createdAt: "2026-11-25T12:00:00",
    }],
  };
  vi.mocked(orderApi.getById).mockResolvedValue(order);
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <BillingDialog table={table} order={order} onClose={vi.fn()} />
    </QueryClientProvider>,
  );

  const bill = within(screen.getByRole("dialog", { name: /VIP 01 \/ #VIP-PREB/i }));
  expect(await bill.findByText("1 × Bún chả")).toBeInTheDocument();
  expect(bill.getByText("Phí thu & cọc phòng VIP")).toBeInTheDocument();
  expect(bill.getByText("Đã thanh toán 250.000đ (không trừ hóa đơn)")).toBeInTheDocument();
  expect(bill.getByText("49.500đ")).toBeInTheDocument();
});
