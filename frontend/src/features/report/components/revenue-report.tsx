import { useMemo, useState, type ReactNode } from "react";
import {
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
  type TooltipProps,
} from "recharts";
import {
  AlertCircle,
  Banknote,
  BarChart3,
  CalendarCheck,
  Download,
  Loader2,
  Receipt,
  RefreshCw,
  TrendingUp,
} from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { formatVND } from "@/lib/restaurant-store";
import { reportApi, type ReportFilterType, type ReportParams } from "../api/report-api";
import { useReportOverview } from "../hooks/use-report-overview";
import { describeRange, fillTimeline, toIsoDate, type ChartPoint } from "../utils/report-timeline";

const FILTER_OPTIONS: { value: ReportFilterType; label: string }[] = [
  { value: "DAILY", label: "Theo ngày" },
  { value: "MONTHLY", label: "Theo tháng" },
  { value: "YEARLY", label: "Theo năm" },
  { value: "CUSTOM", label: "Tuỳ chọn" },
];

const DISH_LIMITS = [5, 10, 20];

const formatNumber = (n: number) => new Intl.NumberFormat("vi-VN").format(n);

/** Rút gọn số tiền cho trục biểu đồ: 1.2tr, 350k. */
const formatCompactVND = (n: number) => {
  if (n >= 1e9) return `${+(n / 1e9).toFixed(1)}tỷ`;
  if (n >= 1e6) return `${+(n / 1e6).toFixed(1)}tr`;
  if (n >= 1e3) return `${+(n / 1e3).toFixed(0)}k`;
  return String(n);
};

const percentOf = (part: number, total: number) => (total > 0 ? (part / total) * 100 : 0);

type FilterForm = {
  filterType: ReportFilterType;
  day: string;
  month: string;
  year: string;
  from: string;
  to: string;
};

function initialForm(): FilterForm {
  const today = new Date();
  const monthAgo = new Date(today);
  monthAgo.setDate(today.getDate() - 30);
  const iso = toIsoDate(today);
  return {
    filterType: "DAILY",
    day: iso,
    month: iso.slice(0, 7),
    year: String(today.getFullYear()),
    from: toIsoDate(monthAgo),
    to: iso,
  };
}

/** Chuyển form lọc thành tham số API; trả về chuỗi lỗi nếu không hợp lệ. */
function toParams(form: FilterForm, dishLimit: number): ReportParams | string {
  switch (form.filterType) {
    case "DAILY":
      if (!form.day) return "Vui lòng chọn ngày.";
      return { filterType: "DAILY", from: form.day, dishLimit };
    case "MONTHLY":
      if (!form.month) return "Vui lòng chọn tháng.";
      return { filterType: "MONTHLY", from: form.month, dishLimit };
    case "YEARLY":
      if (!/^\d{4}$/.test(form.year)) return "Năm không hợp lệ.";
      return { filterType: "YEARLY", from: form.year, dishLimit };
    case "CUSTOM":
      if (!form.from || !form.to) return "Vui lòng chọn khoảng thời gian.";
      if (form.from > form.to) return "Từ ngày không được lớn hơn đến ngày.";
      return { filterType: "CUSTOM", from: form.from, to: form.to, dishLimit };
  }
}

/** Màn hình báo cáo thống kê doanh thu, đặt bàn và món bán chạy (UC24). */
export function RevenueReport() {
  const [form, setForm] = useState(initialForm);
  const [dishLimit, setDishLimit] = useState(10);
  const [exporting, setExporting] = useState(false);

  const paramsOrError = toParams(form, dishLimit);
  const params = typeof paramsOrError === "string" ? null : paramsOrError;
  const validationError = typeof paramsOrError === "string" ? paramsOrError : null;

  const {
    data: report,
    isLoading,
    isFetching,
    error,
    refetch,
  } = useReportOverview(params ?? { filterType: "DAILY" }, params !== null);

  const chartData = useMemo(
    () =>
      report
        ? fillTimeline(report.filterType, report.startDate, report.endDate, report.timeline)
        : [],
    [report],
  );

  const update = <K extends keyof FilterForm>(key: K, value: FilterForm[K]) =>
    setForm((f) => ({ ...f, [key]: value }));

  const handleExport = async () => {
    if (!params) return;
    setExporting(true);
    try {
      // Xuất toàn bộ món trong kỳ, không giới hạn theo số món đang hiển thị.
      const { blob, filename } = await reportApi.exportCsv({ ...params, dishLimit: 100 });
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = filename;
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
      toast.success("Đã xuất báo cáo CSV.");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Không xuất được báo cáo.");
    } finally {
      setExporting(false);
    }
  };

  const summary = report?.revenueSummary;
  const reservations = report?.reservationStats;
  const pointsDiscount = summary
    ? Math.max(0, Number(summary.totalDiscount) - Number(summary.totalVoucherDiscount))
    : 0;

  return (
    <section className="space-y-6">
      {/* BỘ LỌC */}
      <div className="surface-card space-y-4 p-5">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <h2 className="text-2xl font-medium">Báo cáo thống kê</h2>
            <p className="text-sm text-muted-foreground">
              Doanh thu tính từ hoá đơn đã thanh toán, cập nhật theo thời gian thực.
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            <Button
              variant="outline"
              onClick={() => void refetch()}
              disabled={!params || isFetching}
            >
              <RefreshCw className={`mr-2 h-4 w-4 ${isFetching ? "animate-spin" : ""}`} />
              Làm mới
            </Button>
            <Button onClick={handleExport} disabled={!params || exporting || !report?.hasData}>
              {exporting ? (
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              ) : (
                <Download className="mr-2 h-4 w-4" />
              )}
              Xuất CSV
            </Button>
          </div>
        </div>

        <div className="flex flex-wrap gap-2">
          {FILTER_OPTIONS.map((opt) => (
            <Button
              key={opt.value}
              size="sm"
              variant={form.filterType === opt.value ? "default" : "outline"}
              onClick={() => update("filterType", opt.value)}
            >
              {opt.label}
            </Button>
          ))}
        </div>

        <div className="flex flex-wrap items-end gap-4">
          {form.filterType === "DAILY" && (
            <Field label="Ngày">
              <Input type="date" value={form.day} onChange={(e) => update("day", e.target.value)} />
            </Field>
          )}
          {form.filterType === "MONTHLY" && (
            <Field label="Tháng">
              <Input
                type="month"
                value={form.month}
                onChange={(e) => update("month", e.target.value)}
              />
            </Field>
          )}
          {form.filterType === "YEARLY" && (
            <Field label="Năm">
              <Input
                type="number"
                min={2000}
                max={2100}
                value={form.year}
                onChange={(e) => update("year", e.target.value)}
              />
            </Field>
          )}
          {form.filterType === "CUSTOM" && (
            <>
              <Field label="Từ ngày">
                <Input
                  type="date"
                  value={form.from}
                  max={form.to || undefined}
                  onChange={(e) => update("from", e.target.value)}
                />
              </Field>
              <Field label="Đến ngày">
                <Input
                  type="date"
                  value={form.to}
                  min={form.from || undefined}
                  onChange={(e) => update("to", e.target.value)}
                />
              </Field>
            </>
          )}
          {report && (
            <p className="pb-2 text-sm text-muted-foreground">
              Đang xem:{" "}
              <span className="font-medium text-foreground">
                {describeRange(report.filterType, report.startDate, report.endDate)}
              </span>
            </p>
          )}
        </div>
        {validationError && <p className="text-sm text-destructive">{validationError}</p>}
      </div>

      {/* TRẠNG THÁI */}
      {isLoading ? (
        <div className="surface-card flex items-center justify-center py-16 text-muted-foreground">
          <Loader2 className="mr-2 h-5 w-5 animate-spin" />
          Đang tải báo cáo...
        </div>
      ) : error ? (
        <div className="surface-card flex flex-col items-center gap-3 py-12 text-center">
          <AlertCircle className="h-8 w-8 text-destructive" />
          <p className="text-sm text-muted-foreground">{error.message}</p>
          <Button variant="outline" size="sm" onClick={() => void refetch()}>
            Thử lại
          </Button>
        </div>
      ) : report && summary && reservations ? (
        <div className={`space-y-6 transition-opacity ${isFetching ? "opacity-60" : ""}`}>
          {!report.hasData && (
            <div className="rounded-lg border border-dashed p-6 text-center text-sm text-muted-foreground">
              Không có dữ liệu trong khoảng thời gian đã chọn.
            </div>
          )}

          {/* KPI */}
          <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
            <Kpi
              label="Doanh thu thuần"
              value={formatVND(Number(summary.netRevenue))}
              icon={Banknote}
            />
            <Kpi
              label="Hoá đơn đã thanh toán"
              value={formatNumber(summary.totalInvoices)}
              icon={Receipt}
            />
            <Kpi
              label="Giá trị TB / hoá đơn"
              value={formatVND(Number(summary.averageOrderValue))}
              icon={TrendingUp}
            />
            <Kpi
              label="Lượt đặt bàn"
              value={formatNumber(reservations.totalBookings)}
              icon={CalendarCheck}
            />
          </div>

          {/* BIỂU ĐỒ + CƠ CẤU DOANH THU */}
          <div className="grid gap-5 lg:grid-cols-3">
            <div className="surface-card p-5 lg:col-span-2">
              <div className="flex items-center gap-2">
                <BarChart3 className="h-5 w-5 text-primary" />
                <h3 className="text-xl font-medium">Doanh thu theo thời gian</h3>
              </div>
              <div className="mt-4 h-72">
                <ResponsiveContainer>
                  <BarChart data={chartData}>
                    <CartesianGrid vertical={false} stroke="var(--border)" />
                    <XAxis
                      dataKey="label"
                      tickLine={false}
                      axisLine={false}
                      fontSize={12}
                      minTickGap={8}
                    />
                    <YAxis
                      yAxisId="revenue"
                      tickLine={false}
                      axisLine={false}
                      fontSize={12}
                      width={48}
                      tickFormatter={formatCompactVND}
                    />
                    <Tooltip cursor={{ fill: "var(--muted)" }} content={<RevenueTooltip />} />
                    <Bar
                      yAxisId="revenue"
                      dataKey="revenue"
                      fill="var(--primary)"
                      radius={[6, 6, 0, 0]}
                    />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </div>

            <div className="surface-card space-y-4 p-5">
              <h3 className="text-xl font-medium">Cơ cấu doanh thu</h3>
              <dl className="space-y-2 text-sm">
                <Row
                  label="Tiền món (trước thuế)"
                  value={formatVND(Number(summary.grossRevenue))}
                />
                <Row label="Thuế VAT" value={`+ ${formatVND(Number(summary.totalTax))}`} />
                <Row
                  label="Giảm giá voucher"
                  value={`− ${formatVND(Number(summary.totalVoucherDiscount))}`}
                />
                <Row label="Đổi điểm tích luỹ" value={`− ${formatVND(pointsDiscount)}`} />
                <div className="border-t pt-2">
                  <Row
                    label="Doanh thu thuần"
                    value={formatVND(Number(summary.netRevenue))}
                    strong
                  />
                </div>
              </dl>
              <div className="space-y-3 border-t pt-4">
                <p className="text-sm font-medium">Phương thức thanh toán</p>
                <ShareBar
                  label="Tiền mặt"
                  amount={Number(summary.cashRevenue)}
                  total={Number(summary.netRevenue)}
                />
                <ShareBar
                  label="VNPay"
                  amount={Number(summary.vnpayRevenue)}
                  total={Number(summary.netRevenue)}
                />
                {Number(summary.otherPaymentRevenue) > 0 && (
                  <ShareBar
                    label="Khác"
                    amount={Number(summary.otherPaymentRevenue)}
                    total={Number(summary.netRevenue)}
                  />
                )}
              </div>
            </div>
          </div>

          {/* ĐẶT BÀN + TOP MÓN */}
          <div className="grid gap-5 lg:grid-cols-3">
            <div className="surface-card space-y-4 p-5">
              <h3 className="text-xl font-medium">Tình hình đặt bàn</h3>
              <div className="grid grid-cols-2 gap-3">
                <RateTile
                  label="Tỉ lệ đến nhà hàng"
                  value={reservations.completionRate}
                  className="text-emerald-600"
                />
                <RateTile
                  label="Tỉ lệ huỷ"
                  value={reservations.cancellationRate}
                  className="text-destructive"
                />
              </div>
              <dl className="space-y-2 text-sm">
                <Row label="Đã đến" value={formatNumber(reservations.arrivedCount)} />
                <Row label="Đã thanh toán" value={formatNumber(reservations.paidCount ?? 0)} />
                <Row label="Đã xác nhận" value={formatNumber(reservations.confirmedCount)} />
                <Row label="Đang chờ" value={formatNumber(reservations.pendingCount)} />
                <Row label="Đã huỷ" value={formatNumber(reservations.cancelledCount)} />
                <Row label="Không đến (no-show)" value={formatNumber(reservations.noShowCount)} />
                <div className="border-t pt-2">
                  <Row label="Tổng" value={formatNumber(reservations.totalBookings)} strong />
                </div>
              </dl>
            </div>

            <div className="surface-card space-y-4 p-5 lg:col-span-2">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <h3 className="text-xl font-medium">Món bán chạy</h3>
                <label className="flex items-center gap-2 text-sm text-muted-foreground">
                  Hiển thị
                  <select
                    className="rounded-md border bg-background px-2 py-1 text-sm text-foreground"
                    value={dishLimit}
                    onChange={(e) => setDishLimit(Number(e.target.value))}
                  >
                    {DISH_LIMITS.map((n) => (
                      <option key={n} value={n}>
                        Top {n}
                      </option>
                    ))}
                  </select>
                </label>
              </div>
              {report.topDishes.length === 0 ? (
                <div className="rounded-lg border border-dashed py-10 text-center text-sm text-muted-foreground">
                  Chưa có món nào được bán trong kỳ này.
                </div>
              ) : (
                <div className="overflow-x-auto rounded-lg border">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="border-b bg-muted/50 text-left">
                        <th className="px-3 py-2.5 font-semibold">#</th>
                        <th className="px-3 py-2.5 font-semibold">Món</th>
                        <th className="px-3 py-2.5 font-semibold">Danh mục</th>
                        <th className="px-3 py-2.5 text-right font-semibold">Số lượng</th>
                        <th className="px-3 py-2.5 text-right font-semibold">Doanh số</th>
                        <th className="px-3 py-2.5 font-semibold">Tỉ trọng</th>
                      </tr>
                    </thead>
                    <tbody>
                      {report.topDishes.map((dish, idx) => (
                        <tr key={dish.dishId} className="border-b last:border-0 hover:bg-muted/30">
                          <td className="font-display px-3 py-2.5 text-lg text-primary">
                            {idx + 1}
                          </td>
                          <td className="px-3 py-2.5 font-medium">{dish.dishName}</td>
                          <td className="px-3 py-2.5 text-muted-foreground">
                            {dish.categoryName || "—"}
                          </td>
                          <td className="px-3 py-2.5 text-right tabular-nums">
                            {formatNumber(dish.quantitySold)}
                          </td>
                          <td className="px-3 py-2.5 text-right tabular-nums">
                            {formatVND(Number(dish.totalSales))}
                          </td>
                          <td className="min-w-32 px-3 py-2.5">
                            <div className="flex items-center gap-2">
                              <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-muted">
                                <div
                                  className="h-full rounded-full bg-primary"
                                  style={{ width: `${Math.min(100, dish.percentageOfTotal)}%` }}
                                />
                              </div>
                              <span className="w-12 text-right text-xs tabular-nums text-muted-foreground">
                                {dish.percentageOfTotal.toFixed(1)}%
                              </span>
                            </div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </div>
        </div>
      ) : null}
    </section>
  );
}

/** Tooltip của biểu đồ: hiện cả doanh thu lẫn số hoá đơn của mốc đang trỏ. */
function RevenueTooltip({ active, payload }: TooltipProps<number, string>) {
  const point = payload?.[0]?.payload as ChartPoint | undefined;
  if (!active || !point) return null;
  return (
    <div className="rounded-md border border-border bg-background px-3 py-2 text-sm shadow-md">
      <p className="font-medium">{point.label}</p>
      <p className="text-muted-foreground">
        Doanh thu: <span className="text-foreground">{formatVND(point.revenue)}</span>
      </p>
      <p className="text-muted-foreground">
        Số hoá đơn: <span className="text-foreground">{formatNumber(point.orderCount)}</span>
      </p>
    </div>
  );
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="block space-y-1.5 text-sm">
      <span className="font-medium">{label}</span>
      {children}
    </label>
  );
}

function Kpi({
  label,
  value,
  icon: Icon,
}: {
  label: string;
  value: string;
  icon: typeof Banknote;
}) {
  return (
    <div className="surface-card p-5">
      <div className="flex items-center justify-between text-muted-foreground">
        <span className="text-sm">{label}</span>
        <Icon className="h-4 w-4" />
      </div>
      <p className="mt-3 text-2xl font-semibold tabular-nums">{value}</p>
    </div>
  );
}

function Row({ label, value, strong }: { label: string; value: string; strong?: boolean }) {
  return (
    <div className="flex items-center justify-between gap-3">
      <dt className={strong ? "font-semibold" : "text-muted-foreground"}>{label}</dt>
      <dd className={`tabular-nums ${strong ? "font-semibold text-primary" : ""}`}>{value}</dd>
    </div>
  );
}

function ShareBar({ label, amount, total }: { label: string; amount: number; total: number }) {
  const pct = percentOf(amount, total);
  return (
    <div className="space-y-1">
      <div className="flex justify-between text-sm">
        <span className="text-muted-foreground">{label}</span>
        <span className="tabular-nums">
          {formatVND(amount)} · {pct.toFixed(1)}%
        </span>
      </div>
      <div className="h-1.5 overflow-hidden rounded-full bg-muted">
        <div
          className="h-full rounded-full bg-primary"
          style={{ width: `${Math.min(100, pct)}%` }}
        />
      </div>
    </div>
  );
}

function RateTile({
  label,
  value,
  className,
}: {
  label: string;
  value: number;
  className: string;
}) {
  return (
    <div className="rounded-lg border p-3">
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className={`mt-1 text-2xl font-semibold tabular-nums ${className}`}>{value.toFixed(1)}%</p>
    </div>
  );
}
