import * as React from "react";
import { describe, expect, it, vi } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ConfirmBookingDialog } from "../components/confirm-booking-dialog";
import type { ReservationResponse, AvailableTable } from "../types/reservation.types";
import { toast } from "sonner";

vi.mock("sonner", () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
    warning: vi.fn(),
    info: vi.fn(),
  },
}));

const mockTables: AvailableTable[] = [
  { id: "tbl-1", tableNumber: "01", capacity: 4, status: "AVAILABLE", zoneId: "zone-1", zoneName: "Trong Nhà" },
  { id: "tbl-2", tableNumber: "02", capacity: 4, status: "AVAILABLE", zoneId: "zone-1", zoneName: "Trong Nhà" },
  { id: "tbl-3", tableNumber: "03", capacity: 4, status: "AVAILABLE", zoneId: "zone-1", zoneName: "Trong Nhà" },
];

vi.mock("@/lib/table-api", () => ({
  floorPlanApi: {
    getAll: vi.fn().mockResolvedValue([]),
  },
}));

vi.mock("../api/reservation-api", () => ({
  reservationApi: {
    getAvailableTables: vi.fn().mockImplementation(() => Promise.resolve(mockTables)),
  },
}));

vi.mock("../hooks/use-update-reservation-status", () => ({
  useUpdateReservationStatus: () => ({
    mutateAsync: vi.fn(),
    isPending: false,
  }),
}));

const mockBooking: ReservationResponse = {
  id: "res-12345678",
  guestName: "Trần Văn A",
  guestPhone: "0901234567",
  guestEmail: "vana@gmail.com",
  reservationDate: "2026-11-15",
  reservationTime: "18:30:00",
  partySize: 2,
  zoneId: "zone-1",
  zoneName: "Trong Nhà",
  zoneIsVip: false,
  tableId: null,
  tableNumber: null,
  mergedTableIds: null,
  status: "PENDING_HOLD",
  depositAmount: 0,
  createdAt: "2026-10-25T10:00:00Z",
};

describe("ConfirmBookingDialog Component", () => {
  it("renders with booking = null then rerenders with active booking without hook order errors", () => {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });

    const { rerender } = render(
      <QueryClientProvider client={queryClient}>
        <ConfirmBookingDialog booking={null} open={false} onOpenChange={() => {}} />
      </QueryClientProvider>
    );

    // Initial render with null booking should return null without breaking hooks
    expect(screen.queryByText(/Xác Nhận Đặt Bàn & Xếp Bàn/i)).not.toBeInTheDocument();

    // Rerender with active booking and open dialog (scenario that triggered the error)
    rerender(
      <QueryClientProvider client={queryClient}>
        <ConfirmBookingDialog booking={mockBooking} open={true} onOpenChange={() => {}} />
      </QueryClientProvider>
    );

    // Dialog title and booking code should now be rendered cleanly
    expect(screen.getByText(/Xác Nhận Đặt Bàn & Xếp Bàn/i)).toBeInTheDocument();
    expect(screen.getByText(/RES-1234/i)).toBeInTheDocument();
  });

  it("blocks redundant table merging when single selected table is already sufficient for partySize", async () => {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });

    render(
      <QueryClientProvider client={queryClient}>
        <ConfirmBookingDialog booking={mockBooking} open={true} onOpenChange={() => {}} />
      </QueryClientProvider>
    );

    // Wait for available tables query to load
    await waitFor(() => {
      expect(screen.getByRole("tab", { name: /Danh sách bàn \(3\)/i })).toBeInTheDocument();
    });

    // Switch to table list tab (Radix Tabs triggers on mouseDown or Enter keydown)
    const listTabTrigger = screen.getByRole("tab", { name: /Danh sách bàn \(3\)/i });
    fireEvent.keyDown(listTabTrigger, { key: "Enter" });
    fireEvent.click(listTabTrigger);

    // Wait for table buttons to appear
    await waitFor(() => {
      expect(screen.getByText("Bàn 01")).toBeInTheDocument();
      expect(screen.getByText("Bàn 02")).toBeInTheDocument();
    });

    // Click table 1 (capacity 4 >= partySize 2)
    fireEvent.click(screen.getByText("Bàn 01"));
    expect(toast.success).toHaveBeenCalledWith("Đã chọn Bàn 01 (4 chỗ)");

    // Attempt to merge table 2 (capacity 4)
    fireEvent.click(screen.getByText("Bàn 02"));

    // Expect warning toast and action blocked
    expect(toast.warning).toHaveBeenCalledWith(
      expect.stringContaining("đã đủ cho đoàn 2 khách. Để đổi sang bàn khác, vui lòng bỏ chọn bàn này trước.")
    );
  });
});
