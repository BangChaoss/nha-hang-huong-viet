import { describe, expect, it } from "vitest";
import { getStandardDimensions } from "@/components/hv/FloorPlanCanvas";

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
