import { useEffect, useState, type ReactNode } from "react";
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import {Banknote, Users, CheckCircle2, Award, ShieldCheck, TicketPercent, Plus, LayoutGrid,
   Trash2, Eye, Loader2, BarChart3, MessagesSquare,ArrowLeft,} from "lucide-react";
import { toast } from "sonner";
import { formatVND, useRestaurant } from "@/lib/restaurant-store";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  voucherApi,
  type Voucher,
  type VoucherPayload,
  type DiscountType,
  type VoucherUsageRecord,
} from "@/lib/voucher-api";
import { paymentApi } from "@/lib/payment-api";
import { TableManagement } from "@/components/hv/TableManagement";
import { MenuManagement } from "@/features/menu/components/menu-management";
import { RevenueReport } from "@/features/report/components/revenue-report";
import { FeedbackManagement } from "@/features/feedback/components/feedback-management";
import { BackButton } from "@/components/hv/BackButton";
import { StaffManagement } from "@/components/hv/StaffManagement";
import { useSession } from "@/lib/auth-session";

const week = [
  { d: "T2", v: 18.2 },
  { d: "T3", v: 21.5 },
  { d: "T4", v: 19.8 },
  { d: "T5", v: 24.1 },
  { d: "T6", v: 31.4 },
  { d: "T7", v: 38.9 },
];

export type ManagerTab =
  "overview" | "reports" | "feedback" | "permissions" | "vouchers" | "loyalty" | "tables";

export interface ManagerDashboardProps {
  activeTab?: ManagerTab;
  onTabChange?: (tab: ManagerTab) => void;
}

type VoucherForm = {
  voucherCode: string;
  title: string;
  description: string;
  discountType: DiscountType;
  discountValue: string;
  maxDiscountAmount: string;
  minOrderAmount: string;
  startDate: string;
  endDate: string;
  totalQuantity: string;
  maxUsesPerCustomer: string;
};

const emptyVoucher = (): VoucherForm => ({
  voucherCode: "",
  title: "",
  description: "",
  discountType: "PERCENTAGE",
  discountValue: "20",
  maxDiscountAmount: "100000",
  minOrderAmount: "0",
  startDate: "",
  endDate: "",
  totalQuantity: "500",
  maxUsesPerCustomer: "1",
});

const formatVoucherDate = (value: string) => {
  if (!value) return "—";
  const [year = 0, month = 1, day = 1] = value.slice(0, 10).split("-").map(Number);
  return new Date(year, month - 1, day).toLocaleDateString("vi-VN");
};

export function ManagerDashboard({
  activeTab: controlledTab,
  onTabChange,
}: ManagerDashboardProps = {}) {
  const [internalTab, setInternalTab] = useState<ManagerTab>("overview");
  const activeTab = controlledTab ?? internalTab;
  const handleTabChange = (tab: ManagerTab) => {
    if (onTabChange) onTabChange(tab);
    else setInternalTab(tab);
  };
  const { session } = useSession();

  const [voucherForm, setVoucherForm] = useState(emptyVoucher);
  const [vouchersList, setVouchersList] = useState<Voucher[]>([]);
  const [voucherSaving, setVoucherSaving] = useState(false);
  const [voucherLoading, setVoucherLoading] = useState(false);

  // Mini-tab bên trong tab vouchers ("manage" | "usages")
  const [voucherSubTab, setVoucherSubTab] = useState<"manage" | "usages">("manage");

  // Loyalty Settings
  const [loyaltyRate, setLoyaltyRate] = useState("5");
  const [pointValueVnd, setPointValueVnd] = useState("1");
  const [loyaltySaving, setLoyaltySaving] = useState(false);

  const [selectedVoucherForUsages, setSelectedVoucherForUsages] = useState<Voucher | null>(null);
  const [usagesList, setUsagesList] = useState<VoucherUsageRecord[]>([]);
  const [usagesLoading, setUsagesLoading] = useState(false);

  const updateVoucher = (key: keyof ReturnType<typeof emptyVoucher>, value: string) =>
    setVoucherForm((v) => ({ ...v, [key]: value }));

  const loadVouchers = async () => {
    setVoucherLoading(true);
    try {
      setVouchersList(await voucherApi.list());
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Không tải được danh sách voucher.");
    } finally {
      setVoucherLoading(false);
    }
  };

  const handleOpenUsages = async (v: Voucher) => {
    setSelectedVoucherForUsages(v);
    setVoucherSubTab("usages");
    setUsagesLoading(true);
    try {
      const data = await voucherApi.getUsages(v.id);
      setUsagesList(data);
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Không tải được danh sách lượt dùng.");
      setUsagesList([]);
    } finally {
      setUsagesLoading(false);
    }
  };

  useEffect(() => {
    void loadVouchers();
    void paymentApi
      .getLoyaltyRate()
      .then((value) => {
        setLoyaltyRate(String(value.earnRatePercent));
        setPointValueVnd(String(value.pointValueVnd || 1));
      })
      .catch((error) =>
        toast.error(error instanceof Error ? error.message : "Không tải được cấu hình tích điểm."),
      );
  }, []);

  const { transactions, dishes, tables } = useRestaurant();
  const revenue = transactions.reduce((s, t) => s + t.total, 0);
  const points = transactions.reduce((s, t) => s + t.points, 0);
  const served = transactions.length + tables.filter((t) => t.status === "occupied").length;
  const data = [...week, { d: "CN", v: +(revenue / 1e6).toFixed(1) }];
  const top = [...dishes].sort((a, b) => b.sold - a.sold).slice(0, 5);
  const kpis = [
    { l: "Doanh thu hôm nay", v: formatVND(revenue), i: Banknote },
    { l: "Tổng số bàn phục vụ", v: String(served), i: Users },
    { l: "Đơn hoàn tất", v: String(transactions.length), i: CheckCircle2 },
    { l: "Điểm tích luỹ đã phát", v: formatVND(points), i: Award },
  ];

  return (
    <div className="space-y-6">
      <div className="space-y-4">
        <div>
          <BackButton to={session?.role === "ADMIN" ? "/admin" : "/"} label="Quay lại" />
        </div>
        <div className="flex items-center gap-4">
          <div className="rounded-xl bg-primary/10 p-3 text-primary">
            <ShieldCheck className="h-7 w-7" />
          </div>
          <div className="flex flex-col">
            <p className="text-xs uppercase tracking-[0.2em] text-muted-foreground">
              Tổng quan vận hành
            </p>
            <h1 className="text-4xl font-semibold">Bảng điều khiển Quản lý</h1>
          </div>
        </div>
      </div>

      <div className="flex flex-wrap gap-2 border-b pb-3">
        <Button
          variant={activeTab === "overview" ? "default" : "outline"}
          onClick={() => handleTabChange("overview")}
        >
          Tổng quan
        </Button>
        <Button
          variant={activeTab === "reports" ? "default" : "outline"}
          onClick={() => handleTabChange("reports")}
        >
          <BarChart3 className="mr-2 h-4 w-4" />
          Báo cáo thống kê
        </Button>
        <Button
          variant={activeTab === "feedback" ? "default" : "outline"}
          onClick={() => handleTabChange("feedback")}
        >
          <MessagesSquare className="mr-2 h-4 w-4" />
          Đánh giá khách hàng
        </Button>
        <Button
          variant={activeTab === "permissions" ? "default" : "outline"}
          onClick={() => handleTabChange("permissions")}
        >
          <ShieldCheck className="mr-2 h-4 w-4" />
          Phân quyền người dùng
        </Button>
        <Button
          variant={activeTab === "vouchers" ? "default" : "outline"}
          onClick={() => handleTabChange("vouchers")}
        >
          <TicketPercent className="mr-2 h-4 w-4" />
          Voucher nhà hàng
        </Button>
        <Button
          variant={activeTab === "loyalty" ? "default" : "outline"}
          onClick={() => handleTabChange("loyalty")}
        >
          <Award className="mr-2 h-4 w-4" />
          Cấu hình tích & đổi điểm
        </Button>
        <Button
          variant={activeTab === "tables" ? "default" : "outline"}
          onClick={() => handleTabChange("tables")}
        >
          <LayoutGrid className="mr-2 h-4 w-4" />
          Sơ đồ & Bàn ăn
        </Button>
      </div>

      {activeTab === "reports" && <RevenueReport />}
      {activeTab === "feedback" && <FeedbackManagement />}
      {activeTab === "tables" && <TableManagement />}
      {activeTab === "permissions" && (
        <section className="space-y-4">
          {session ? (
            <StaffManagement actor={session} title="Quản lý nhân sự & Phân quyền" />
          ) : (
            <div className="surface-card p-6 text-center text-sm text-muted-foreground">
              Vui lòng đăng nhập để xem danh sách nhân sự.
            </div>
          )}
        </section>
      )}

      {/* TAB VOUCHERS: SỬ DỤNG MINI-TABS THAY VÌ DIALOG POPUP */}
      {activeTab === "vouchers" && (
        <section className="space-y-6">
          <Tabs
            value={voucherSubTab}
            onValueChange={(val) => setVoucherSubTab(val as "manage" | "usages")}
            className="space-y-6"
          >
            <TabsList className="bg-muted p-1">
              <TabsTrigger value="manage" className="text-sm">
                Danh sách & Tạo Voucher
              </TabsTrigger>
              <TabsTrigger value="usages" className="text-sm flex items-center gap-1.5">
                <Eye className="h-4 w-4" />
                <span>
                  Lượt sử dụng voucher{" "}
                  {selectedVoucherForUsages ? `(${selectedVoucherForUsages.voucherCode})` : ""}
                </span>
              </TabsTrigger>
            </TabsList>

            {/* MINI-TAB 1: TẠO VÀ QUẢN LÝ VOUCHER */}
            <TabsContent value="manage" className="space-y-6 mt-0">
              <div className="surface-card space-y-4 p-5">
                <h2 className="text-2xl font-medium">Tạo voucher nhà hàng</h2>
                <p className="text-sm text-muted-foreground">
                  Khách dùng voucher khi thanh toán cần nhập số điện thoại; giới hạn lượt dùng được tính theo số điện thoại.
                </p>
                <form
                  className="grid gap-4 md:grid-cols-2 xl:grid-cols-3"
                  onSubmit={async (e) => {
                    e.preventDefault();
                    const value = Number(voucherForm.discountValue);
                    const maxDiscount = voucherForm.maxDiscountAmount.trim() ? Number(voucherForm.maxDiscountAmount) : null;
                    const minOrder = Number(voucherForm.minOrderAmount);
                    const totalQuantity = Number(voucherForm.totalQuantity);
                    const perCustomer = Number(voucherForm.maxUsesPerCustomer);

                    if (!voucherForm.voucherCode.trim() || !voucherForm.title.trim() || !voucherForm.startDate || !voucherForm.endDate) {
                      toast.error("Vui lòng nhập mã, tên chương trình và thời gian áp dụng.");
                      return;
                    }
                    if (voucherForm.endDate < voucherForm.startDate) {
                      toast.error("Ngày kết thúc không được trước ngày bắt đầu.");
                      return;
                    }
                    if (!Number.isFinite(value) || value <= 0 || (voucherForm.discountType === "PERCENTAGE" && value > 100)) {
                      toast.error("Giá trị giảm không hợp lệ.");
                      return;
                    }

                    const payload: VoucherPayload = {
                      voucherCode: voucherForm.voucherCode.trim().toUpperCase(),
                      title: voucherForm.title.trim(),
                      description: voucherForm.description.trim() || null,
                      discountType: voucherForm.discountType,
                      discountValue: value,
                      maxDiscountAmount: maxDiscount,
                      minOrderAmount: minOrder,
                      startDate: `${voucherForm.startDate}T00:00:00`,
                      endDate: `${voucherForm.endDate}T23:59:59`,
                      totalQuantity,
                      maxUsesPerCustomer: perCustomer,
                    };

                    setVoucherSaving(true);
                    try {
                      await voucherApi.create(payload);
                      await loadVouchers();
                      setVoucherForm(emptyVoucher());
                      toast.success("Đã tạo voucher thành công.");
                    } catch (error) {
                      toast.error(error instanceof Error ? error.message : "Không tạo được voucher.");
                    } finally {
                      setVoucherSaving(false);
                    }
                  }}
                >
                  <Field label="Mã Voucher">
                    <Input
                      required
                      maxLength={50}
                      value={voucherForm.voucherCode}
                      onChange={(e) => updateVoucher("voucherCode", e.target.value)}
                      placeholder="HUONGVIET20"
                    />
                  </Field>
                  <Field label="Tên chương trình">
                    <Input
                      required
                      maxLength={150}
                      value={voucherForm.title}
                      onChange={(e) => updateVoucher("title", e.target.value)}
                      placeholder="Ưu đãi khách hàng mới"
                    />
                  </Field>
                  <Field label="Mô tả chi tiết">
                    <Input
                      value={voucherForm.description}
                      onChange={(e) => updateVoucher("description", e.target.value)}
                      placeholder="Điều kiện và nội dung ưu đãi"
                    />
                  </Field>
                  <Field label="Loại giảm giá">
                    <select
                      className="w-full rounded-md border bg-background p-2.5 text-sm"
                      value={voucherForm.discountType}
                      onChange={(e) => {
                        const type = e.target.value as DiscountType;
                        updateVoucher("discountType", type);
                        if (type === "FIXED_AMOUNT") updateVoucher("maxDiscountAmount", "");
                        else updateVoucher("maxDiscountAmount", "100000");
                      }}
                    >
                      <option value="PERCENTAGE">Giảm theo phần trăm (%)</option>
                      <option value="FIXED_AMOUNT">Giảm theo giá tiền (VNĐ)</option>
                    </select>
                  </Field>
                  <Field label={voucherForm.discountType === "PERCENTAGE" ? "Giá trị giảm (%)" : "Giá trị giảm (VNĐ)"}>
                    <Input
                      required
                      type="number"
                      min="0.01"
                      max={voucherForm.discountType === "PERCENTAGE" ? 100 : undefined}
                      step="0.01"
                      value={voucherForm.discountValue}
                      onChange={(e) => updateVoucher("discountValue", e.target.value)}
                    />
                  </Field>
                  {voucherForm.discountType === "PERCENTAGE" && (
                    <Field label="Mức giảm tối đa (VNĐ)">
                      <Input
                        type="number"
                        min="0"
                        step="1000"
                        value={voucherForm.maxDiscountAmount}
                        onChange={(e) => updateVoucher("maxDiscountAmount", e.target.value)}
                        placeholder="100000"
                      />
                    </Field>
                  )}
                  <Field label="Đơn tối thiểu (VNĐ)">
                    <Input
                      required
                      type="number"
                      min="0"
                      step="1000"
                      value={voucherForm.minOrderAmount}
                      onChange={(e) => updateVoucher("minOrderAmount", e.target.value)}
                    />
                  </Field>
                  <Field label="Ngày bắt đầu">
                    <Input
                      required
                      type="date"
                      value={voucherForm.startDate}
                      onChange={(e) => updateVoucher("startDate", e.target.value)}
                    />
                  </Field>
                  <Field label="Ngày kết thúc">
                    <Input
                      required
                      type="date"
                      min={voucherForm.startDate || undefined}
                      value={voucherForm.endDate}
                      onChange={(e) => updateVoucher("endDate", e.target.value)}
                    />
                  </Field>
                  <Field label="Số lượng phát hành">
                    <Input
                      required
                      type="number"
                      min="1"
                      step="1"
                      value={voucherForm.totalQuantity}
                      onChange={(e) => updateVoucher("totalQuantity", e.target.value)}
                    />
                  </Field>
                  <Field label="Giới hạn dùng/khách (theo SĐT)">
                    <Input
                      required
                      type="number"
                      min="1"
                      step="1"
                      value={voucherForm.maxUsesPerCustomer}
                      onChange={(e) => updateVoucher("maxUsesPerCustomer", e.target.value)}
                    />
                  </Field>
                  <div className="flex items-end">
                    <Button type="submit" disabled={voucherSaving} className="w-full">
                      <Plus className="mr-2 h-4 w-4" />
                      {voucherSaving ? "Đang lưu..." : "Tạo voucher"}
                    </Button>
                  </div>
                </form>
              </div>

              <div className="surface-card overflow-x-auto p-5">
                <div className="mb-4 flex items-center justify-between">
                  <h2 className="text-2xl font-medium">Danh sách voucher</h2>
                  <Button type="button" variant="outline" onClick={() => void loadVouchers()} disabled={voucherLoading}>
                    {voucherLoading ? "Đang tải..." : "Làm mới"}
                  </Button>
                </div>
                {vouchersList.length === 0 ? (
                  <p className="text-sm text-muted-foreground">Chưa có voucher nào được tạo.</p>
                ) : (
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="border-b text-left">
                        <th className="py-2">Mã / Tên</th>
                        <th>Ưu đãi</th>
                        <th>Hiệu lực</th>
                        <th>Lượt dùng / Phát hành</th>
                        <th>Giới hạn/khách</th>
                        <th>Trạng thái</th>
                        <th className="text-right">Thao tác</th>
                      </tr>
                    </thead>
                    <tbody>
                      {vouchersList.map((v) => (
                        <tr key={v.id} className="border-b last:border-0">
                          <td className="py-3">
                            <b className="font-mono text-primary">{v.voucherCode}</b>
                            <p className="font-medium">{v.title}</p>
                          </td>
                          <td>
                            {v.discountType === "PERCENTAGE" ? `${v.discountValue}%` : formatVND(v.discountValue)}
                            <p className="text-xs text-muted-foreground">Đơn tối thiểu {formatVND(v.minOrderAmount)}</p>
                          </td>
                          <td>{formatVoucherDate(v.startDate)} – {formatVoucherDate(v.endDate)}</td>
                          <td>
                            <button
                              type="button"
                              onClick={() => void handleOpenUsages(v)}
                              className="font-medium text-primary hover:underline cursor-pointer"
                              title="Bấm để xem danh sách lượt sử dụng"
                            >
                              {v.usedQuantity} / {v.totalQuantity}
                            </button>
                          </td>
                          <td>{v.maxUsesPerCustomer} lượt / SĐT</td>
                          <td>
                            <span className={v.active ? "text-emerald-600 font-medium" : "text-muted-foreground"}>
                              {v.active ? "Đang bật" : "Đã tắt"}
                            </span>
                          </td>
                          <td className="text-right">
                            <div className="flex items-center justify-end gap-1">
                              <Button
                                type="button"
                                size="sm"
                                variant="outline"
                                onClick={() => void handleOpenUsages(v)}
                                title="Xem danh sách lượt sử dụng"
                              >
                                <Eye className="mr-1 h-3.5 w-3.5" />
                                Lượt dùng
                              </Button>
                              <Button
                                type="button"
                                size="sm"
                                variant="outline"
                                onClick={async () => {
                                  try {
                                    await voucherApi.setActive(v.id, !v.active);
                                    await loadVouchers();
                                  } catch (error) {
                                    toast.error(error instanceof Error ? error.message : "Lỗi cập nhật.");
                                  }
                                }}
                              >
                                {v.active ? "Tắt" : "Bật"}
                              </Button>
                              <Button
                                type="button"
                                size="icon"
                                variant="ghost"
                                onClick={async () => {
                                  if (!window.confirm(`Xóa voucher ${v.voucherCode}?`)) return;
                                  try {
                                    await voucherApi.remove(v.id);
                                    await loadVouchers();
                                    toast.success("Đã xóa voucher.");
                                  } catch (error) {
                                    toast.error(error instanceof Error ? error.message : "Không xóa được voucher.");
                                  }
                                }}
                              >
                                <Trash2 className="h-4 w-4 text-alert" />
                              </Button>
                            </div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
              </div>
            </TabsContent>

            {/* MINI-TAB 2: LƯỢT SỬ DỤNG VOUCHER (THAY THẾ POPUP) */}
            <TabsContent value="usages" className="space-y-4 mt-0">
              <div className="surface-card p-5 space-y-4">
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b pb-4">
                  <div className="flex items-center gap-3">
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={() => setVoucherSubTab("manage")}
                    >
                      <ArrowLeft className="mr-1.5 h-4 w-4" />
                      Về danh sách
                    </Button>
                    <div>
                      <h2 className="text-xl font-semibold">
                        Lịch sử sử dụng voucher:{" "}
                        <span className="font-mono text-primary">
                          {selectedVoucherForUsages?.voucherCode ?? "Chưa chọn"}
                        </span>
                      </h2>
                      {selectedVoucherForUsages && (
                        <p className="text-xs text-muted-foreground mt-0.5">
                          {selectedVoucherForUsages.title} · Đã dùng {selectedVoucherForUsages.usedQuantity} /{" "}
                          {selectedVoucherForUsages.totalQuantity} lượt
                        </p>
                      )}
                    </div>
                  </div>

                  {/* Dropdown chuyển nhanh voucher khác */}
                  <div className="flex items-center gap-2">
                    <span className="text-xs text-muted-foreground shrink-0">Chọn voucher khác:</span>
                    <select
                      className="rounded-md border bg-background px-3 py-1.5 text-xs font-mono font-medium"
                      value={selectedVoucherForUsages?.id ?? ""}
                      onChange={(e) => {
                        const targetVoucher = vouchersList.find((v) => v.id === e.target.value);
                        if (targetVoucher) {
                          void handleOpenUsages(targetVoucher);
                        }
                      }}
                    >
                      <option value="" disabled>
                        -- Chọn voucher --
                      </option>
                      {vouchersList.map((v) => (
                        <option key={v.id} value={v.id}>
                          {v.voucherCode} ({v.usedQuantity}/{v.totalQuantity})
                        </option>
                      ))}
                    </select>
                  </div>
                </div>

                {usagesLoading ? (
                  <div className="flex items-center justify-center py-16 text-muted-foreground">
                    <Loader2 className="mr-2 h-5 w-5 animate-spin" />
                    Đang tải dữ liệu lượt sử dụng voucher...
                  </div>
                ) : !selectedVoucherForUsages ? (
                  <div className="rounded-lg border border-dashed py-12 text-center text-sm text-muted-foreground">
                    Vui lòng chọn một voucher từ danh sách để xem lịch sử sử dụng.
                  </div>
                ) : usagesList.length === 0 ? (
                  <div className="rounded-lg border border-dashed py-12 text-center text-sm text-muted-foreground">
                    Voucher <strong>{selectedVoucherForUsages.voucherCode}</strong> chưa có lượt sử dụng nào.
                  </div>
                ) : (
                  <div className="overflow-x-auto rounded-lg border">
                    <table className="w-full text-sm">
                      <thead>
                        <tr className="border-b bg-muted/50 text-left text-xs uppercase text-muted-foreground">
                          <th className="py-2.5 px-3 font-semibold">STT</th>
                          <th className="py-2.5 px-3 font-semibold">Khách hàng (SĐT)</th>
                          <th className="py-2.5 px-3 font-semibold">Mã hóa đơn</th>
                          <th className="py-2.5 px-3 font-semibold text-right">Số tiền giảm</th>
                          <th className="py-2.5 px-3 font-semibold">Thời gian sử dụng</th>
                        </tr>
                      </thead>
                      <tbody>
                        {usagesList.map((item, idx) => (
                          <tr key={item.id} className="border-b last:border-0 hover:bg-muted/30">
                            <td className="py-2.5 px-3 tabular-nums">{idx + 1}</td>
                            <td className="py-2.5 px-3 font-mono font-medium">{item.customerPhone}</td>
                            <td className="py-2.5 px-3 font-mono text-xs">{item.invoiceCode}</td>
                            <td className="py-2.5 px-3 font-semibold text-primary tabular-nums text-right">
                              {formatVND(item.discountApplied)}
                            </td>
                            <td className="py-2.5 px-3 text-muted-foreground text-xs">
                              {item.usedAt ? new Date(item.usedAt).toLocaleString("vi-VN") : "—"}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            </TabsContent>
          </Tabs>
        </section>
      )}

      {activeTab === "loyalty" && (
        <section className="surface-card max-w-2xl space-y-5 p-5">
          <div>
            <h2 className="text-2xl font-medium">Cấu hình tích điểm & Quy đổi điểm</h2>
            <p className="mt-1 text-sm text-muted-foreground">
              Thiết lập tỷ lệ tích luỹ điểm thưởng khi thanh toán thành công và giá trị tiền tệ của 1 điểm khi khách tiêu dùng.
            </p>
          </div>
          <form
            className="space-y-4"
            onSubmit={async (e) => {
              e.preventDefault();
              const rate = Number(loyaltyRate);
              const pointVal = Number(pointValueVnd);

              if (!Number.isFinite(rate) || rate < 0 || rate > 100) {
                toast.error("Tỷ lệ tích điểm phải từ 0% đến 100%.");
                return;
              }
              if (!Number.isFinite(pointVal) || pointVal <= 0) {
                toast.error("Giá trị quy đổi 1 điểm phải lớn hơn 0 VNĐ.");
                return;
              }

              setLoyaltySaving(true);
              try {
                const saved = await paymentApi.updateLoyaltyRate({
                  earnRatePercent: rate,
                  pointValueVnd: pointVal,
                });
                setLoyaltyRate(String(saved.earnRatePercent));
                setPointValueVnd(String(saved.pointValueVnd));
                toast.success("Đã cập nhật cấu hình tích & đổi điểm thành công.");
              } catch (error) {
                toast.error(error instanceof Error ? error.message : "Không lưu được cấu hình.");
              } finally {
                setLoyaltySaving(false);
              }
            }}
          >
            <Field label="Tỷ lệ tích điểm thưởng (%)">
              <Input
                type="number"
                min="0"
                max="100"
                step="0.001"
                value={loyaltyRate}
                onChange={(e) => setLoyaltyRate(e.target.value)}
                required
              />
              <p className="text-xs text-muted-foreground">
                Ví dụ: hóa đơn 200.000 VNĐ với tỷ lệ 10% sẽ nhận được: 200.000 × 10% = 20.000 điểm.
              </p>
            </Field>

            <Field label="Giá trị quy đổi điểm (VNĐ / 1 điểm)">
              <Input
                type="number"
                min="1"
                step="1"
                value={pointValueVnd}
                onChange={(e) => setPointValueVnd(e.target.value)}
                required
              />
              <p className="text-xs text-muted-foreground">
                Quy ước giá trị: 1 điểm = {formatVND(Number(pointValueVnd) || 1)}. Ví dụ khách dùng 20.000 điểm với giá trị 2đ sẽ được trừ: 20.000 × 2 = 40.000 VNĐ.
              </p>
            </Field>

            <Button type="submit" disabled={loyaltySaving}>
              {loyaltySaving ? "Đang lưu..." : "Lưu cấu hình"}
            </Button>
          </form>
        </section>
      )}

      {activeTab === "overview" && (
        <>
          <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
            {kpis.map((k) => (
              <div key={k.l} className="surface-card p-5">
                <div className="flex items-center justify-between text-muted-foreground">
                  <span className="text-sm">{k.l}</span>
                  <k.i className="h-4 w-4" />
                </div>
                <p className="mt-3 text-2xl font-semibold tabular-nums">{k.v}</p>
              </div>
            ))}
          </div>
          <div className="grid gap-5 lg:grid-cols-3">
            <div className="surface-card p-5 lg:col-span-2">
              <h2 className="text-2xl font-medium">Doanh thu 7 ngày (triệu đ)</h2>
              <div className="mt-4 h-64">
                <ResponsiveContainer>
                  <BarChart data={data}>
                    <CartesianGrid vertical={false} stroke="var(--border)" />
                    <XAxis dataKey="d" tickLine={false} axisLine={false} fontSize={12} />
                    <YAxis tickLine={false} axisLine={false} fontSize={12} width={30} />
                    <Tooltip
                      cursor={{ fill: "var(--muted)" }}
                      formatter={(v: number) => [`${v} triệu đ`, "Doanh thu"]}
                    />
                    <Bar dataKey="v" fill="var(--primary)" radius={[6, 6, 0, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </div>
            <div className="surface-card p-5">
              <h2 className="text-2xl font-medium">Top 5 Món Bán Chạy</h2>
              <ol className="mt-4 space-y-3">
                {top.map((d, i) => (
                  <li key={d.id} className="flex items-center gap-3">
                    <span className="font-display w-6 text-2xl text-primary">{i + 1}</span>
                    <span className="flex-1 text-sm">{d.name}</span>
                    <span className="text-sm tabular-nums text-muted-foreground">
                      {d.sold} phần
                    </span>
                  </li>
                ))}
              </ol>
            </div>
          </div>
          <MenuManagement />
        </>
      )}
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