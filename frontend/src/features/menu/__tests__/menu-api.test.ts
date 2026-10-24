import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import { adminMenuApi, menuApi } from "../api/menu-api";

describe("menuApi / adminMenuApi", () => {
  const originalFetch = global.fetch;

  beforeEach(() => {
    vi.restoreAllMocks();
  });

  afterEach(() => {
    global.fetch = originalFetch;
  });

  it("getOverview() should GET /api/menu and return the parsed body", async () => {
    const mockOverview = {
      categories: [
        {
          id: "cat-1",
          categoryName: "Khai vị",
          displayOrder: 1,
          items: [
            {
              id: "item-1",
              categoryId: "cat-1",
              categoryName: "Khai vị",
              itemName: "Gà hấp mật ong",
              description: "Gà ta hấp mật ong",
              currentPrice: 185000,
              imageUrl: "https://example.com/ga.jpg",
              isAvailable: true,
            },
          ],
        },
      ],
    };

    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: vi.fn().mockResolvedValue(mockOverview),
    } as unknown as Response);

    const result = await menuApi.getOverview();

    expect(global.fetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/menu"),
      expect.objectContaining({
        headers: expect.objectContaining({
          "Content-Type": "application/json",
        }),
      }),
    );
    expect(result.categories).toHaveLength(1);
    expect(result.categories[0]?.categoryName).toBe("Khai vị");
    expect(result.categories[0]?.items[0]?.itemName).toBe("Gà hấp mật ong");
  });

  it("getItem() should GET /api/menu/:id", async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: vi.fn().mockResolvedValue({ id: "item-1", itemName: "Gà hấp mật ong" }),
    } as unknown as Response);

    const result = await menuApi.getItem("item-1");

    expect(global.fetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/menu/item-1"),
      expect.any(Object),
    );
    expect(result.id).toBe("item-1");
  });

  it("setAvailability() should PATCH /api/admin/menu-items/:id/availability", async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: vi.fn().mockResolvedValue({ id: "item-1", isAvailable: false }),
    } as unknown as Response);

    const result = await adminMenuApi.setAvailability("item-1", false);

    expect(global.fetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/admin/menu-items/item-1/availability"),
      expect.objectContaining({
        method: "PATCH",
        body: JSON.stringify({ isAvailable: false }),
      }),
    );
    expect(result.isAvailable).toBe(false);
  });

  it("removeItem() should DELETE /api/admin/menu-items/:id and not parse a 204 body", async () => {
    const jsonMock = vi.fn();
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 204,
      json: jsonMock,
    } as unknown as Response);

    const result = await adminMenuApi.removeItem("item-1");

    expect(global.fetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/admin/menu-items/item-1"),
      expect.objectContaining({
        method: "DELETE",
      }),
    );
    expect(jsonMock).not.toHaveBeenCalled();
    expect(result).toBeUndefined();
  });

  it("listItems() should append filter query params", async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: vi.fn().mockResolvedValue([]),
    } as unknown as Response);

    await adminMenuApi.listItems({ categoryId: "cat-1", q: "gà", available: false });

    expect(global.fetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/admin/menu-items?categoryId=cat-1&q=g%C3%A0&available=false"),
      expect.any(Object),
    );
  });

  it("should extract error message from API response when request fails", async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 409,
      json: vi.fn().mockResolvedValue({ message: "Danh mục còn món ăn, không thể xóa" }),
    } as unknown as Response);

    await expect(adminMenuApi.removeCategory("cat-1")).rejects.toThrow(
      "Danh mục còn món ăn, không thể xóa",
    );
  });
});
