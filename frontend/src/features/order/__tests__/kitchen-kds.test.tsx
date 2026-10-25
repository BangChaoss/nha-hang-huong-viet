import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

const tickets = [
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

const mutate = vi.fn();

// `vi.mock` được hoist lên trước import nên trạng thái đăng nhập phải khai báo qua vi.hoisted.
const auth = vi.hoisted(() => ({ role: "MANAGER" as string | undefined }));

vi.mock("@/features/order/hooks/use-kitchen-tickets", () => ({
  useKitchenTickets: () => ({ data: tickets, isLoading: false, isError: false, error: null }),
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
});
