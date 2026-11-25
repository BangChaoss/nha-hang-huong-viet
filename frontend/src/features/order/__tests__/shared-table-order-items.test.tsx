import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { StaffPOS } from "@/components/hv/StaffPOS";
import { menuApi } from "@/features/menu/api/menu-api";
import { reservationApi } from "@/features/reservation/api/reservation-api";
import { tableApi, zoneApi, type TableResponse } from "@/lib/table-api";
import { orderApi } from "../api/order-api";
import type { OrderItemResponse, OrderResponse } from "../types/order.types";

vi.mock("../api/order-api");
vi.mock("@/features/menu/api/menu-api");
vi.mock("@/features/reservation/api/reservation-api");
vi.mock("@/lib/table-api");
vi.mock("@/lib/use-table-websocket", () => ({ useTableWebSocket: vi.fn() }));
vi.mock("../hooks/use-kitchen-tickets", () => ({ useKitchenCompleted: vi.fn() }));

const table: TableResponse = {
  id: "table-1", tableNumber: "Bàn 01", capacity: 4, status: "OCCUPIED",
  zoneId: "zone-1", zoneName: "Sảnh", zoneIsVip: false, availableSeats: 2,
};
const pho = {
  id: "pho", categoryId: "main", categoryName: "Món chính", itemName: "Phở bò",
  description: null, currentPrice: 85000, imageUrl: null, isAvailable: true,
};
const item = (id: string, overrides: Partial<OrderItemResponse> = {}): OrderItemResponse => ({
  id, menuItemId: pho.id, itemName: pho.itemName, unitPrice: pho.currentPrice,
  lineTotal: pho.currentPrice, quantity: 1, cookingStatus: "QUEUED", note: null,
  cancelReason: null, createdAt: "2026-11-21T14:00:00", ...overrides,
});
const order = (id: string, items: OrderItemResponse[]): OrderResponse => ({
  id, tableId: table.id, tableNumber: table.tableNumber, orderType: "DINE_IN", status: "IN_PROGRESS",
  partySize: 1, seated: true, staffId: null, closedAt: null, createdAt: "2026-11-21T14:00:00",
  items, subtotal: items.reduce((sum, row) => row.cookingStatus === "CANCELLED" ? sum : sum + row.lineTotal, 0),
  cancelledAmount: items.reduce((sum, row) => row.cookingStatus === "CANCELLED" ? sum + row.lineTotal : sum, 0),
});
const a = "aaaaaaaa-order";
const b = "bbbbbbbb-order";
let orders: OrderResponse[];

beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(zoneApi.list).mockResolvedValue([{ id: table.zoneId, zoneName: "Sảnh", tableCount: 1, isVip: false }]);
  vi.mocked(tableApi.list).mockResolvedValue([table]);
  vi.mocked(reservationApi.listByDate).mockResolvedValue([]);
  vi.mocked(menuApi.getOverview).mockResolvedValue({ categories: [{
    id: pho.categoryId, categoryName: pho.categoryName, displayOrder: 1, items: [pho],
  }] });
  vi.mocked(orderApi.seatedAtTable).mockImplementation(async () => orders);
});

async function openPos() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(<QueryClientProvider client={client}><StaffPOS /></QueryClientProvider>);
  fireEvent.click(await screen.findByRole("button", { name: /Bàn 01/ }));
  return within(screen.getByRole("dialog", { name: /Bàn 01 · Gọi món/ }));
}

it("gom các lượt gọi cũ và giảm từ cả hai cột, không đổi đơn ngồi ghép", async () => {
  const original = [item("one"), item("two"), item("three")];
  const other = order(b, [item("other", { note: "Đơn bên cạnh" })]);
  orders = [order(a, original), other];
  const cancel = (row: OrderItemResponse) => ({ ...row, cookingStatus: "CANCELLED" as const, cancelReason: "Giảm số lượng" });
  const adjust = vi.mocked(orderApi.adjustItems);
  adjust.mockImplementation(async (id, change) => {
    const current = orders.find((entry) => entry.id === id)!;
    const last = change.itemIds.at(-1);
    const response = order(id, current.items.map((row) => row.id === last ? cancel(row) : row));
    orders = orders.map((entry) => entry.id === id ? response : entry);
    return response;
  });
  const pos = await openPos();
  fireEvent.click(await pos.findByRole("button", { name: /#AAAAAAAA/ }));
  expect(await pos.findByText("3 × Phở bò")).toBeInTheDocument();
  expect(pos.queryByText("1 × Phở bò")).not.toBeInTheDocument();
  expect(pos.getByText("255.000đ")).toBeInTheDocument();

  fireEvent.click(pos.getByRole("button", { name: "Bớt Phở bò khỏi đơn" }));
  expect(await pos.findByText("2 × Phở bò")).toBeInTheDocument();
  expect(adjust).toHaveBeenNthCalledWith(1, a, {
    action: "DECREMENT", itemIds: ["one", "two", "three"], expectedQuantity: 3, expectedStatus: "QUEUED",
  });
  await waitFor(() => expect(pos.getByRole("button", { name: "Giảm số lượng Phở bò" })).toBeEnabled());
  fireEvent.click(pos.getByRole("button", { name: "Giảm số lượng Phở bò" }));
  await waitFor(() => expect(adjust).toHaveBeenNthCalledWith(2, a, {
    action: "DECREMENT", itemIds: ["one", "two"], expectedQuantity: 2, expectedStatus: "QUEUED",
  }));
  expect(await pos.findByText("1 × Phở bò")).toBeInTheDocument();
  expect(pos.getByText("2 × Phở bò")).toHaveClass("line-through");

  const minus = pos.getByRole("button", { name: "Giảm số lượng Phở bò" });
  await waitFor(() => expect(minus).toBeEnabled());
  fireEvent.click(minus);
  const confirmation = within(screen.getByRole("alertdialog"));
  expect(confirmation.getByText(/Huỷ 1 × Phở bò khỏi đơn/)).toBeInTheDocument();
  fireEvent.click(confirmation.getByRole("button", { name: "Giữ món" }));
  expect(adjust).toHaveBeenCalledTimes(2);
  fireEvent.click(pos.getByRole("button", { name: "Bớt Phở bò khỏi đơn" }));
  fireEvent.click(within(screen.getByRole("alertdialog")).getByRole("button", { name: "Huỷ món" }));
  await waitFor(() => expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument());
  expect(adjust).toHaveBeenNthCalledWith(3, a, {
    action: "CANCEL", itemIds: ["one"], expectedQuantity: 1, expectedStatus: "QUEUED", reason: "Khách đổi món",
  });
  expect(pos.getByRole("button", { name: "Thanh toán đơn #AAAAAAAA" })).toBeDisabled();
  fireEvent.click(pos.getByRole("button", { name: /#BBBBBBBB ·/ }));
  expect(pos.getByDisplayValue("Đơn bên cạnh")).toBeInTheDocument();
  expect(orders[1]).toEqual(other);
});

it("giữ riêng món khác ghi chú, giá hoặc trạng thái và vẫn cho giảm món đã hết hàng", async () => {
  orders = [order(a, [
    item("ready", { cookingStatus: "READY" }),
    item("plain"),
    item("old-price", { unitPrice: 70000, lineTotal: 70000 }),
    item("note", { note: "Không hành" }),
  ])];
  vi.mocked(menuApi.getOverview).mockResolvedValue({ categories: [{
    id: pho.categoryId, categoryName: pho.categoryName, displayOrder: 1, items: [{ ...pho, isAvailable: false }],
  }] });
  const pos = await openPos();
  await pos.findByText("Hết món");
  expect(pos.getAllByText("1 × Phở bò")).toHaveLength(4);
  expect(pos.getByRole("button", { name: "Thêm Phở bò" })).toBeDisabled();
  const minus = pos.getByRole("button", { name: "Bớt Phở bò khỏi đơn" });
  expect(minus).toBeEnabled();
  fireEvent.click(minus);
  expect(screen.getByRole("alertdialog")).toHaveTextContent("Huỷ 1 × Phở bò (Không hành) khỏi đơn?");
});

it("cập nhật ghi chú cả nhóm và gửi nút cộng bên phải như một lượt gọi mới", async () => {
  orders = [order(a, [item("one"), item("two")])];
  vi.mocked(orderApi.adjustItems).mockImplementation(async () => {
    const response = order(a, [item("one", { note: "Không hành" }), item("two", { note: "Không hành" })]);
    orders = [response];
    return response;
  });
  vi.mocked(orderApi.addItem).mockImplementation(async () => {
    const response = order(a, [...(orders[0]?.items ?? []), item("three", { note: "Không hành" })]);
    orders = [response];
    return response;
  });
  const pos = await openPos();
  const note = await pos.findByRole("textbox", { name: "Ghi chú cho bếp: Phở bò" });
  fireEvent.change(note, { target: { value: " Không hành " } });
  fireEvent.blur(note);
  const plus = await pos.findByRole("button", { name: "Tăng số lượng Phở bò (Không hành)" });
  expect(orderApi.adjustItems).toHaveBeenCalledWith(a, {
    action: "UPDATE_NOTE", itemIds: ["one", "two"], expectedQuantity: 2, expectedStatus: "QUEUED", note: "Không hành",
  });
  fireEvent.click(plus);
  expect(await pos.findByText("3 × Phở bò")).toBeInTheDocument();
  expect(orderApi.addItem).toHaveBeenCalledWith(a, { menuItemId: "pho", quantity: 1, note: "Không hành" });
});

it.each([false, true])("khôi phục đúng đơn đã xem hóa đơn (đã thu tiền: %s)", async (paid) => {
  orders = [order(a, [item("one")]), order(b, [item("two")])];
  vi.mocked(orderApi.getById).mockImplementation(async (id) => orders.find((entry) => entry.id === id)!);
  vi.mocked(orderApi.checkoutCash).mockImplementation(async (id) => {
    orders = orders.map((entry) => entry.id === id ? { ...entry, status: "COMPLETED" } : entry);
    return {
      tableId: table.id, tableNumber: table.tableNumber, subtotal: 85000, vat: 8500,
      discountAmount: 0, pointsRedeemed: 0, total: 93500, pointsAwarded: 0,
    };
  });
  const pos = await openPos();
  fireEvent.click(await pos.findByRole("button", { name: /#BBBBBBBB ·/ }));
  fireEvent.click(pos.getByRole("button", { name: "Thanh toán đơn #BBBBBBBB" }));
  const bill = within(await screen.findByRole("dialog", { name: /Hóa đơn.*#BBBBBBBB/ }));
  if (paid) {
    const settle = bill.getByRole("button", { name: "Hoàn Tất Thanh Toán (Tiền mặt)" });
    await waitFor(() => expect(settle).toBeEnabled());
    fireEvent.click(settle);
  } else {
    fireEvent.click(bill.getByRole("button", { name: "Close" }));
  }

  const restored = within(await screen.findByRole("dialog", { name: /Bàn 01 · Gọi món/ }));
  expect(await restored.findByRole("button", { name: /#BBBBBBBB ·/, pressed: true })).toBeInTheDocument();
  expect(restored.getByRole("button", { name: /#AAAAAAAA ·/, pressed: false })).toBeInTheDocument();
  if (paid) expect(await restored.findByRole("button", { name: "Trả chỗ" })).toBeInTheDocument();
});

it("kết thúc đơn cũ trống trước khi đóng bàn", async () => {
  orders = [{ ...order(a, [item("one", { cookingStatus: "CANCELLED" })]), partySize: null, seated: false }];
  vi.mocked(orderApi.releaseSeats).mockImplementation(async () => { orders = []; });
  const pos = await openPos();
  const cancel = await pos.findByRole("button", { name: "Hủy đơn trống" });
  expect(pos.queryByRole("button", { name: "Đóng bàn (Trống)" })).not.toBeInTheDocument();
  fireEvent.click(cancel);
  expect(await pos.findByRole("button", { name: "Đóng bàn (Trống)" })).toBeEnabled();
  expect(orderApi.releaseSeats).toHaveBeenCalledExactlyOnceWith(a);
});

it("hiển thị tách biệt từng bàn lẻ và tải đúng đơn của bàn được chọn", async () => {
  const left = { ...table, id: "left", tableNumber: "Bàn 01A" };
  const right = { ...table, id: "right", tableNumber: "Bàn 01B" };
  vi.mocked(tableApi.list).mockResolvedValue([left, right]);
  orders = [{ ...order(a, [item("one")]), tableId: right.id }];

  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(<QueryClientProvider client={client}><StaffPOS /></QueryClientProvider>);
  fireEvent.click(await screen.findByRole("button", { name: /Bàn 01B/ }));
  const pos = within(screen.getByRole("dialog", { name: /Bàn 01B · Gọi món/ }));

  expect(await pos.findByRole("button", { name: /#AAAAAAAA ·/, pressed: true })).toBeInTheDocument();
  expect(pos.getByText("1 × Phở bò")).toBeInTheDocument();
  expect(orderApi.seatedAtTable).toHaveBeenCalledExactlyOnceWith(right.id);
});

it("chọn trực tiếp bàn lẻ còn trống khi bàn lẻ bên cạnh đã đầy", async () => {
  const left = { ...table, id: "left", tableNumber: "Bàn 01A", availableSeats: 0 };
  const right: TableResponse = { ...table, id: "right", tableNumber: "Bàn 01B", status: "AVAILABLE", availableSeats: 4 };
  const opened = { ...right, status: "OCCUPIED" as const };
  vi.mocked(tableApi.list).mockResolvedValue([left, right]);
  vi.mocked(tableApi.updateStatus).mockImplementation(async () => {
    vi.mocked(tableApi.list).mockResolvedValue([left, opened]);
    return opened;
  });
  orders = [{ ...order(a, [item("one")]), tableId: left.id, partySize: 4 }];
  vi.mocked(orderApi.seatedAtTable).mockImplementation(async (id) => orders.filter((entry) => entry.tableId === id));
  vi.mocked(orderApi.openSeatedOrder).mockImplementation(async (id, size) => {
    const entry = { ...order(b, []), tableId: id, partySize: size };
    orders = [...orders, entry];
    return entry;
  });

  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(<QueryClientProvider client={client}><StaffPOS /></QueryClientProvider>);

  expect(await screen.findByRole("button", { name: /Bàn 01A.*0\/4 ghế trống/ })).toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: /Bàn 01B.*4 ghế/ }));
  const pos = within(screen.getByRole("dialog", { name: /Bàn 01B · Gọi món/ }));

  fireEvent.click(pos.getByRole("button", { name: "Mở bàn" }));
  const partySize = await pos.findByRole("spinbutton", { name: "Số khách của đơn mới" });
  expect(partySize).toHaveAttribute("max", "4");
  fireEvent.change(partySize, { target: { value: "4" } });
  fireEvent.click(pos.getByRole("button", { name: "Mở đơn mới" }));
  expect(await pos.findByRole("button", { name: /#BBBBBBBB · 4 khách/, pressed: true })).toBeInTheDocument();
  expect(tableApi.updateStatus).toHaveBeenCalledExactlyOnceWith(right.id, "OCCUPIED");
  expect(orderApi.openSeatedOrder).toHaveBeenCalledExactlyOnceWith(right.id, 4);
});
