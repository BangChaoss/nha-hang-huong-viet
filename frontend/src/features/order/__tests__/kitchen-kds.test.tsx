import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, within } from "@testing-library/react";
import type { KitchenTicketResponse } from "../types/order.types";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

const tickets: [KitchenTicketResponse, KitchenTicketResponse] = [
  {
    id: "ticket-1",
    orderId: "order-1",
    orderItemId: "item-1",
    tableNumber: "Bàn 01",
    itemName: "Phở Bò",
    quantity: 2,
    note: null,
    status: "QUEUED" as const,
    createdAt: new Date().toISOString(),
    completedAt: null,
  },
  {
    id: "ticket-2",
    orderId: "order-1",
    orderItemId: "item-2",
    tableNumber: "Bàn 02",
    itemName: "Lẩu Riêu Cua",
    quantity: 1,
    note: "Không cay",
    status: "PREPARING" as const,
    createdAt: new Date().toISOString(),
    completedAt: null,
  },
];

let currentTickets: KitchenTicketResponse[] = tickets;
const mutate = vi.fn();

// `vi.mock` được hoist lên trước import nên trạng thái đăng nhập phải khai báo qua vi.hoisted.
const auth = vi.hoisted(() => ({ role: "MANAGER" as string | undefined }));

vi.mock("@/features/order/hooks/use-kitchen-tickets", () => ({
  useKitchenTickets: () => ({ data: currentTickets, isLoading: false, isError: false, error: null }),
}));

vi.mock("@/features/order/hooks/use-ticket-status", () => ({
  useUpdateTicketStatus: () => ({ mutate, isPending: false }),
}));

vi.mock("@/lib/auth-session", () => ({
  useSession: () => ({ session: auth.role ? { role: auth.role } : null, ready: true }),
  isUserManager: (role: string | undefined) => role === "ADMIN" || role === "MANAGER",
}));

const { KitchenKDS } = await import("@/components/hv/KitchenKDS");

function renderKds(readOnly?: boolean) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <KitchenKDS {...(readOnly === undefined ? {} : { readOnly })} />
    </QueryClientProvider>,
  );
}

describe("KitchenKDS", () => {
  beforeEach(() => {
    auth.role = "MANAGER";
    currentTickets = tickets;
    mutate.mockClear();
  });

  it("màn hình của đầu bếp chỉ để xem — không có nút cập nhật trạng thái", () => {
    renderKds(true);

    expect(screen.getByText(/chỉ xem/i)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /bắt đầu nấu/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /báo món hoàn tất/i })).not.toBeInTheDocument();
  });

  it("tài khoản đầu bếp mở màn quản lý cũng không thấy nút cập nhật", () => {
    auth.role = "CHEF";
    renderKds();

    expect(screen.getByText(/chỉ xem/i)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /bắt đầu nấu/i })).not.toBeInTheDocument();
  });

  it("quản lý thấy nút chuyển trạng thái cho từng cột", () => {
    renderKds();

    expect(screen.getAllByRole("button", { name: /bắt đầu nấu/i }).length).toBeGreaterThan(0);
    expect(screen.getAllByRole("button", { name: /báo món hoàn tất/i }).length).toBeGreaterThan(0);
    expect(screen.queryByText(/chỉ xem/i)).not.toBeInTheDocument();
  });

  it("nhân viên phục vụ cũng được cập nhật trạng thái phiếu", () => {
    auth.role = "STAFF";
    renderKds();

    expect(screen.queryByText(/chỉ xem/i)).not.toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: /bắt đầu nấu/i }).length).toBeGreaterThan(0);
  });

  it("bấm 'Bắt đầu nấu' gửi trạng thái PREPARING cho đúng phiếu", () => {
    renderKds();

    fireEvent.click(screen.getByRole("button", { name: /bắt đầu nấu/i }));

    expect(mutate).toHaveBeenCalledTimes(1);
    expect(mutate).toHaveBeenCalledWith(
      { id: "ticket-1", status: "PREPARING" },
      expect.objectContaining({ onSuccess: expect.any(Function), onError: expect.any(Function) }),
    );
  });

  it("bấm 'Báo món hoàn tất' gửi trạng thái READY cho đúng phiếu", () => {
    renderKds();

    fireEvent.click(screen.getByRole("button", { name: /báo món hoàn tất/i }));

    expect(mutate).toHaveBeenCalledTimes(1);
    expect(mutate).toHaveBeenCalledWith(
      { id: "ticket-2", status: "READY" },
      expect.objectContaining({ onSuccess: expect.any(Function), onError: expect.any(Function) }),
    );
  });

  it("đánh dấu phiếu READY đã phục vụ, nhưng đầu bếp không được sửa", () => {
    currentTickets = [{ ...tickets[0], status: "READY" }];
    auth.role = "STAFF";
    const { rerender } = renderKds();
    fireEvent.click(screen.getByRole("button", { name: /Đánh dấu đã phục vụ: Phở Bò, Bàn 01/ }));
    expect(mutate).toHaveBeenCalledExactlyOnceWith(
      { id: "ticket-1", status: "SERVED" },
      expect.objectContaining({ onSuccess: expect.any(Function) }),
    );

    auth.role = "CHEF";
    rerender(<KitchenKDS />);
    expect(screen.queryByRole("button", { name: /đánh dấu đã phục vụ/i })).not.toBeInTheDocument();
  });

  it("gom cùng bàn và đơn nhưng giữ lượt của món gọi thêm sau bàn khác", () => {
    currentTickets = [
      tickets[0],
      { ...tickets[1], orderId: "order-2", status: "QUEUED" },
      { ...tickets[0], id: "ticket-3", itemName: "Nem", additional: true },
      { ...tickets[0], id: "ticket-4", orderId: "order-3", itemName: "Bún" },
    ];
    renderKds();

    const firstTable = within(screen.getByRole("region", { name: "Bàn 01" }));
    const otherTable = within(screen.getByRole("region", { name: "Bàn 02" }));
    expect(firstTable.getByRole("heading", { name: "Đơn #ORDER-1" })).toBeInTheDocument();
    expect(firstTable.getByRole("heading", { name: "Đơn #ORDER-3" })).toBeInTheDocument();
    expect(firstTable.getByText("Lượt 1 · Tiếp theo")).toBeInTheDocument();
    expect(otherTable.getByText("Lượt 2")).toBeInTheDocument();
    expect(firstTable.getByText("Lượt 3").closest("li")).toHaveTextContent("Gọi thêm");
  });

  it("không hiển thị nút cập nhật với vai trò không có quyền", () => {
    auth.role = "CUSTOMER";
    renderKds();

    expect(screen.getByText(/chỉ xem/i)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /bắt đầu nấu/i })).not.toBeInTheDocument();
  });
});
