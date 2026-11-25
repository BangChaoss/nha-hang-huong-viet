import { describe, expect, it } from "vitest";
import {
  getStandardDimensions,
  computeCollisions,
  computeZoneBoxes,
  computeZoneCollisions,
  computeSeatPositions,
  getSeatColor,
  type LocalTableLayout,
  type ZoneBox,
} from "@/components/hv/FloorPlanCanvas";
import type { FloorPlanLayoutResponse } from "@/lib/table-api";

describe("FloorPlanCanvas Standard Table Dimensions", () => {
  it("should return correct standard rectangle dimensions based on capacity", () => {
    // 1-2 persons: 70 x 70px
    expect(getStandardDimensions(1, "RECTANGLE")).toEqual({ width: 70, height: 70 });
    expect(getStandardDimensions(2, "RECTANGLE")).toEqual({ width: 70, height: 70 });

    // 3-4 persons: 90 x 90px
    expect(getStandardDimensions(3, "RECTANGLE")).toEqual({ width: 90, height: 90 });
    expect(getStandardDimensions(4, "RECTANGLE")).toEqual({ width: 90, height: 90 });

    // 5+ persons: 140 x 90px
    expect(getStandardDimensions(6, "RECTANGLE")).toEqual({ width: 140, height: 90 });
    expect(getStandardDimensions(8, "RECTANGLE")).toEqual({ width: 140, height: 90 });
    expect(getStandardDimensions(12, "RECTANGLE")).toEqual({ width: 140, height: 90 });
  });

  it("should return correct standard circle dimensions based on capacity", () => {
    // 1-2 persons circle: diameter 70px (radius 35px)
    expect(getStandardDimensions(2, "CIRCLE")).toEqual({ width: 70, height: 70 });

    // 3-4 persons circle: diameter 90px (radius 45px)
    expect(getStandardDimensions(4, "CIRCLE")).toEqual({ width: 90, height: 90 });

    // 5+ persons circle: diameter 120px (radius 60px)
    expect(getStandardDimensions(6, "CIRCLE")).toEqual({ width: 120, height: 120 });
    expect(getStandardDimensions(10, "CIRCLE")).toEqual({ width: 120, height: 120 });
  });
});

describe("FloorPlanCanvas Table Collision Detection", () => {
  it("should detect overlapping tables correctly", () => {
    const layoutsMap: Record<string, LocalTableLayout> = {
      t1: { x: 50, y: 50, width: 90, height: 90, rotation: 0, shape: "RECTANGLE" },
      t2: { x: 80, y: 80, width: 90, height: 90, rotation: 0, shape: "RECTANGLE" }, // overlaps t1
      t3: { x: 300, y: 300, width: 90, height: 90, rotation: 0, shape: "RECTANGLE" }, // separated
    };

    const collisions = computeCollisions(layoutsMap, ["t1", "t2", "t3"]);
    expect(collisions.has("t1")).toBe(true);
    expect(collisions.has("t2")).toBe(true);
    expect(collisions.has("t3")).toBe(false);
  });

  it("should not flag adjacent non-overlapping tables as colliding", () => {
    const layoutsMap: Record<string, LocalTableLayout> = {
      t1: { x: 50, y: 50, width: 90, height: 90, rotation: 0, shape: "RECTANGLE" },
      t2: { x: 180, y: 50, width: 90, height: 90, rotation: 0, shape: "RECTANGLE" },
    };

    const collisions = computeCollisions(layoutsMap, ["t1", "t2"]);
    expect(collisions.size).toBe(0);
  });
});

describe("FloorPlanCanvas Zone Collision Detection", () => {
  const mockTable = (tableId: string, zoneName: string, x: number, y: number): FloorPlanLayoutResponse => ({
    id: `layout-${tableId}`,
    tableId,
    tableNumber: `Bàn ${tableId}`,
    capacity: 4,
    status: "AVAILABLE",
    zoneId: `zone-${zoneName}`,
    zoneName,
    zoneIsVip: zoneName.includes("VIP"),
    positionX: x,
    positionY: y,
    width: 90,
    height: 90,
    rotation: 0,
    shape: "RECTANGLE",
  });

  it("should compute zone bounding boxes with proper padding and header", () => {
    const visible = [
      mockTable("t1", "Trong Nhà", 100, 100),
      mockTable("t2", "Trong Nhà", 230, 100),
    ];
    const layoutsMap: Record<string, LocalTableLayout> = {
      t1: { x: 100, y: 100, width: 90, height: 90, rotation: 0, shape: "RECTANGLE" },
      t2: { x: 230, y: 100, width: 90, height: 90, rotation: 0, shape: "RECTANGLE" },
    };

    const boxes = computeZoneBoxes(layoutsMap, visible);
    expect(boxes.length).toBe(1);
    const box = boxes[0]!;
    expect(box.zoneName).toBe("Trong Nhà");
    // minX = 100, maxX = 320 -> boxX = 100 - 24 = 76, width = (320 - 100) + 48 = 268
    expect(box.boxX).toBe(76);
    expect(box.boxWidth).toBe(268);
    // minY = 100, maxY = 190 -> boxY = 100 - 24 - 30 = 46, height = (190 - 100) + 48 + 30 = 168
    expect(box.boxY).toBe(46);
    expect(box.boxHeight).toBe(168);
  });

  it("should detect colliding zone boundaries when two zones overlap", () => {
    const zoneA: ZoneBox = {
      zoneName: "Khu vực Trong Nhà",
      isVip: false,
      boxX: 50,
      boxY: 50,
      boxWidth: 300,
      boxHeight: 200,
      tableCount: 3,
    };
    const zoneB: ZoneBox = {
      zoneName: "Phòng VIP 1",
      isVip: true,
      boxX: 200, // Overlaps zoneA ([50..350])
      boxY: 150, // Overlaps zoneA ([50..250])
      boxWidth: 200,
      boxHeight: 200,
      tableCount: 2,
    };
    const zoneC: ZoneBox = {
      zoneName: "Sân Vườn",
      isVip: false,
      boxX: 600, // Safe distance
      boxY: 500,
      boxWidth: 200,
      boxHeight: 200,
      tableCount: 2,
    };

    const collisions = computeZoneCollisions([zoneA, zoneB, zoneC]);
    expect(collisions.has("Khu vực Trong Nhà")).toBe(true);
    expect(collisions.has("Phòng VIP 1")).toBe(true);
    expect(collisions.has("Sân Vườn")).toBe(false);
  });

  it("should return empty set when all zones are cleanly separated", () => {
    const zoneA: ZoneBox = {
      zoneName: "Khu vực 1",
      isVip: false,
      boxX: 50,
      boxY: 50,
      boxWidth: 200,
      boxHeight: 200,
      tableCount: 2,
    };
    const zoneB: ZoneBox = {
      zoneName: "Khu vực 2",
      isVip: false,
      boxX: 300,
      boxY: 50,
      boxWidth: 200,
      boxHeight: 200,
      tableCount: 2,
    };

    const collisions = computeZoneCollisions([zoneA, zoneB]);
    expect(collisions.size).toBe(0);
  });
});

describe("FloorPlanCanvas Seat Positions Computation", () => {
  it("should calculate single-row seat positions for 2-person small table without overflow", () => {
    const seats = computeSeatPositions(2, 60, 60, true);
    expect(seats.length).toBe(2);
    for (const seat of seats) {
      expect(seat.x).toBeGreaterThanOrEqual(0);
      expect(seat.x + seat.w).toBeLessThanOrEqual(60);
      expect(seat.y).toBeGreaterThanOrEqual(30);
      expect(seat.y + seat.h).toBeLessThanOrEqual(60);
    }
  });

  it("should calculate single-row seat positions for 4-person table inside 60px or 90px width", () => {
    const seats = computeSeatPositions(4, 90, 90, false);
    expect(seats.length).toBe(4);
    for (const seat of seats) {
      expect(seat.x).toBeGreaterThanOrEqual(0);
      expect(seat.x + seat.w).toBeLessThanOrEqual(90);
      expect(seat.y).toBeGreaterThanOrEqual(40);
      expect(seat.y + seat.h).toBeLessThanOrEqual(90);
    }
  });

  it("should calculate two-row seat positions for VIP 10-person table", () => {
    const seats = computeSeatPositions(10, 140, 90, false);
    expect(seats.length).toBe(10);
    // Row 1 (first 5 seats) have y = 47
    for (let i = 0; i < 5; i++) {
      expect(seats[i]!.y).toBe(47);
      expect(seats[i]!.x).toBeGreaterThanOrEqual(0);
      expect(seats[i]!.x + seats[i]!.w).toBeLessThanOrEqual(140);
    }
    // Row 2 (next 5 seats) have y = 61
    for (let i = 5; i < 10; i++) {
      expect(seats[i]!.y).toBe(61);
      expect(seats[i]!.x).toBeGreaterThanOrEqual(0);
      expect(seats[i]!.x + seats[i]!.w).toBeLessThanOrEqual(140);
    }
  });

  it("should determine red vs white seat colors matching assigned partySize", () => {
    // Giả sử bàn 4 chỗ gán cho đoàn 2 khách (occupiedSeats = 2)
    const capacity = 4;
    const occupiedSeats = 2;
    const seats = computeSeatPositions(capacity, 90, 90, false);
    const seatColors = seats.map((_, sIdx) => (sIdx < occupiedSeats ? "#ef4444" : "#ffffff"));

    // 2 ghế đầu màu đỏ, 2 ghế sau màu trắng
    expect(seatColors[0]).toBe("#ef4444");
    expect(seatColors[1]).toBe("#ef4444");
    expect(seatColors[2]).toBe("#ffffff");
    expect(seatColors[3]).toBe("#ffffff");

    const redCount = seatColors.filter((c) => c === "#ef4444").length;
    expect(redCount).toBe(2);
  });

  it("should determine red seats across merged tables matching total partySize of 5", () => {
    // Đoàn 5 khách gộp Bàn 03A (2 chỗ) và Bàn 03B (4 chỗ)
    const tableASeats = computeSeatPositions(2, 60, 90, false);
    const tableBSeats = computeSeatPositions(4, 80, 90, false);

    const partySize = 5;
    const allocatedA = Math.min(partySize, 2); // 2
    const allocatedB = Math.min(partySize - allocatedA, 4); // 3

    const colorsA = tableASeats.map((_, i) => (i < allocatedA ? "#ef4444" : "#ffffff"));
    const colorsB = tableBSeats.map((_, i) => (i < allocatedB ? "#ef4444" : "#ffffff"));

    // Bàn A đỏ cả 2
    expect(colorsA.filter((c) => c === "#ef4444").length).toBe(2);
    // Bàn B đỏ 3, trắng 1
    expect(colorsB.filter((c) => c === "#ef4444").length).toBe(3);
    expect(colorsB.filter((c) => c === "#ffffff").length).toBe(1);

    // Tổng số người đỏ trên cả 2 bàn = đúng 5 khách
    expect(colorsA.filter((c) => c === "#ef4444").length + colorsB.filter((c) => c === "#ef4444").length).toBe(5);
  });

  it("should choose distinct seat colors for RESERVED (#f59e0b) and OCCUPIED (#ffffff) without colliding with table backgrounds", () => {
    // RESERVED table (bg #eab308): occupied seats = amber #f59e0b, empty seats = white #ffffff
    expect(getSeatColor("RESERVED", true)).toBe("#f59e0b");
    expect(getSeatColor("RESERVED", false)).toBe("#ffffff");

    // OCCUPIED table (bg #ef4444): occupied seats = white #ffffff, empty seats remain distinct translucent white
    expect(getSeatColor("OCCUPIED", true)).toBe("#ffffff");
    expect(getSeatColor("OCCUPIED", false)).toBe("rgba(255, 255, 255, 0.35)");

    // AVAILABLE table (e.g. previewing seat allocation in ConfirmBookingDialog): red vs white
    expect(getSeatColor("AVAILABLE", true)).toBe("#ef4444");
    expect(getSeatColor("AVAILABLE", false)).toBe("#ffffff");
  });
});
