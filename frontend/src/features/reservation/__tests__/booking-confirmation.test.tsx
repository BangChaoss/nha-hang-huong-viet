import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { BookingConfirmation } from "../components/booking-confirmation";
import { reservationApi } from "../api/reservation-api";
import type { ReservationResponse } from "../types/reservation.types";

describe("BookingConfirmation", () => {
  const sampleReservation: ReservationResponse = {
    id: "24589d89-9830-4e3e-86fe-2b23ba4c767f",
    guestName: "Nguyễn Văn A",
    guestPhone: "0901234567",
    guestEmail: "nguyenvana@example.com",
    partySize: 4,
    reservationDate: "2026-11-20",
    reservationTime: "18:30",
    specialRequests: "Không có",
    status: "PENDING_HOLD",
    zoneId: "zone-1",
    zoneName: "Tầng 1",
    zoneIsVip: false,
    tableId: null,
    tableNumber: null,
    mergedTableIds: null,
    depositAmount: 0,
    cancellationReason: null,
    createdAt: "2026-11-10T12:00:00",
  };

  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("renders formatted booking code HV-XXXXXXXX on the confirmation card", () => {
    render(<BookingConfirmation reservation={sampleReservation} onReset={() => {}} />);

    // Expected code: HV-24589D89
    expect(screen.getByText("HV-24589D89")).toBeDefined();
    expect(screen.getByText("Mã tra cứu đặt bàn")).toBeDefined();
    expect(screen.getByText("Nguyễn Văn A")).toBeDefined();
  });

  it("copies full booking code HV-XXXXXXXX including prefix to clipboard", async () => {
    const writeTextMock = vi.fn().mockResolvedValue(undefined);
    Object.assign(navigator, {
      clipboard: {
        writeText: writeTextMock,
      },
    });

    render(<BookingConfirmation reservation={sampleReservation} onReset={() => {}} />);

    const copyBtn = screen.getByRole("button", { name: /sao chép mã/i });
    fireEvent.click(copyBtn);

    expect(writeTextMock).toHaveBeenCalledWith("HV-24589D89");
  });

  it("displays 250.000đ deposit copy for VIP PENDING_DEPOSIT and sends returnPath='book'", async () => {
    const vipReservation: ReservationResponse = {
      ...sampleReservation,
      status: "PENDING_DEPOSIT",
      zoneIsVip: true,
      zoneName: "Phòng VIP",
      depositAmount: 250000,
    };

    const depositSpy = vi
      .spyOn(reservationApi, "createDepositPayment")
      .mockResolvedValue({ paymentUrl: "" });

    render(<BookingConfirmation reservation={vipReservation} onReset={() => {}} />);

    expect(screen.getAllByText(/250\.000đ/).length).toBeGreaterThanOrEqual(2);
    expect(screen.getByText(/không trừ vào hóa đơn món ăn/i)).toBeInTheDocument();
    const depositBtn = screen.getByRole("button", { name: /Thanh Toán Phí & Cọc VIP 250\.000đ qua VNPay/i });
    expect(depositBtn).toBeInTheDocument();

    fireEvent.click(depositBtn);

    await waitFor(() => {
      expect(depositSpy).toHaveBeenCalledWith(vipReservation.id, "book");
    });
  });
});
