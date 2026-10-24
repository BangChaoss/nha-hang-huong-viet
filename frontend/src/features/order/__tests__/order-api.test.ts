import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import { kitchenApi, orderApi } from "../api/order-api";
import type { CreateOrderRequest } from "../types/order.types";

describe("orderApi", () => {
  const originalFetch = global.fetch;

  beforeEach(() => {
    vi.restoreAllMocks();
  });

  afterEach(() => {
    global.fetch = originalFetch;
  });

  const mockOk = (body: unknown, status = 200) => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status,
      json: vi.fn().mockResolvedValue(body),
    } as unknown as Response);
  };

  it("create() should POST /api/orders with correct payload", async () => {
    const payload: CreateOrderRequest = {
      tableId: "table-1",
      items: [{ menuItemId: "dish-1", quantity: 2, note: "Ít cay" }],
      submit: true,
    };

    const mockResponse = {
      id: "order-1",
      tableId: "table-1",
      tableNumber: "Bàn 01",
      staffId: "staff-1",
      orderType: "DINE_IN",
      status: "IN_PROGRESS",
      createdAt: "2026-10-23T10:00:00Z",
      closedAt: null,
      items: [],
    };

    mockOk(mockResponse, 201);

    const result = await orderApi.create(payload);

    expect(global.fetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/orders"),
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify(payload),
        headers: expect.objectContaining({ "Content-Type": "application/json" }),
      }),
    );
    expect(result.id).toBe("order-1");
    expect(result.tableNumber).toBe("Bàn 01");
  });

  it("getActiveByTable() should GET /api/orders/table/{id}/active", async () => {
    mockOk({ id: "order-2" });

    await orderApi.getActiveByTable("table-9");

    expect(global.fetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/orders/table/table-9/active"),
      expect.objectContaining({
        headers: expect.objectContaining({ "Content-Type": "application/json" }),
      }),
    );
  });

  it("list() should append tableId and status filters", async () => {
    mockOk([]);

    await orderApi.list({ tableId: "table-3", status: "IN_PROGRESS" });

    const calls = (global.fetch as ReturnType<typeof vi.fn>).mock.calls;
    const calledUrl = String(calls[0]?.[0]);
    expect(calledUrl).toContain("/api/orders?");
    expect(calledUrl).toContain("tableId=table-3");
    expect(calledUrl).toContain("status=IN_PROGRESS");
  });

  it("kitchenApi.listTickets() should GET /api/kitchen/tickets", async () => {
    mockOk([]);

    await kitchenApi.listTickets();

    expect(global.fetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/kitchen/tickets"),
      expect.objectContaining({
        headers: expect.objectContaining({ "Content-Type": "application/json" }),
      }),
    );
  });

  it("kitchenApi.updateStatus() should PATCH the ticket status", async () => {
    mockOk({ id: "ticket-1", status: "PREPARING" });

    await kitchenApi.updateStatus("ticket-1", "PREPARING");

    expect(global.fetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/kitchen/tickets/ticket-1/status"),
      expect.objectContaining({
        method: "PATCH",
        body: JSON.stringify({ status: "PREPARING" }),
      }),
    );
  });

  it("should reject with the server message when the response is not ok", async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 409,
      json: vi.fn().mockResolvedValue({ message: "Món Phở Bò hiện không còn sẵn." }),
    } as unknown as Response);

    await expect(
      orderApi.create({ tableId: "table-1", items: [{ menuItemId: "dish-1", quantity: 1 }] }),
    ).rejects.toThrow("Món Phở Bò hiện không còn sẵn.");
  });
});
