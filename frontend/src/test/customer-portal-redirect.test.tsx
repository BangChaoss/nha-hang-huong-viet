import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { CustomerPortal } from "@/components/hv/CustomerPortal";
import { reservationApi } from "@/features/reservation/api/reservation-api";
import { toast } from "sonner";
import type { ReservationResponse } from "@/features/reservation/types/reservation.types";

vi.mock("@tanstack/react-router", () => ({
  useNavigate: () => vi.fn(),
}));

vi.mock("sonner", () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
    info: vi.fn(),
    warning: vi.fn(),
  },
}));

vi.mock("@/features/menu/hooks/use-menu", () => ({
  useMenu: () => ({
    categories: [],
    items: [],
    isLoading: false,
    isError: false,
    error: null,
    refetch: vi.fn(),
  }),
}));

vi.mock("@/features/reservation/components/book-form", () => ({
  BookForm: ({ initialReservation }: { initialReservation?: ReservationResponse | null }) => (
    <div data-testid="mock-book-form">
      {initialReservation ? `confirmation:${initialReservation.id}` : "new-booking-form"}
    </div>
  ),
}));

vi.mock("@/features/reservation/components/booking-history-list", () => ({
  BookingHistoryList: () => <div data-testid="mock-history-list">history-list</div>,
}));

vi.mock("@/features/feedback/components/customer-feedback", () => ({
  CustomerFeedback: () => <div data-testid="mock-feedback">feedback</div>,
}));

vi.mock("@/features/menu/components/menu-detail-dialog", () => ({
  MenuDetailDialog: () => null,
}));

describe("CustomerPortal VNPay Deposit Redirect", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("stays on history tab and shows success toast without loading BookForm when initialTab='history'", async () => {
    const getPublicSpy = vi.spyOn(reservationApi, "getPublicBooking");
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

    render(
      <QueryClientProvider client={queryClient}>
        <CustomerPortal
          initialTab="history"
          reservationId="res-vip-1"
          depositStatus="success"
        />
      </QueryClientProvider>,
    );

    expect(screen.getByTestId("mock-history-list")).toBeInTheDocument();
    expect(getPublicSpy).not.toHaveBeenCalled();
    await waitFor(() => {
      expect(toast.success).toHaveBeenCalledWith(
        "Thanh toán phí thu & cọc phòng VIP 250.000đ thành công! Đơn đặt bàn của bạn đang chờ xác nhận.",
      );
    });
  });

  it("opens book tab and loads reservation into BookForm when initialTab='book'", async () => {
    const mockReservation: ReservationResponse = {
      id: "res-vip-2",
      guestName: "Khách VIP",
      guestPhone: "0909999999",
      partySize: 4,
      reservationDate: "2026-11-26",
      reservationTime: "19:00",
      status: "PENDING_HOLD",
      depositAmount: 250000,
      createdAt: "2026-11-25T10:00:00",
    };

    const getPublicSpy = vi
      .spyOn(reservationApi, "getPublicBooking")
      .mockResolvedValue(mockReservation);
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

    render(
      <QueryClientProvider client={queryClient}>
        <CustomerPortal
          initialTab="book"
          reservationId="res-vip-2"
          depositStatus="success"
        />
      </QueryClientProvider>,
    );

    await waitFor(() => {
      expect(getPublicSpy).toHaveBeenCalledWith("res-vip-2");
      expect(screen.getByTestId("mock-book-form")).toHaveTextContent("confirmation:res-vip-2");
      expect(toast.success).toHaveBeenCalledWith(
        "Thanh toán phí thu & cọc phòng VIP 250.000đ thành công! Đơn đặt bàn của bạn đang chờ xác nhận.",
      );
    });
  });
});
