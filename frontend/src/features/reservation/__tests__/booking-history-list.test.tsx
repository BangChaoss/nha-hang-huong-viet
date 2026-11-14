import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BookingHistoryList } from "../components/booking-history-list";
import * as authSession from "@/lib/auth-session";
import * as reservationHooks from "../hooks/use-booking-history";
import type { ReservationResponse } from "../types/reservation.types";

vi.mock("@/lib/auth-session");
vi.mock("../hooks/use-booking-history");

const createQueryClient = () =>
  new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

const mockBookingUser1: ReservationResponse = {
  id: "11111111-2222-3333-4444-555555555555",
  customerId: "user-1",
  guestName: "Khách User 1",
  guestPhone: "0901111111",
  guestEmail: "user1@example.com",
  partySize: 2,
  reservationDate: "2026-11-25",
  reservationTime: "18:00:00",
  status: "PENDING_HOLD",
  zoneId: "zone-1",
  zoneName: "Khu vực Sân Vườn",
  depositAmount: 0,
  createdAt: "2026-11-10T10:00:00Z",
};

const mockBookingUser2: ReservationResponse = {
  id: "22222222-3333-4444-5555-666666666666",
  customerId: "user-2",
  guestName: "Khách User 2",
  guestPhone: "0902222222",
  guestEmail: "user2@example.com",
  partySize: 4,
  reservationDate: "2026-11-25",
  reservationTime: "19:00:00",
  status: "PENDING_HOLD",
  zoneId: "zone-1",
  zoneName: "Khu vực Sân Vườn",
  depositAmount: 0,
  createdAt: "2026-11-10T10:00:00Z",
};

const mockBookingGuestWithoutAccount: ReservationResponse = {
  id: "33333333-4444-5555-6666-777777777777",
  customerId: null,
  guestName: "Khách Vãng Lai Không Tài Khoản",
  guestPhone: "0903333333",
  partySize: 3,
  reservationDate: "2026-11-25",
  reservationTime: "20:00:00",
  status: "PENDING_HOLD",
  zoneId: "zone-1",
  zoneName: "Khu vực Sân Vườn",
  depositAmount: 0,
  createdAt: "2026-11-10T10:00:00Z",
};

describe("BookingHistoryList - Quyền hiển thị nút Hủy khi tra cứu khách vãng lai", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("1. Chưa đăng nhập: Tra cứu đơn của tài khoản thành viên (customerId != null) KHÔNG hiển thị nút Hủy đặt bàn", async () => {
    vi.spyOn(authSession, "useSession").mockReturnValue({
      session: null,
      ready: true,
    });

    const mutateAsyncMock = vi.fn().mockResolvedValue([mockBookingUser1]);
    vi.spyOn(reservationHooks, "useBookingHistory").mockReturnValue({
      data: [],
      isLoading: false,
      refetch: vi.fn(),
    } as any);
    vi.spyOn(reservationHooks, "useGuestLookup").mockReturnValue({
      mutateAsync: mutateAsyncMock,
      isPending: false,
    } as any);

    const queryClient = createQueryClient();
    render(
      <QueryClientProvider client={queryClient}>
        <BookingHistoryList />
      </QueryClientProvider>
    );

    // Điền form tra cứu
    fireEvent.change(screen.getByLabelText(/Số điện thoại đặt bàn/i), {
      target: { value: "0901111111" },
    });
    fireEvent.change(screen.getByLabelText(/Mã đặt bàn/i), {
      target: { value: "11111111" },
    });

    fireEvent.click(screen.getByRole("button", { name: /Tra Cứu Đặt Bàn/i }));

    await waitFor(() => {
      expect(screen.getByText("HV-11111111")).toBeInTheDocument();
    });

    // Đơn của tài khoản thành viên -> Khách chưa đăng nhập KHÔNG được thấy nút Hủy đặt bàn
    expect(screen.queryByRole("button", { name: /Hủy đặt bàn/i })).not.toBeInTheDocument();
  });

  it("2. Đăng nhập User 1: Tra cứu đơn của User 2 KHÔNG hiển thị nút Hủy đặt bàn", async () => {
    vi.spyOn(authSession, "useSession").mockReturnValue({
      session: {
        id: "user-1",
        username: "user1",
        email: "user1@example.com",
        role: "CUSTOMER",
        token: "fake-token-1",
        refreshToken: null,
      },
      ready: true,
    });

    const mutateAsyncMock = vi.fn().mockResolvedValue([mockBookingUser2]);
    vi.spyOn(reservationHooks, "useBookingHistory").mockReturnValue({
      data: [],
      isLoading: false,
      refetch: vi.fn(),
    } as any);
    vi.spyOn(reservationHooks, "useGuestLookup").mockReturnValue({
      mutateAsync: mutateAsyncMock,
      isPending: false,
    } as any);

    const queryClient = createQueryClient();
    render(
      <QueryClientProvider client={queryClient}>
        <BookingHistoryList />
      </QueryClientProvider>
    );

    // Chuyển sang tab Tra Cứu Khách Vãng Lai
    fireEvent.mouseDown(screen.getByRole("tab", { name: /Tra Cứu Khách Vãng Lai/i }), {
      button: 0,
      ctrlKey: false,
    });

    fireEvent.change(screen.getByLabelText(/Số điện thoại đặt bàn/i), {
      target: { value: "0902222222" },
    });
    fireEvent.change(screen.getByLabelText(/Mã đặt bàn/i), {
      target: { value: "22222222" },
    });

    fireEvent.click(screen.getByRole("button", { name: /Tra Cứu Đặt Bàn/i }));

    await waitFor(() => {
      expect(screen.getByText("HV-22222222")).toBeInTheDocument();
    });

    // Không phải tài khoản của bản thân -> KHÔNG có nút Hủy đặt bàn
    expect(screen.queryByRole("button", { name: /Hủy đặt bàn/i })).not.toBeInTheDocument();
  });

  it("3. Khách vãng lai: Tra cứu đơn khách vãng lai (customerId == null) CÓ hiển thị nút Hủy đặt bàn", async () => {
    vi.spyOn(authSession, "useSession").mockReturnValue({
      session: null,
      ready: true,
    });

    const mutateAsyncMock = vi.fn().mockResolvedValue([mockBookingGuestWithoutAccount]);
    vi.spyOn(reservationHooks, "useBookingHistory").mockReturnValue({
      data: [],
      isLoading: false,
      refetch: vi.fn(),
    } as any);
    vi.spyOn(reservationHooks, "useGuestLookup").mockReturnValue({
      mutateAsync: mutateAsyncMock,
      isPending: false,
    } as any);

    const queryClient = createQueryClient();
    render(
      <QueryClientProvider client={queryClient}>
        <BookingHistoryList />
      </QueryClientProvider>
    );

    fireEvent.change(screen.getByLabelText(/Số điện thoại đặt bàn/i), {
      target: { value: "0903333333" },
    });
    fireEvent.change(screen.getByLabelText(/Mã đặt bàn/i), {
      target: { value: "33333333" },
    });

    fireEvent.click(screen.getByRole("button", { name: /Tra Cứu Đặt Bàn/i }));

    await waitFor(() => {
      expect(screen.getByText("HV-33333333")).toBeInTheDocument();
    });

    // Là đơn khách vãng lai của chính mình -> CÓ nút Hủy đặt bàn
    expect(screen.getByRole("button", { name: /Hủy đặt bàn/i })).toBeInTheDocument();
  });

  it("4. Đăng nhập User 1: Tra cứu đúng đơn của chính mình (customerId === session.id) CÓ hiển thị nút Hủy đặt bàn", async () => {
    vi.spyOn(authSession, "useSession").mockReturnValue({
      session: {
        id: "user-1",
        username: "user1",
        email: "user1@example.com",
        role: "CUSTOMER",
        token: "fake-token-1",
        refreshToken: null,
      },
      ready: true,
    });

    const mutateAsyncMock = vi.fn().mockResolvedValue([mockBookingUser1]);
    vi.spyOn(reservationHooks, "useBookingHistory").mockReturnValue({
      data: [],
      isLoading: false,
      refetch: vi.fn(),
    } as any);
    vi.spyOn(reservationHooks, "useGuestLookup").mockReturnValue({
      mutateAsync: mutateAsyncMock,
      isPending: false,
    } as any);

    const queryClient = createQueryClient();
    render(
      <QueryClientProvider client={queryClient}>
        <BookingHistoryList />
      </QueryClientProvider>
    );

    fireEvent.mouseDown(screen.getByRole("tab", { name: /Tra Cứu Khách Vãng Lai/i }), {
      button: 0,
      ctrlKey: false,
    });

    fireEvent.change(screen.getByLabelText(/Số điện thoại đặt bàn/i), {
      target: { value: "0901111111" },
    });
    fireEvent.change(screen.getByLabelText(/Mã đặt bàn/i), {
      target: { value: "11111111" },
    });

    fireEvent.click(screen.getByRole("button", { name: /Tra Cứu Đặt Bàn/i }));

    await waitFor(() => {
      expect(screen.getByText("HV-11111111")).toBeInTheDocument();
    });

    // Là chính tài khoản của bản thân -> CÓ hiển thị nút Hủy đặt bàn
    expect(screen.getByRole("button", { name: /Hủy đặt bàn/i })).toBeInTheDocument();
  });

  it("5. Tab 'Lịch Sử Của Tôi': Luôn hiển thị nút Hủy cho các đơn hợp lệ của chính tài khoản", async () => {
    vi.spyOn(authSession, "useSession").mockReturnValue({
      session: {
        id: "user-1",
        username: "user1",
        email: "user1@example.com",
        role: "CUSTOMER",
        token: "fake-token-1",
        refreshToken: null,
      },
      ready: true,
    });

    vi.spyOn(reservationHooks, "useBookingHistory").mockReturnValue({
      data: [mockBookingUser1],
      isLoading: false,
      refetch: vi.fn(),
    } as any);
    vi.spyOn(reservationHooks, "useGuestLookup").mockReturnValue({
      mutateAsync: vi.fn(),
      isPending: false,
    } as any);

    const queryClient = createQueryClient();
    render(
      <QueryClientProvider client={queryClient}>
        <BookingHistoryList />
      </QueryClientProvider>
    );

    // Mặc định mở tab "Lịch Sử Của Tôi" khi đã đăng nhập
    expect(screen.getByText("HV-11111111")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Hủy đặt bàn/i })).toBeInTheDocument();
  });
});
