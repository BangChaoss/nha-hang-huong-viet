import * as React from "react";
import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { Table, TableBody } from "@/components/ui/table";
import { StaffBookingRow } from "../components/staff-booking-row";
import type { ReservationResponse } from "../types/reservation.types";

const baseBooking: ReservationResponse = {
  id: "res-row-1234",
  guestName: "Lê Minh",
  guestPhone: "0918889999",
  guestEmail: "leminh@example.com",
  reservationDate: new Date().toISOString().split("T")[0]!,
  reservationTime: "12:00:00",
  partySize: 2,
  zoneId: "zone-1",
  zoneName: "Khu A",
  zoneIsVip: false,
  tableId: "t1",
  tableNumber: "01 + 02",
  mergedTableIds: "t1,t2",
  status: "CONFIRMED",
  depositAmount: 0,
  createdAt: "2026-10-25T10:00:00Z",
};

function renderRow(booking: ReservationResponse) {
  return render(
    <Table>
      <TableBody>
        <StaffBookingRow
          booking={booking}
          onOpenConfirm={vi.fn()}
          onCheckIn={vi.fn()}
          onCancel={vi.fn()}
        />
      </TableBody>
    </Table>
  );
}

describe("StaffBookingRow Component", () => {
  it("renders booking info and check-in button without reference errors", () => {
    // Current time reservation (allowed check-in)
    const now = new Date();
    const timeStr = `${String(now.getHours()).padStart(2, "0")}:${String(now.getMinutes()).padStart(2, "0")}:00`;
    const todayStr = now.toISOString().split("T")[0]!;

    renderRow({
      ...baseBooking,
      reservationDate: todayStr,
      reservationTime: timeStr,
    });

    expect(screen.getByText("Lê Minh")).toBeInTheDocument();
    expect(screen.getByText("0918889999")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Check-in/i })).toBeEnabled();
  });

  it("disables check-in when too early (> 30 mins before reservation)", () => {
    const futureDate = new Date(Date.now() + 2 * 60 * 60 * 1000);
    const timeStr = `${String(futureDate.getHours()).padStart(2, "0")}:${String(futureDate.getMinutes()).padStart(2, "0")}:00`;
    const dateStr = futureDate.toISOString().split("T")[0]!;

    renderRow({
      ...baseBooking,
      reservationDate: dateStr,
      reservationTime: timeStr,
    });

    const checkInBtn = screen.getByRole("button", { name: /Check-in/i });
    expect(checkInBtn).toBeDisabled();
    expect(screen.getByText(/Chưa đến giờ check-in/i)).toBeInTheDocument();
  });

  it("disables check-in when too late (> 30 mins after reservation)", () => {
    const pastDate = new Date(Date.now() - 2 * 60 * 60 * 1000);
    const timeStr = `${String(pastDate.getHours()).padStart(2, "0")}:${String(pastDate.getMinutes()).padStart(2, "0")}:00`;
    const dateStr = pastDate.toISOString().split("T")[0]!;

    renderRow({
      ...baseBooking,
      reservationDate: dateStr,
      reservationTime: timeStr,
    });

    const checkInBtn = screen.getByRole("button", { name: /Check-in/i });
    expect(checkInBtn).toBeDisabled();
    expect(screen.getByText(/Quá giờ nhận bàn/i)).toBeInTheDocument();
  });
});
