import { describe, expect, it } from "vitest";
import {
  getStandardDimensions,
  computeCollisions,
  computeZoneBoxes,
  computeZoneCollisions,
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
