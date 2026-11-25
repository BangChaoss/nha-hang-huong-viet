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

  fireEvent.click(pos.getByRole("button", { name: "Giảm số lượng Phở bò" }));
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
  fireEvent.click(pos.getByRole("button", { name: "Giảm số lượng Phở bò" }));
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
  expect(pos.getByRole("button", { name: "Bớt Phở bò khỏi giỏ" })).toBeDisabled();
  const minus = pos.getByRole("button", { name: "Giảm số lượng Phở bò (Không hành)" });
  expect(minus).toBeEnabled();
  fireEvent.click(minus);
  expect(screen.getByRole("alertdialog")).toHaveTextContent("Huỷ 1 × Phở bò (Không hành) khỏi đơn?");
});

it("cập nhật ghi chú cả nhóm và đưa nút cộng bên phải vào Món mới chờ xác nhận gửi bếp", async () => {
  orders = [order(a, [item("one"), item("two")])];
  vi.mocked(orderApi.adjustItems).mockImplementation(async () => {
    const response = order(a, [item("one", { note: "Không hành" }), item("two", { note: "Không hành" })]);
    orders = [response];
    return response;
  });
  vi.mocked(orderApi.addItemsBatch).mockImplementation(async () => {
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
  expect(orderApi.addItemsBatch).not.toHaveBeenCalled();
  expect(orderApi.addItem).not.toHaveBeenCalled();
  expect(pos.getByRole("textbox", { name: "Ghi chú món mới: Phở bò" })).toHaveValue("Không hành");

  fireEvent.click(pos.getByRole("button", { name: "Gửi Bếp (1 phần)" }));
  const confirm = within(screen.getByRole("alertdialog"));
  expect(confirm.getByText("Xác nhận gửi đơn xuống bếp")).toBeInTheDocument();
  fireEvent.click(confirm.getByRole("button", { name: "Xác nhận gửi bếp" }));

  expect(await pos.findByText("3 × Phở bò")).toBeInTheDocument();
  expect(orderApi.addItemsBatch).toHaveBeenCalledExactlyOnceWith(a, {
    items: [{ menuItemId: "pho", quantity: 1, note: "Không hành" }],
  });
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

it("chỉ hiển thị một lần đơn được trả về cho cả hai bàn con", async () => {
  const left = { ...table, id: "left", tableNumber: "Bàn 01A" };
  const right = { ...table, id: "right", tableNumber: "Bàn 01B" };
  vi.mocked(tableApi.list).mockResolvedValue([left, right]);
  orders = [{ ...order(a, [item("one")]), tableId: right.id }];

  const pos = await openPos();
  expect(await pos.findByRole("button", { name: /#AAAAAAAA ·/, pressed: true })).toBeInTheDocument();
  expect(pos.getByText("1 × Phở bò")).toBeInTheDocument();
  expect(orderApi.seatedAtTable).toHaveBeenCalledWith(left.id);
  expect(orderApi.seatedAtTable).toHaveBeenCalledWith(right.id);
});

it("chọn bàn con đủ chỗ thay vì gửi đơn mới vào bàn con đã đầy", async () => {
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

  const pos = await openPos();
  const partySize = await pos.findByRole("spinbutton", { name: "Số khách của đơn mới" });
  expect(partySize).toHaveAttribute("max", "4");
  fireEvent.change(partySize, { target: { value: "4" } });
  fireEvent.click(pos.getByRole("button", { name: "Mở đơn mới" }));
  expect(await pos.findByRole("button", { name: /#BBBBBBBB · 4 khách/, pressed: true })).toBeInTheDocument();
  expect(tableApi.updateStatus).toHaveBeenCalledExactlyOnceWith(right.id, "OCCUPIED");
  expect(orderApi.openSeatedOrder).toHaveBeenCalledExactlyOnceWith(right.id, 4);
});

it("lọc thực đơn theo danh mục và giữ nguyên món mới khi đổi danh mục", async () => {
  const goiCuon = {
    id: "goi", categoryId: "starter", categoryName: "Khai vị", itemName: "Gỏi cuốn",
    description: null, currentPrice: 45000, imageUrl: null, isAvailable: true,
  };
  const traDa = {
    id: "tra", categoryId: "drink", categoryName: "Đồ uống", itemName: "Trà đào",
    description: null, currentPrice: 35000, imageUrl: null, isAvailable: true,
  };
  vi.mocked(menuApi.getOverview).mockResolvedValue({
    categories: [
      { id: "starter", categoryName: "Khai vị", displayOrder: 1, items: [goiCuon] },
      { id: pho.categoryId, categoryName: pho.categoryName, displayOrder: 2, items: [pho] },
      { id: "drink", categoryName: "Đồ uống", displayOrder: 3, items: [traDa] },
      { id: "dessert", categoryName: "Tráng miệng", displayOrder: 4, items: [] },
    ],
  });
  orders = [order(a, [])];

  const pos = await openPos();
  const filterGroup = await pos.findByRole("group", { name: "Lọc món theo danh mục" });
  expect(within(filterGroup).getByRole("button", { name: "Tất cả (3)", pressed: true })).toBeInTheDocument();
  expect(pos.getByText("Gỏi cuốn")).toBeInTheDocument();
  expect(pos.getByText("Phở bò")).toBeInTheDocument();
  expect(pos.getByText("Trà đào")).toBeInTheDocument();

  fireEvent.click(pos.getByRole("button", { name: "Thêm Gỏi cuốn" }));
  expect(pos.getByText("1 × Gỏi cuốn")).toBeInTheDocument();

  fireEvent.click(within(filterGroup).getByRole("button", { name: "Đồ uống (1)" }));
  expect(within(filterGroup).getByRole("button", { name: "Đồ uống (1)", pressed: true })).toBeInTheDocument();
  expect(pos.queryByText("Gỏi cuốn")).not.toBeInTheDocument();
  expect(pos.queryByText("Phở bò")).not.toBeInTheDocument();
  expect(pos.getByText("Trà đào")).toBeInTheDocument();
  expect(pos.getByText("1 × Gỏi cuốn")).toBeInTheDocument();

  fireEvent.click(within(filterGroup).getByRole("button", { name: "Tráng miệng (0)" }));
  expect(pos.getByText("Danh mục này chưa có món.")).toBeInTheDocument();
  expect(pos.getByText("1 × Gỏi cuốn")).toBeInTheDocument();

  fireEvent.click(within(filterGroup).getByRole("button", { name: "Tất cả (3)" }));
  expect(pos.getByText("Gỏi cuốn")).toBeInTheDocument();
  expect(pos.getByText("Phở bò")).toBeInTheDocument();
  expect(pos.getByText("Trà đào")).toBeInTheDocument();
});

it("yêu cầu nhấn xác nhận trước khi gửi đơn đang mở xuống bếp", async () => {
  const bunCha = {
    id: "bun", categoryId: "main", categoryName: "Món chính", itemName: "Bún chả",
    description: null, currentPrice: 45000, imageUrl: null, isAvailable: true,
  };
  vi.mocked(menuApi.getOverview).mockResolvedValue({
    categories: [{ id: pho.categoryId, categoryName: pho.categoryName, displayOrder: 1, items: [pho, bunCha] }],
  });
  orders = [order(a, [item("one")])];
  vi.mocked(orderApi.addItemsBatch).mockImplementation(async (id, payload) => {
    const appended = payload.items.map((entry, idx) =>
      item(`added-${idx}`, {
        menuItemId: entry.menuItemId,
        itemName: entry.menuItemId === "pho" ? "Phở bò" : "Bún chả",
        quantity: entry.quantity,
        unitPrice: entry.menuItemId === "pho" ? 85000 : 45000,
        lineTotal: (entry.menuItemId === "pho" ? 85000 : 45000) * entry.quantity,
        note: entry.note ?? null,
      }),
    );
    const updated = order(id, [...(orders[0]?.items ?? []), ...appended]);
    orders = [updated];
    return updated;
  });

  const pos = await openPos();
  await pos.findByRole("button", { name: "Thêm Phở bò" });
  expect(pos.getByRole("button", { name: "Gửi Bếp" })).toBeDisabled();

  fireEvent.click(pos.getByRole("button", { name: "Thêm Phở bò" }));
  fireEvent.click(pos.getByRole("button", { name: "Thêm Phở bò" }));
  fireEvent.click(pos.getByRole("button", { name: "Thêm Bún chả" }));
  fireEvent.change(pos.getByRole("textbox", { name: "Ghi chú món mới: Phở bò" }), {
    target: { value: "Ít hành" },
  });

  expect(orderApi.addItemsBatch).not.toHaveBeenCalled();
  expect(orderApi.addItem).not.toHaveBeenCalled();
  expect(pos.getByRole("button", { name: "Thanh toán đơn #AAAAAAAA" })).toBeDisabled();
  expect(pos.getByText("Gửi bếp hoặc xóa các món mới trước khi thanh toán.")).toBeInTheDocument();

  fireEvent.click(pos.getByRole("button", { name: "Gửi Bếp (3 phần)" }));
  const confirm = within(screen.getByRole("alertdialog"));
  expect(confirm.getByText("Xác nhận gửi đơn xuống bếp")).toBeInTheDocument();
  expect(confirm.getByText("2 × Phở bò")).toBeInTheDocument();
  expect(confirm.getByText("Ghi chú: Ít hành")).toBeInTheDocument();
  expect(confirm.getByText("1 × Bún chả")).toBeInTheDocument();
  expect(confirm.getByText("215.000đ")).toBeInTheDocument();

  fireEvent.click(confirm.getByRole("button", { name: "Quay lại" }));
  await waitFor(() => expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument());
  expect(orderApi.addItemsBatch).not.toHaveBeenCalled();
  expect(pos.getByDisplayValue("Ít hành")).toBeInTheDocument();

  fireEvent.click(pos.getByRole("button", { name: "Gửi Bếp (3 phần)" }));
  fireEvent.click(within(screen.getByRole("alertdialog")).getByRole("button", { name: "Xác nhận gửi bếp" }));

  await waitFor(() =>
    expect(orderApi.addItemsBatch).toHaveBeenCalledExactlyOnceWith(a, {
      items: [
        { menuItemId: "pho", quantity: 2, note: "Ít hành" },
        { menuItemId: "bun", quantity: 1, note: undefined },
      ],
    }),
  );
  await waitFor(() => expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument());
  expect(await pos.findByText("1 × Bún chả")).toBeInTheDocument();
  expect(pos.getByText("2 × Phở bò")).toBeInTheDocument();
  expect(pos.getByRole("button", { name: "Gửi Bếp" })).toBeDisabled();
  expect(pos.getByRole("button", { name: "Thanh toán đơn #AAAAAAAA" })).toBeEnabled();
});

it("giữ lại Món mới khi gửi bếp gặp lỗi để nhân viên chỉnh sửa", async () => {
  orders = [order(a, [])];
  vi.mocked(orderApi.addItemsBatch).mockRejectedValue(new Error("Món Phở bò hiện không còn sẵn."));

  const pos = await openPos();
  fireEvent.click(await pos.findByRole("button", { name: "Thêm Phở bò" }));
  fireEvent.click(pos.getByRole("button", { name: "Gửi Bếp (1 phần)" }));
  fireEvent.click(within(screen.getByRole("alertdialog")).getByRole("button", { name: "Xác nhận gửi bếp" }));

  await waitFor(() => expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument());
  expect(pos.getByText("1 × Phở bò")).toBeInTheDocument();
  expect(pos.getByRole("button", { name: "Gửi Bếp (1 phần)" })).toBeEnabled();
});

it("tách riêng giỏ Món mới giữa các đơn ngồi ghép trên cùng bàn", async () => {
  const bunCha = {
    id: "bun", categoryId: "main", categoryName: "Món chính", itemName: "Bún chả",
    description: null, currentPrice: 45000, imageUrl: null, isAvailable: true,
  };
  vi.mocked(menuApi.getOverview).mockResolvedValue({
    categories: [{ id: pho.categoryId, categoryName: pho.categoryName, displayOrder: 1, items: [pho, bunCha] }],
  });
  orders = [order(a, []), order(b, [])];
  vi.mocked(orderApi.addItemsBatch).mockImplementation(async (id, payload) => {
    const appended = payload.items.map((entry, idx) =>
      item(`added-${idx}`, {
        menuItemId: entry.menuItemId,
        itemName: entry.menuItemId === "pho" ? "Phở bò" : "Bún chả",
        quantity: entry.quantity,
        unitPrice: 45000,
        lineTotal: 45000 * entry.quantity,
      }),
    );
    const updated = order(id, appended);
    orders = orders.map((entry) => (entry.id === id ? updated : entry));
    return updated;
  });

  const pos = await openPos();
  fireEvent.click(await pos.findByRole("button", { name: /#AAAAAAAA ·/ }));
  fireEvent.click(await pos.findByRole("button", { name: "Thêm Phở bò" }));
  expect(pos.getByText("1 × Phở bò")).toBeInTheDocument();

  fireEvent.click(pos.getByRole("button", { name: /#BBBBBBBB ·/ }));
  expect(pos.queryByText("1 × Phở bò")).not.toBeInTheDocument();
  expect(pos.getByRole("button", { name: "Gửi Bếp" })).toBeDisabled();

  fireEvent.click(pos.getByRole("button", { name: "Thêm Bún chả" }));
  fireEvent.click(pos.getByRole("button", { name: "Gửi Bếp (1 phần)" }));
  fireEvent.click(within(screen.getByRole("alertdialog")).getByRole("button", { name: "Xác nhận gửi bếp" }));

  await waitFor(() =>
    expect(orderApi.addItemsBatch).toHaveBeenCalledExactlyOnceWith(b, {
      items: [{ menuItemId: "bun", quantity: 1, note: undefined }],
    }),
  );

  fireEvent.click(pos.getByRole("button", { name: /#AAAAAAAA ·/ }));
  expect(pos.getByText("1 × Phở bò")).toBeInTheDocument();
  expect(pos.getByRole("button", { name: "Gửi Bếp (1 phần)" })).toBeEnabled();
});

it("yêu cầu xác nhận trước khi gửi đơn đặt trước chưa có order xuống bếp", async () => {
  orders = [];
  vi.mocked(reservationApi.listByDate).mockResolvedValue([
    {
      id: "res-1",
      customerId: null,
      tableId: table.id,
      tableNumber: table.tableNumber,
      mergedTableIds: null,
      zoneId: table.zoneId,
      zoneName: "Sảnh",
      guestName: "Anh Minh",
      guestPhone: "0901234567",
      partySize: 2,
      reservationDate: "2026-11-24",
      reservationTime: "18:00:00",
      status: "ARRIVED",
      specialRequests: null,
      depositAmount: 0,
      createdAt: "2026-11-24T10:00:00",
    },
  ]);
  vi.mocked(orderApi.create).mockImplementation(async () => {
    const created = order(a, [item("one")]);
    orders = [created];
    return created;
  });

  const pos = await openPos();
  fireEvent.click(await pos.findByRole("button", { name: "Thêm Phở bò" }));
  fireEvent.click(pos.getByRole("button", { name: "Gửi Bếp (1 phần)" }));
  const confirm = within(screen.getByRole("alertdialog"));
  expect(confirm.getByText("Xác nhận gửi đơn xuống bếp")).toBeInTheDocument();

  fireEvent.click(confirm.getByRole("button", { name: "Quay lại" }));
  await waitFor(() => expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument());
  expect(orderApi.create).not.toHaveBeenCalled();

  fireEvent.click(pos.getByRole("button", { name: "Gửi Bếp (1 phần)" }));
  fireEvent.click(within(screen.getByRole("alertdialog")).getByRole("button", { name: "Xác nhận gửi bếp" }));

  await waitFor(() =>
    expect(orderApi.create).toHaveBeenCalledExactlyOnceWith({
      tableId: table.id,
      items: [{ menuItemId: "pho", quantity: 1, note: undefined }],
      submit: true,
    }),
  );
  expect(await pos.findByText("1 × Phở bò")).toBeInTheDocument();
});
