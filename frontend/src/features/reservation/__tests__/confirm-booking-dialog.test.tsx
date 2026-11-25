import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ConfirmBookingDialog } from "../components/confirm-booking-dialog";
import { reservationApi } from "../api/reservation-api";
import { floorPlanApi } from "@/lib/table-api";
import { toast } from "sonner";
import type { AvailableTable, ReservationResponse } from "../types/reservation.types";

const mutateAsyncMock = vi.fn();

vi.mock("../hooks/use-update-reservation-status", () => ({
  useUpdateReservationStatus: () => ({
    mutateAsync: mutateAsyncMock,
    isPending: false,
  }),
}));

vi.mock("sonner", () => ({
  toast: {
    info: vi.fn(),
    warning: vi.fn(),
    error: vi.fn(),
    success: vi.fn(),
  },
}));

vi.mock("@/components/hv/FloorPlanCanvas", () => ({
  FloorPlanCanvas: () => <div data-testid="floor-plan-canvas" />,
}));

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  });
  return ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
}

function switchToListTab() {
  const tab = screen.getByRole("tab", { name: /danh sách bàn/i });
  fireEvent.mouseDown(tab, { button: 0 });
  fireEvent.click(tab);
}

const baseBooking: ReservationResponse = {
  id: "booking-1234-5678",
  guestName: "Nguyễn Văn A",
  guestPhone: "0901234567",
  guestEmail: "vana@example.com",
  partySize: 8,
  reservationDate: "2026-11-25",
  reservationTime: "18:30:00",
  status: "PENDING_HOLD",
  zoneId: "zone-1",
  zoneName: "Khu A",
  zoneIsVip: false,
  tableId: null,
  tableNumber: null,
  depositAmount: 0,
  createdAt: "2026-11-25T10:00:00Z",
};

describe("ConfirmBookingDialog shared seating merge", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mutateAsyncMock.mockResolvedValue({});
    vi.spyOn(floorPlanApi, "getAll").mockResolvedValue([]);
  });

  it("allows merging partially occupied tables based on availableSeats rather than raw capacity", async () => {
    const tables: AvailableTable[] = [
      {
        id: "t1",
        tableNumber: "01",
        capacity: 10,
        availableSeats: 3,
        zoneId: "zone-1",
        zoneName: "Khu A",
        zoneIsVip: false,
        status: "AVAILABLE",
      },
      {
        id: "t2",
        tableNumber: "02",
        capacity: 10,
        availableSeats: 6,
        zoneId: "zone-1",
        zoneName: "Khu A",
        zoneIsVip: false,
        status: "AVAILABLE",
      },
    ];
    vi.spyOn(reservationApi, "getAvailableTables").mockResolvedValue(tables);

    const onOpenChange = vi.fn();
    render(
      <ConfirmBookingDialog booking={baseBooking} open={true} onOpenChange={onOpenChange} />,
      { wrapper: createWrapper() }
    );

    // Switch to table list tab
    switchToListTab();

    await waitFor(() => {
      expect(screen.getByText("Bàn 01")).toBeInTheDocument();
      expect(screen.getByText("Bàn 02")).toBeInTheDocument();
    });

    // Select Table 1 (3 availableSeats) and Table 2 (6 availableSeats)
    fireEvent.click(screen.getByText("Bàn 01"));
    fireEvent.click(screen.getByText("Bàn 02"));

    expect(toast.warning).not.toHaveBeenCalled();

    const confirmBtn = screen.getByRole("button", { name: /xác nhận & gán bàn/i });
    expect(confirmBtn).toBeEnabled();
    fireEvent.click(confirmBtn);

    await waitFor(() => {
      expect(mutateAsyncMock).toHaveBeenCalledWith({
        id: baseBooking.id,
        payload: {
          status: "CONFIRMED",
          tableId: "t1",
          tableIds: ["t1", "t2"],
        },
      });
    });
    expect(onOpenChange).toHaveBeenCalledWith(false);
  });

  it("warns and blocks selecting a second table when a single table has enough availableSeats", async () => {
    const smallBooking: ReservationResponse = { ...baseBooking, partySize: 4 };
    const tables: AvailableTable[] = [
      {
        id: "t1",
        tableNumber: "01",
        capacity: 6,
        availableSeats: 6,
        zoneId: "zone-1",
        zoneName: "Khu A",
        zoneIsVip: false,
        status: "AVAILABLE",
      },
      {
        id: "t2",
        tableNumber: "02",
        capacity: 6,
        availableSeats: 6,
        zoneId: "zone-1",
        zoneName: "Khu A",
        zoneIsVip: false,
        status: "AVAILABLE",
      },
    ];
    vi.spyOn(reservationApi, "getAvailableTables").mockResolvedValue(tables);

    render(
      <ConfirmBookingDialog booking={smallBooking} open={true} onOpenChange={vi.fn()} />,
      { wrapper: createWrapper() }
    );

    switchToListTab();

    await waitFor(() => {
      expect(screen.getByText("Bàn 01")).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText("Bàn 01"));
    fireEvent.click(screen.getByText("Bàn 02"));

    expect(toast.warning).toHaveBeenCalled();

    const confirmBtn = screen.getByRole("button", { name: /xác nhận & gán bàn/i });
    fireEvent.click(confirmBtn);

    await waitFor(() => {
      expect(mutateAsyncMock).toHaveBeenCalledWith({
        id: smallBooking.id,
        payload: {
          status: "CONFIRMED",
          tableId: "t1",
          tableIds: ["t1"],
        },
      });
    });
  });

  it("allows merging 3 partially occupied tables when none is redundant by availableSeats", async () => {
    const largeBooking: ReservationResponse = { ...baseBooking, partySize: 12 };
    const tables: AvailableTable[] = [
      {
        id: "ta",
        tableNumber: "A1",
        capacity: 10,
        availableSeats: 4,
        zoneId: "zone-1",
        zoneName: "Khu A",
        zoneIsVip: false,
        status: "AVAILABLE",
      },
      {
        id: "tb",
        tableNumber: "B1",
        capacity: 10,
        availableSeats: 4,
        zoneId: "zone-1",
        zoneName: "Khu A",
        zoneIsVip: false,
        status: "AVAILABLE",
      },
      {
        id: "tc",
        tableNumber: "C1",
        capacity: 10,
        availableSeats: 4,
        zoneId: "zone-1",
        zoneName: "Khu A",
        zoneIsVip: false,
        status: "AVAILABLE",
      },
    ];
    vi.spyOn(reservationApi, "getAvailableTables").mockResolvedValue(tables);

    const onOpenChange = vi.fn();
    render(
      <ConfirmBookingDialog booking={largeBooking} open={true} onOpenChange={onOpenChange} />,
      { wrapper: createWrapper() }
    );

    switchToListTab();

    await waitFor(() => {
      expect(screen.getByText("Bàn A1")).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText("Bàn A1"));
    fireEvent.click(screen.getByText("Bàn B1"));
    fireEvent.click(screen.getByText("Bàn C1"));

    expect(toast.warning).not.toHaveBeenCalled();

    const confirmBtn = screen.getByRole("button", { name: /xác nhận & gán bàn/i });
    expect(confirmBtn).toBeEnabled();
    fireEvent.click(confirmBtn);

    await waitFor(() => {
      expect(mutateAsyncMock).toHaveBeenCalledWith({
        id: largeBooking.id,
        payload: {
          status: "CONFIRMED",
          tableId: "ta",
          tableIds: ["ta", "tb", "tc"],
        },
      });
    });
    expect(toast.error).not.toHaveBeenCalled();
    expect(onOpenChange).toHaveBeenCalledWith(false);
  });
});
