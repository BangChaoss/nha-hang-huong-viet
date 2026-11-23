import { describe, it, expect } from "vitest";
import { consolidateTables } from "@/components/hv/StaffPOS";
import type { TableResponse } from "@/lib/table-api";

describe("StaffPOS consolidateTables", () => {
  it("gom các bàn modular con (A, B) thành một bàn gốc duy nhất với tổng số ghế", () => {
    const rawTables: TableResponse[] = [
      {
        id: "t-03a",
        tableNumber: "Bàn 03A",
        capacity: 2,
        status: "AVAILABLE",
        zoneId: "zone-1",
        zoneName: "Trong Nhà",
        zoneIsVip: false,
      },
      {
        id: "t-03b",
        tableNumber: "Bàn 03B",
        capacity: 4,
        status: "AVAILABLE",
        zoneId: "zone-1",
        zoneName: "Trong Nhà",
        zoneIsVip: false,
      },
      {
        id: "t-01",
        tableNumber: "Bàn 01",
        capacity: 4,
        status: "AVAILABLE",
        zoneId: "zone-1",
        zoneName: "Trong Nhà",
        zoneIsVip: false,
      },
    ];

    const result = consolidateTables(rawTables);

    expect(result).toHaveLength(2);

    // Bàn 03 gộp
    const table03 = result.find((t) => t.tableNumber === "Bàn 03");
    expect(table03).toBeDefined();
    expect(table03?.capacity).toBe(6);
    expect(table03?.status).toBe("AVAILABLE");
    expect(table03?.subTables).toHaveLength(2);

    // Bàn 01 giữ nguyên
    const table01 = result.find((t) => t.tableNumber === "Bàn 01");
    expect(table01).toBeDefined();
    expect(table01?.capacity).toBe(4);
    expect(table01?.subTables).toHaveLength(1);
  });

  it("ưu tiên trạng thái OCCUPIED và tính chính xác ghế trống khi có bàn con đang phục vụ", () => {
    const rawTables: TableResponse[] = [
      {
        id: "t-05a",
        tableNumber: "Bàn 05A",
        capacity: 4,
        status: "OCCUPIED",
        availableSeats: 2,
        zoneId: "zone-1",
        zoneName: "Trong Nhà",
        zoneIsVip: false,
      },
      {
        id: "t-05b",
        tableNumber: "Bàn 05B",
        capacity: 4,
        status: "AVAILABLE",
        availableSeats: null,
        zoneId: "zone-1",
        zoneName: "Trong Nhà",
        zoneIsVip: false,
      },
    ];

    const result = consolidateTables(rawTables);

    expect(result).toHaveLength(1);
    const table05 = result[0]!;
    expect(table05.tableNumber).toBe("Bàn 05");
    expect(table05.capacity).toBe(8);
    expect(table05.status).toBe("OCCUPIED");
    // Ghế trống tổng = 2 (từ 05A) + 4 (từ 05B còn trống) = 6
    expect(table05.availableSeats).toBe(6);
  });

  it("gộp đúng trạng thái RESERVED khi có bàn con được đặt trước", () => {
    const rawTables: TableResponse[] = [
      {
        id: "t-07a",
        tableNumber: "Bàn 07A",
        capacity: 2,
        status: "RESERVED",
        zoneId: "zone-garden",
        zoneName: "Sân Vườn",
        zoneIsVip: false,
      },
      {
        id: "t-07b",
        tableNumber: "Bàn 07B",
        capacity: 4,
        status: "AVAILABLE",
        zoneId: "zone-garden",
        zoneName: "Sân Vườn",
        zoneIsVip: false,
      },
    ];

    const result = consolidateTables(rawTables);

    expect(result).toHaveLength(1);
    const table07 = result[0]!;
    expect(table07.tableNumber).toBe("Bàn 07");
    expect(table07.capacity).toBe(6);
    expect(table07.status).toBe("RESERVED");
  });
});
