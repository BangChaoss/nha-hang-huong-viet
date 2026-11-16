import type { ReportFilterType, RevenueTimelinePoint } from "../api/report-api";

/** Điểm dữ liệu đã chuẩn hoá để vẽ biểu đồ. */
export type ChartPoint = {
  key: string;
  label: string;
  revenue: number;
  orderCount: number;
};

/** Giới hạn số cột khi lọc tuỳ chọn để biểu đồ không bị quá dày. */
const MAX_CUSTOM_DAYS = 366;

const pad = (n: number) => String(n).padStart(2, "0");

/** Định dạng ngày theo giờ địa phương thành `yyyy-MM-dd`. */
export const toIsoDate = (d: Date) =>
  `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;

/** Parse phần ngày của chuỗi `yyyy-MM-dd[ HH:mm:ss]` thành Date địa phương. */
function parseDate(value: string): Date {
  const [y = 1970, m = 1, d = 1] = value.slice(0, 10).split("-").map(Number);
  return new Date(y, m - 1, d);
}

/** Sinh danh sách khoá thời gian đầy đủ của khoảng báo cáo, khớp định dạng `timeLabel` của backend. */
function buildKeys(filterType: ReportFilterType, startDate: string, endDate: string): string[] {
  if (filterType === "DAILY") {
    return Array.from({ length: 24 }, (_, h) => `${pad(h)}:00`);
  }
  const start = parseDate(startDate);
  if (filterType === "YEARLY") {
    return Array.from({ length: 12 }, (_, m) => `${start.getFullYear()}-${pad(m + 1)}`);
  }
  const end = parseDate(endDate);
  const keys: string[] = [];
  for (
    const d = new Date(start);
    d <= end && keys.length < MAX_CUSTOM_DAYS;
    d.setDate(d.getDate() + 1)
  ) {
    keys.push(toIsoDate(d));
  }
  return keys;
}

function formatLabel(filterType: ReportFilterType, key: string): string {
  switch (filterType) {
    case "DAILY":
      return `${key.slice(0, 2)}h`;
    case "YEARLY":
      return `T${Number(key.slice(5, 7))}`;
    case "MONTHLY":
      return key.slice(8, 10);
    case "CUSTOM":
      return `${key.slice(8, 10)}/${key.slice(5, 7)}`;
  }
}

/**
 * Backend chỉ trả về các mốc có doanh thu; hàm này lấp các mốc trống bằng 0
 * để trục thời gian liên tục (24 giờ / các ngày trong tháng / 12 tháng).
 */
export function fillTimeline(
  filterType: ReportFilterType,
  startDate: string,
  endDate: string,
  timeline: RevenueTimelinePoint[],
): ChartPoint[] {
  const byKey = new Map(timeline.map((p) => [p.timeLabel, p]));
  const keys = buildKeys(filterType, startDate, endDate);
  // Giữ lại các mốc backend trả về nhưng nằm ngoài danh sách sinh ra (phòng trường hợp lệch định dạng).
  for (const p of timeline) {
    if (!keys.includes(p.timeLabel)) keys.push(p.timeLabel);
  }
  keys.sort();
  return keys.map((key) => {
    const point = byKey.get(key);
    return {
      key,
      label: formatLabel(filterType, key),
      revenue: Number(point?.revenue ?? 0),
      orderCount: Number(point?.orderCount ?? 0),
    };
  });
}

/** Mô tả khoảng thời gian dễ đọc cho tiêu đề báo cáo. */
export function describeRange(filterType: ReportFilterType, startDate: string, endDate: string) {
  const fmt = (v: string) => parseDate(v).toLocaleDateString("vi-VN");
  const start = parseDate(startDate);
  switch (filterType) {
    case "DAILY":
      return `Ngày ${fmt(startDate)}`;
    case "MONTHLY":
      return `Tháng ${start.getMonth() + 1}/${start.getFullYear()}`;
    case "YEARLY":
      return `Năm ${start.getFullYear()}`;
    case "CUSTOM":
      return `Từ ${fmt(startDate)} đến ${fmt(endDate)}`;
  }
}
