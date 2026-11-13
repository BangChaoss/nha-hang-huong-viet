import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { StaffBookingCard } from "../components/staff-booking-card";
import { StaffBookingRow } from "../components/staff-booking-row";
import { StaffBookingDashboard } from "../components/staff-booking-dashboard";
import { Table, TableBody } from "@/components/ui/table";
import type { ReservationResponse } from "../types/reservation.types";

const mockPendingBooking: ReservationResponse = {
  id: "24589d89-9830-4e3e-86fe-2b23ba4c767f",
  guestName: "Nguyễn Văn A",
  guestPhone: "0901234567",
  guestEmail: "vana@gmail.com",
  partySize: 4,
  reservationDate: "2026-11-12",
  reservationTime: "18:30:00",
  status: "PENDING_HOLD",
  zoneId: "zone-1",
  zoneName: "Khu vực Sân Vườn",
  zoneIsVip: false,
  tableId: null,
  tableNumber: null,
  depositAmount: 0,
  specialRequests: "Gần lối ra",
  createdAt: "2026-11-10T10:00:00Z",
};

const mockConfirmedBooking: ReservationResponse = {
  id: "a1b2c3d4-1111-2222-3333-444455556666",
  guestName: "Trần Thị B",
  guestPhone: "0988776655",
  guestEmail: "thib@gmail.com",
  partySize: 2,
  reservationDate: new Date().toISOString().slice(0, 10),
  reservationTime: "19:00:00",
  status: "CONFIRMED",
  zoneId: "zone-2",
  zoneName: "Phòng VIP",
  zoneIsVip: true,
  tableId: "tbl-vip-1",
  tableNumber: "VIP1",
  depositAmount: 100000,
  createdAt: "2026-11-10T11:00:00Z",
};

const mockArrivedBooking: ReservationResponse = {
  id: "c3d4e5f6-7777-8888-9999-000011112222",
  guestName: "Lê Văn C",
  guestPhone: "0911223344",
  guestEmail: "vanc@gmail.com",
  partySize: 3,
  reservationDate: new Date().toISOString().slice(0, 10),
  reservationTime: "19:30:00",
  status: "ARRIVED",
  zoneId: "zone-1",
  zoneName: "Khu vực Sân Vườn",
  zoneIsVip: false,
  tableId: "tbl-garden-1",
  tableNumber: "06",
  depositAmount: 0,
  createdAt: "2026-11-10T12:00:00Z",
};

const mockCancelledBooking: ReservationResponse = {
  id: "d4e5f6a7-8888-9999-0000-111122223333",
  guestName: "Phạm Văn D",
  guestPhone: "0922334455",
  partySize: 2,
  reservationDate: "2026-11-20",
  reservationTime: "20:00:00",
  status: "CANCELLED",
  cancellationReason: "Khách bận việc",
  depositAmount: 0,
  createdAt: "2026-11-10T13:00:00Z",
};

describe("StaffBookingCard", () => {
  it("renders pending booking with both 'Hủy' and 'Xác nhận & Gán bàn' action buttons", () => {
    const onOpenConfirm = vi.fn();
    const onCheckIn = vi.fn();
    const onCancel = vi.fn();

    render(
      <StaffBookingCard
        booking={mockPendingBooking}
        onOpenConfirm={onOpenConfirm}
        onCheckIn={onCheckIn}
        onCancel={onCancel}
      />,
    );

    expect(screen.getByText("HV-24589D89")).toBeDefined();
    expect(screen.getByText("Nguyễn Văn A")).toBeDefined();
    expect(screen.getByText("0901234567")).toBeDefined();
    expect(screen.getByText("4 khách")).toBeDefined();
    expect(screen.getByText("Chưa gán bàn")).toBeDefined();
    expect(screen.getByText("Chờ xác nhận")).toBeDefined();

    const cancelBtn = screen.getByRole("button", { name: /Hủy/i });
    expect(cancelBtn).toBeDefined();
    fireEvent.click(cancelBtn);
    expect(onCancel).toHaveBeenCalledWith(mockPendingBooking);

    const confirmBtn = screen.getByRole("button", { name: /Xác nhận & Gán bàn/i });
    expect(confirmBtn).toBeDefined();
    fireEvent.click(confirmBtn);
    expect(onOpenConfirm).toHaveBeenCalledWith(mockPendingBooking);
  });

  it("renders confirmed booking with table number and Check-in / Cancel buttons", () => {
    const onOpenConfirm = vi.fn();
    const onCheckIn = vi.fn();
    const onCancel = vi.fn();

    render(
      <StaffBookingCard
        booking={mockConfirmedBooking}
        onOpenConfirm={onOpenConfirm}
        onCheckIn={onCheckIn}
        onCancel={onCancel}
      />,
    );

    expect(screen.getByText("HV-A1B2C3D4")).toBeDefined();
    expect(screen.getByText("Trần Thị B")).toBeDefined();
    expect(screen.getByText("Bàn VIP1")).toBeDefined();
    expect(screen.getByText("Đã xác nhận")).toBeDefined();
    expect(screen.getByRole("button", { name: /Check-in/i })).toBeDefined();
    expect(screen.getByRole("button", { name: /Hủy/i })).toBeDefined();
  });
});

describe("StaffBookingRow", () => {
  it("renders pending booking with 'Hủy' button on the left of 'Xác nhận & Gán bàn'", () => {
    const onOpenConfirm = vi.fn();
    const onCheckIn = vi.fn();
    const onCancel = vi.fn();

    render(
      <Table>
        <TableBody>
          <StaffBookingRow
            booking={mockPendingBooking}
            onOpenConfirm={onOpenConfirm}
            onCheckIn={onCheckIn}
            onCancel={onCancel}
          />
        </TableBody>
      </Table>,
    );

    expect(screen.getByText("HV-24589D89")).toBeDefined();
    const cancelBtn = screen.getByRole("button", { name: /Hủy/i });
    expect(cancelBtn).toBeDefined();
    fireEvent.click(cancelBtn);
    expect(onCancel).toHaveBeenCalledWith(mockPendingBooking);

    const confirmBtn = screen.getByRole("button", { name: /Xác nhận & Gán bàn/i });
    expect(confirmBtn).toBeDefined();
    fireEvent.click(confirmBtn);
    expect(onOpenConfirm).toHaveBeenCalledWith(mockPendingBooking);
  });
});

describe("StaffBookingDashboard", () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
      },
    });

    vi.restoreAllMocks();
    global.fetch = vi.fn().mockImplementation((url: string) => {
      if (url.includes("scope=pending")) {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: () => Promise.resolve([mockPendingBooking]),
        });
      }
      if (url.includes("scope=upcoming")) {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: () =>
            Promise.resolve([
              mockConfirmedBooking,
              mockArrivedBooking,
              mockCancelledBooking,
            ]),
        });
      }
      return Promise.resolve({
        ok: true,
        status: 200,
        json: () => Promise.resolve([]),
      });
    });
  });

  it("renders metric cards and single 'Tra cứu theo ngày' tab without redundant filter buttons", async () => {
    render(
      <QueryClientProvider client={queryClient}>
        <StaffBookingDashboard />
      </QueryClientProvider>,
    );

    expect(screen.getByText("Quản Lý Đặt Bàn")).toBeDefined();
    // 4 metric cards at top
    expect(screen.getByText("Cần xử lý ngay")).toBeDefined();
    expect(screen.getByText("Hôm nay")).toBeDefined();
    expect(screen.getByText("Sắp tới")).toBeDefined();
    expect(screen.getByText("Đã đến nhận bàn hôm nay")).toBeDefined();

    // The single tab button below
    expect(screen.getByText("Tra cứu theo ngày")).toBeDefined();

    // Verify there are no duplicate tab buttons below
    const buttons = screen.getAllByRole("button");
    const tabButtonTexts = buttons.map((b) => b.textContent?.trim());
    expect(tabButtonTexts.filter((t) => t?.includes("Cần xử lý ngay")).length).toBe(0);
    expect(tabButtonTexts.filter((t) => t?.includes("Sắp tới")).length).toBe(0);

    // Waiting for pending card
    const guestName = await screen.findByText("Nguyễn Văn A");
    expect(guestName).toBeDefined();
  });

  it("filters only ARRIVED bookings when clicking 'Đã đến nhận bàn hôm nay' card", async () => {
    render(
      <QueryClientProvider client={queryClient}>
        <StaffBookingDashboard />
      </QueryClientProvider>,
    );

    // Click 'Đã đến nhận bàn hôm nay' card
    const arrivedCard = screen.getByText("Đã đến nhận bàn hôm nay").closest(".cursor-pointer");
    expect(arrivedCard).toBeDefined();
    fireEvent.click(arrivedCard!);

    // Should display arrived booking
    const arrivedGuest = await screen.findByText("Lê Văn C");
    expect(arrivedGuest).toBeDefined();

    // Should NOT display confirmed booking
    expect(screen.queryByText("Trần Thị B")).toBeNull();
  });

  it("excludes completed and cancelled bookings (ARRIVED, CANCELLED) in 'Sắp tới' tab", async () => {
    render(
      <QueryClientProvider client={queryClient}>
        <StaffBookingDashboard />
      </QueryClientProvider>,
    );

    // Click 'Sắp tới' card
    const upcomingCard = screen.getByText("Sắp tới").closest(".cursor-pointer");
    expect(upcomingCard).toBeDefined();
    fireEvent.click(upcomingCard!);

    // Should display confirmed booking
    const confirmedGuest = await screen.findByText("Trần Thị B");
    expect(confirmedGuest).toBeDefined();

    // Should NOT display arrived or cancelled booking in 'Sắp tới'
    expect(screen.queryByText("Lê Văn C")).toBeNull();
    expect(screen.queryByText("Phạm Văn D")).toBeNull();
  });
});
