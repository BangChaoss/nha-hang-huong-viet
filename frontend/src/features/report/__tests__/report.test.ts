import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import { reportApi } from "../api/report-api";
import { describeRange, fillTimeline } from "../utils/report-timeline";

describe("reportApi", () => {
  const originalFetch = global.fetch;

  beforeEach(() => {
    vi.restoreAllMocks();
  });

  afterEach(() => {
    global.fetch = originalFetch;
  });

  it("getOverview() should GET /api/admin/reports/overview with filter params", async () => {
    const body = { filterType: "MONTHLY", hasData: false };
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve(body),
    });

    const result = await reportApi.getOverview({
      filterType: "MONTHLY",
      from: "2026-10",
      dishLimit: 5,
    });

    expect(result).toEqual(body);
    const url = (global.fetch as ReturnType<typeof vi.fn>).mock.calls[0]?.[0] as string;
    expect(url).toContain("/api/admin/reports/overview?");
    expect(url).toContain("filterType=MONTHLY");
    expect(url).toContain("from=2026-10");
    expect(url).toContain("dishLimit=5");
  });

  it("getOverview() should map 403 to a permission error", async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 403,
      json: () => Promise.reject(new Error("no body")),
    });

    await expect(reportApi.getOverview({ filterType: "DAILY" })).rejects.toThrow(
      "Bạn không có quyền xem báo cáo tài chính.",
    );
  });

  it("exportCsv() should read the UTF-8 filename from Content-Disposition", async () => {
    const blob = new Blob(["a,b"]);
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      headers: new Headers({
        "Content-Disposition":
          "attachment; filename=\"Bao-cao.csv\"; filename*=UTF-8''B%C3%A1o-c%C3%A1o.csv",
      }),
      blob: () => Promise.resolve(blob),
    });

    const result = await reportApi.exportCsv({ filterType: "DAILY" });

    expect(result.filename).toBe("Báo-cáo.csv");
    expect(result.blob).toBe(blob);
  });
});

describe("fillTimeline", () => {
  it("fills all 24 hours for DAILY and keeps backend values", () => {
    const points = fillTimeline("DAILY", "2026-11-07 00:00:00", "2026-11-07 23:59:59", [
      { timeLabel: "12:00", revenue: 500000, orderCount: 3 },
    ]);

    expect(points).toHaveLength(24);
    expect(points[0]).toMatchObject({ key: "00:00", label: "00h", revenue: 0 });
    expect(points[12]).toMatchObject({ key: "12:00", revenue: 500000, orderCount: 3 });
  });

  it("fills every day of the month for MONTHLY", () => {
    const points = fillTimeline("MONTHLY", "2026-02-01 00:00:00", "2026-02-28 23:59:59", []);

    expect(points).toHaveLength(28);
    expect(points[27]).toMatchObject({ key: "2026-02-28", label: "28" });
  });

  it("fills 12 months for YEARLY", () => {
    const points = fillTimeline("YEARLY", "2026-01-01 00:00:00", "2026-12-31 23:59:59", [
      { timeLabel: "2026-03", revenue: 1000, orderCount: 1 },
    ]);

    expect(points).toHaveLength(12);
    expect(points[2]).toMatchObject({ label: "T3", revenue: 1000 });
  });
});

describe("describeRange", () => {
  it("describes each filter type", () => {
    expect(describeRange("MONTHLY", "2026-10-01 00:00:00", "2026-10-31 23:59:59")).toBe(
      "Tháng 10/2026",
    );
    expect(describeRange("YEARLY", "2026-01-01 00:00:00", "2026-12-31 23:59:59")).toBe("Năm 2026");
  });
});
