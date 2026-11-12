import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { StaffBookingCard } from "../components/staff-booking-card";
import { StaffBookingDashboard } from "../components/staff-booking-dashboard";
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

describe("StaffBookingCard", () => {
  it("renders pending booking with prominent 'Xác nhận & Gán bàn' action", () => {
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
          json: () => Promise.resolve([mockConfirmedBooking]),
        });
      }
      return Promise.resolve({
        ok: true,
        status: 200,
        json: () => Promise.resolve([]),
      });
    });
  });

  it("renders navigation tabs and action metrics", async () => {
    render(
      <QueryClientProvider client={queryClient}>
        <StaffBookingDashboard />
      </QueryClientProvider>,
    );

    expect(screen.getByText("Quản Lý Đặt Bàn")).toBeDefined();
    expect(screen.getAllByText("Cần xử lý ngay").length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText("Hôm nay").length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText("Sắp tới").length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText("Tra cứu theo ngày")).toBeDefined();

    // Waiting for pending card
    const guestName = await screen.findByText("Nguyễn Văn A");
    expect(guestName).toBeDefined();
  });
});
