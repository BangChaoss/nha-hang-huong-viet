import { API_BASE } from "@/lib/table-api";
import { getSession } from "@/lib/auth-session";

/** Loại bộ lọc thời gian của báo cáo (UC24). */
export type ReportFilterType = "DAILY" | "MONTHLY" | "YEARLY" | "CUSTOM";

/** Tóm tắt doanh thu từ hoá đơn đã thanh toán (BR-64). */
export type RevenueSummary = {
  netRevenue: number;
  grossRevenue: number;
  totalVoucherDiscount: number;
  totalDiscount: number;
  totalTax: number;
  totalInvoices: number;
  averageOrderValue: number;
  cashRevenue: number;
  vnpayRevenue: number;
  otherPaymentRevenue: number;
};

/** Một điểm trên trục thời gian: `HH:00` (ngày), `yyyy-MM-dd` (tháng/tuỳ chọn), `yyyy-MM` (năm). */
export type RevenueTimelinePoint = {
  timeLabel: string;
  revenue: number;
  orderCount: number;
};

/** Thống kê đặt bàn theo trạng thái. */
export type ReservationStats = {
  totalBookings: number;
  arrivedCount: number;
  confirmedCount: number;
  cancelledCount: number;
  noShowCount: number;
  pendingCount: number;
  completionRate: number;
  cancellationRate: number;
};

/** Hiệu suất bán của một món ăn. */
export type DishSalesItem = {
  dishId: string;
  dishName: string;
  categoryName: string;
  quantitySold: number;
  totalSales: number;
  percentageOfTotal: number;
};

/** Payload của `GET /api/admin/reports/overview`. */
export type ComprehensiveReport = {
  filterType: ReportFilterType;
  /** Định dạng `yyyy-MM-dd HH:mm:ss`. */
  startDate: string;
  /** Định dạng `yyyy-MM-dd HH:mm:ss`. */
  endDate: string;
  hasData: boolean;
  revenueSummary: RevenueSummary;
  timeline: RevenueTimelinePoint[];
  reservationStats: ReservationStats;
  topDishes: DishSalesItem[];
};

/**
 * Tham số lọc báo cáo.
 * - DAILY: `from` = `yyyy-MM-dd`
 * - MONTHLY: `from` = `yyyy-MM`
 * - YEARLY: `from` = `yyyy`
 * - CUSTOM: `from`, `to` = `yyyy-MM-dd`
 */
export type ReportParams = {
  filterType: ReportFilterType;
  from?: string;
  to?: string;
  dishLimit?: number;
};

function buildQuery(params: ReportParams): string {
  const search = new URLSearchParams();
  search.append("filterType", params.filterType);
  if (params.from) search.append("from", params.from);
  if (params.to) search.append("to", params.to);
  if (params.dishLimit !== undefined) search.append("dishLimit", String(params.dishLimit));
  return search.toString();
}

function authHeaders(): Record<string, string> {
  const token = getSession()?.token;
  return token ? { Authorization: `Bearer ${token}` } : {};
}

async function readError(response: Response): Promise<Error> {
  let message = `Yêu cầu thất bại (${response.status})`;
  if (response.status === 401 || response.status === 403) {
    message = "Bạn không có quyền xem báo cáo tài chính.";
  }
  try {
    const body = (await response.json()) as { message?: string; detail?: string } | null;
    if (body) {
      message = body.message ?? body.detail ?? message;
    }
  } catch {
    // Fallback khi body không phải JSON
  }
  return new Error(message);
}

/** Lấy tên file từ header Content-Disposition (ưu tiên `filename*` UTF-8). */
function parseFilename(disposition: string | null, fallback: string): string {
  if (!disposition) return fallback;
  const encoded = /filename\*=UTF-8''([^;]+)/i.exec(disposition);
  if (encoded?.[1]) {
    try {
      return decodeURIComponent(encoded[1]);
    } catch {
      // Bỏ qua và dùng filename thường
    }
  }
  const plain = /filename="?([^";]+)"?/i.exec(disposition);
  return plain?.[1] ?? fallback;
}

/** API thống kê báo cáo (Manager/Admin — BR-63). */
export const reportApi = {
  getOverview: async (params: ReportParams): Promise<ComprehensiveReport> => {
    const response = await fetch(`${API_BASE}/api/admin/reports/overview?${buildQuery(params)}`, {
      headers: { "Content-Type": "application/json", ...authHeaders() },
    });
    if (!response.ok) throw await readError(response);
    return response.json() as Promise<ComprehensiveReport>;
  },

  /** Tải file CSV báo cáo tổng hợp (BR-66). */
  exportCsv: async (params: ReportParams): Promise<{ blob: Blob; filename: string }> => {
    const response = await fetch(`${API_BASE}/api/admin/reports/export/csv?${buildQuery(params)}`, {
      headers: authHeaders(),
    });
    if (!response.ok) throw await readError(response);
    const filename = parseFilename(
      response.headers.get("Content-Disposition"),
      `Bao-cao-Huong-Viet-${params.filterType}.csv`,
    );
    return { blob: await response.blob(), filename };
  },
};
