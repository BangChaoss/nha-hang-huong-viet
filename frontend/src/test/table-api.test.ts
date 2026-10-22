import { describe, expect, it } from "vitest";
import { STATUS_COLORS, type TableStatus } from "@/lib/table-api";

describe("table-api and status configurations", () => {
  it("should define all 5 required business table statuses", () => {
    const requiredStatuses: TableStatus[] = [
      "AVAILABLE",
      "RESERVED",
      "OCCUPIED",
      "CLEANING",
      "MAINTENANCE",
    ];

    for (const status of requiredStatuses) {
      expect(STATUS_COLORS[status]).toBeDefined();
      expect(STATUS_COLORS[status].label).toBeTruthy();
      expect(STATUS_COLORS[status].bg).toBeTruthy();
      expect(STATUS_COLORS[status].badgeCls).toBeTruthy();
      expect(STATUS_COLORS[status].dotCls).toBeTruthy();
    }
  });

  it("should have correct human-readable Vietnamese labels", () => {
    expect(STATUS_COLORS.AVAILABLE.label).toBe("Trống");
    expect(STATUS_COLORS.RESERVED.label).toBe("Đã đặt");
    expect(STATUS_COLORS.OCCUPIED.label).toBe("Đang ăn");
    expect(STATUS_COLORS.CLEANING.label).toBe("Dọn dẹp");
    expect(STATUS_COLORS.MAINTENANCE.label).toBe("Bảo trì");
  });
});
