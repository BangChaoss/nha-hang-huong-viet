import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import { reservationApi } from "../api/reservation-api";
import type { CreateReservationRequest } from "../types/reservation.types";

describe("reservationApi", () => {
  const originalFetch = global.fetch;

  beforeEach(() => {
    vi.restoreAllMocks();
  });

  afterEach(() => {
    global.fetch = originalFetch;
  });

  it("create() should POST /api/reservations with correct payload", async () => {
    const mockPayload: CreateReservationRequest = {
      guestName: "Trần Văn An",
      guestPhone: "0912345678",
      guestEmail: "an@example.com",
      reservationDate: "2026-11-01",
      reservationTime: "18:00",
      partySize: 4,
      zoneId: "zone-1",
      specialRequests: "Bàn gần cửa sổ",
    };

    const mockResponse = {
      id: "res-123",
      guestName: "Trần Văn An",
      guestPhone: "0912345678",
      guestEmail: "an@example.com",
      partySize: 4,
      reservationDate: "2026-11-01",
      reservationTime: "18:00",
      specialRequests: "Bàn gần cửa sổ",
      status: "PENDING_HOLD",
      zoneId: "zone-1",
      zoneName: "Sân Vườn",
      zoneIsVip: false,
      tableId: null,
      tableNumber: null,
      depositAmount: 0,
      createdAt: "2026-10-23T10:00:00Z",
    };

    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 201,
      json: vi.fn().mockResolvedValue(mockResponse),
    } as unknown as Response);

    const result = await reservationApi.create(mockPayload);

    expect(global.fetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/reservations"),
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify(mockPayload),
        headers: expect.objectContaining({
          "Content-Type": "application/json",
        }),
      }),
    );
    expect(result.id).toBe("res-123");
    expect(result.status).toBe("PENDING_HOLD");
  });

  it("checkAvailability() should query /api/reservations/availability with date and partySize params", async () => {
    const mockAvailability = [
      {
        zoneId: "z1",
        zoneName: "Trong Nhà",
        isVip: false,
        totalCapacity: 40,
        timeSlots: [
          {
            time: "11:00",
            label: "11:00",
            confirmedSeats: 0,
            remainingCapacity: 40,
            available: true,
          },
        ],
      },
    ];

    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: vi.fn().mockResolvedValue(mockAvailability),
    } as unknown as Response);

    const result = await reservationApi.checkAvailability("2026-11-01", 6);

    expect(global.fetch).toHaveBeenCalledWith(
      expect.stringMatching(/\/api\/reservations\/availability\?date=2026-11-01&partySize=6/),
      expect.any(Object),
    );
    expect(result).toHaveLength(1);
    expect(result[0]?.zoneName).toBe("Trong Nhà");
  });

  it("guestLookup() should query /api/reservations/guest-lookup with encoded phone number", async () => {
    const mockBookings = [
      {
        id: "res-guest-1",
        guestName: "Lê Hoàng",
        guestPhone: "0987654321",
        status: "CONFIRMED",
        partySize: 2,
        reservationDate: "2026-11-05",
        reservationTime: "19:00",
        depositAmount: 0,
        createdAt: "2026-10-23T08:00:00Z",
      },
    ];

    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: vi.fn().mockResolvedValue(mockBookings),
    } as unknown as Response);

    const result = await reservationApi.guestLookup("0987654321");

    expect(global.fetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/reservations/guest-lookup?phone=0987654321"),
      expect.any(Object),
    );
    expect(result).toHaveLength(1);
    expect(result[0]?.id).toBe("res-guest-1");
  });

  it("cancelBooking() should POST /api/reservations/:id/cancel with reason", async () => {
    const mockCancelled = {
      id: "res-123",
      status: "CANCELLED",
      cancellationReason: "Thay đổi kế hoạch",
    };

    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: vi.fn().mockResolvedValue(mockCancelled),
    } as unknown as Response);

    const result = await reservationApi.cancelBooking("res-123", "Thay đổi kế hoạch");

    expect(global.fetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/reservations/res-123/cancel"),
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ reason: "Thay đổi kế hoạch" }),
      }),
    );
    expect(result.status).toBe("CANCELLED");
  });

  it("listByDate() should query /api/admin/reservations with date and optional status filter", async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: vi.fn().mockResolvedValue([]),
    } as unknown as Response);

    await reservationApi.listByDate("2026-10-23", "PENDING_HOLD");

    expect(global.fetch).toHaveBeenCalledWith(
      expect.stringMatching(/\/api\/admin\/reservations\?date=2026-10-23&status=PENDING_HOLD/),
      expect.any(Object),
    );
  });

  it("getAvailableTables() should query /api/admin/reservations/:id/available-tables", async () => {
    const mockTables = [
      { id: "tbl-1", tableNumber: "T1", capacity: 4, status: "AVAILABLE", zoneName: "Sân Vườn" },
    ];
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: vi.fn().mockResolvedValue(mockTables),
    } as unknown as Response);

    const result = await reservationApi.getAvailableTables("res-123");

    expect(global.fetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/admin/reservations/res-123/available-tables"),
      expect.any(Object),
    );
    expect(result).toHaveLength(1);
    expect(result[0]?.tableNumber).toBe("T1");
  });

  it("updateReservationStatus() should PATCH /api/admin/reservations/:id/status", async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: vi.fn().mockResolvedValue({ id: "res-123", status: "CONFIRMED" }),
    } as unknown as Response);

    const result = await reservationApi.updateReservationStatus("res-123", {
      status: "CONFIRMED",
      tableId: "tbl-1",
    });

    expect(global.fetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/admin/reservations/res-123/status"),
      expect.objectContaining({
        method: "PATCH",
        body: JSON.stringify({ status: "CONFIRMED", tableId: "tbl-1" }),
      }),
    );
    expect(result.status).toBe("CONFIRMED");
  });

  it("should extract error message from API response when request fails", async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 409,
      json: vi.fn().mockResolvedValue({ message: "Khung giờ đã đầy cho khu vực này" }),
    } as unknown as Response);

    await expect(
      reservationApi.create({
        guestName: "A",
        guestPhone: "0912345678",
        reservationDate: "2026-11-01",
        reservationTime: "18:00",
        partySize: 4,
        zoneId: "z1",
      }),
    ).rejects.toThrow("Khung giờ đã đầy cho khu vực này");
  });
});
