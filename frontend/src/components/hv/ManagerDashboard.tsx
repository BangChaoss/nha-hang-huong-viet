import { useEffect, useState, type ReactNode } from "react";
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import {
  Banknote,
  Users,
  CheckCircle2,
  Award,
  ShieldCheck,
  TicketPercent,
  Plus,
  LayoutGrid,
  Trash2,
  ChefHat,
} from "lucide-react";
import { toast } from "sonner";
import { formatVND, useRestaurant } from "@/lib/restaurant-store";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import {
  voucherApi,
  type Voucher,
  type VoucherPayload,
  type DiscountType,
} from "@/lib/voucher-api";
import { paymentApi } from "@/lib/payment-api";
import { TableManagement } from "@/components/hv/TableManagement";
import { KitchenKDS } from "@/components/hv/KitchenKDS";
import { MenuManagement } from "@/features/menu/components/menu-management";

const week = [
  { d: "T2", v: 18.2 },
  { d: "T3", v: 21.5 },
  { d: "T4", v: 19.8 },
  { d: "T5", v: 24.1 },
  { d: "T6", v: 31.4 },
  { d: "T7", v: 38.9 },
];

type UserRole = "CUSTOMER" | "STAFF" | "CHEF" | "MANAGER";
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

export function ManagerDashboard() {
  const [activeTab, setActiveTab] = useState<
    "overview" | "permissions" | "vouchers" | "loyalty" | "tables" | "kitchen"
  >("overview");
  const [userRoles, setUserRoles] = useState([
    { phone: "0903123456", name: "Nguyễn Văn A", role: "CUSTOMER" as UserRole },
    { phone: "0903987654", name: "Trần Thị B", role: "STAFF" as UserRole },
    { phone: "0903555666", name: "Lê Văn C", role: "CHEF" as UserRole },
  ]);
  const [voucherForm, setVoucherForm] = useState(emptyVoucher);
  const [vouchersList, setVouchersList] = useState<Voucher[]>([]);
  const [voucherSaving, setVoucherSaving] = useState(false);
  const [voucherLoading, setVoucherLoading] = useState(false);
  const [loyaltyRate, setLoyaltyRate] = useState("5");
  const [loyaltySaving, setLoyaltySaving] = useState(false);
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

  useEffect(() => {
    void loadVouchers();
    void paymentApi
      .getLoyaltyRate()
      .then((value) => setLoyaltyRate(String(value.earnRatePercent)))
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
      <div>
        <p className="text-xs uppercase tracking-[0.2em] text-muted-foreground">
          Tổng quan vận hành
        </p>
        <h1 className="text-4xl font-semibold">Bảng điều khiển Quản lý</h1>
      </div>
      <div className="flex flex-wrap gap-2 border-b pb-3">
        <Button
          variant={activeTab === "overview" ? "default" : "outline"}
          onClick={() => setActiveTab("overview")}
        >
          Tổng quan
        </Button>
        <Button
          variant={activeTab === "permissions" ? "default" : "outline"}
          onClick={() => setActiveTab("permissions")}
        >
          <ShieldCheck className="mr-2 h-4 w-4" />
          Phân quyền người dùng
        </Button>
        <Button
          variant={activeTab === "vouchers" ? "default" : "outline"}
          onClick={() => setActiveTab("vouchers")}
        >
          <TicketPercent className="mr-2 h-4 w-4" />
          Voucher nhà hàng
        </Button>
        <Button
          variant={activeTab === "loyalty" ? "default" : "outline"}
          onClick={() => setActiveTab("loyalty")}
        >
          <Award className="mr-2 h-4 w-4" />
          Cấu hình tích điểm
        </Button>
        <Button
          variant={activeTab === "tables" ? "default" : "outline"}
          onClick={() => setActiveTab("tables")}
        >
          <LayoutGrid className="mr-2 h-4 w-4" />
          Sơ đồ & Bàn ăn
        </Button>
        <Button
          variant={activeTab === "kitchen" ? "default" : "outline"}
          onClick={() => setActiveTab("kitchen")}
        >
          <ChefHat className="mr-2 h-4 w-4" />
          Điều phối bếp
        </Button>
      </div>
      {activeTab === "tables" && <TableManagement />}
      {activeTab === "kitchen" && (
        // Đầu bếp chỉ xem phiếu; quản lý/quản trị cập nhật trạng thái nấu tại đây.
        <KitchenKDS />
      )}
      {activeTab === "permissions" && (
        <section className="surface-card space-y-4 p-5">
          <h2 className="text-2xl font-medium">Phân quyền người dùng</h2>
          <p className="text-sm text-muted-foreground">
            Gán vai trò theo số điện thoại. Bản frontend hiện lưu trạng thái trong bộ nhớ trình
            duyệt; cần API backend để lưu và áp dụng quyền thật.
          </p>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b text-left">
                  <th className="py-2">Họ tên</th>
                  <th>Số điện thoại</th>
                  <th>Vai trò</th>
                </tr>
              </thead>
              <tbody>
                {userRoles.map((u) => (
                  <tr key={u.phone} className="border-b last:border-0">
                    <td className="py-3">{u.name}</td>
                    <td>{u.phone}</td>
                    <td>
                      <select
                        className="rounded-md border bg-background p-2"
                        value={u.role}
                        onChange={(e) =>
                          setUserRoles((all) =>
                            all.map((x) =>
                              x.phone === u.phone ? { ...x, role: e.target.value as UserRole } : x,
                            ),
                          )
                        }
                      >
                        {(["CUSTOMER", "STAFF", "CHEF", "MANAGER"] as UserRole[]).map((r) => (
                          <option key={r} value={r}>
                            {r}
                          </option>
                        ))}
                      </select>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}
      {activeTab === "vouchers" && (
        <section className="space-y-6">
          <div className="surface-card space-y-4 p-5">
            <h2 className="text-2xl font-medium">Tạo voucher nhà hàng</h2>
            <p className="text-sm text-muted-foreground">
              Khách dùng voucher khi thanh toán cần nhập số điện thoại; giới hạn lượt dùng được tính
              theo số điện thoại.
            </p>
            <form
              className="grid gap-4 md:grid-cols-2 xl:grid-cols-3"
              onSubmit={async (e) => {
                e.preventDefault();
                const value = Number(voucherForm.discountValue);
                const maxDiscount = voucherForm.maxDiscountAmount.trim()
                  ? Number(voucherForm.maxDiscountAmount)
                  : null;
                const minOrder = Number(voucherForm.minOrderAmount);
                const totalQuantity = Number(voucherForm.totalQuantity);
                const perCustomer = Number(voucherForm.maxUsesPerCustomer);
                if (
                  !voucherForm.voucherCode.trim() ||
                  !voucherForm.title.trim() ||
                  !voucherForm.startDate ||
                  !voucherForm.endDate
                ) {
                  toast.error("Vui lòng nhập mã, tên chương trình và thời gian áp dụng.");
                  return;
                }
                if (voucherForm.endDate < voucherForm.startDate) {
                  toast.error("Ngày kết thúc không được trước ngày bắt đầu.");
                  return;
                }
                if (
                  !Number.isFinite(value) ||
                  value <= 0 ||
                  (voucherForm.discountType === "PERCENTAGE" && value > 100)
                ) {
                  toast.error("Giá trị giảm không hợp lệ.");
                  return;
                }
                if (
                  minOrder < 0 ||
                  totalQuantity < 1 ||
                  perCustomer < 1 ||
                  (maxDiscount !== null && maxDiscount < 0)
                ) {
                  toast.error(
                    "Kiểm tra giá trị đơn tối thiểu, số lượng phát hành, giới hạn dùng và mức giảm tối đa.",
                  );
                  return;
                }
                if (voucherForm.discountType === "FIXED_AMOUNT" && maxDiscount !== null) {
                  toast.error("Mức giảm tối đa chỉ áp dụng cho voucher giảm theo %.");
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
                  // Chỉ chọn ngày: backend chuẩn hoá thành 00:00:00 và 23:59:59.
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
                  toast.success("Đã tạo voucher và lưu vào database.");
                } catch (error) {
                  toast.error(error instanceof Error ? error.message : "Không tạo được voucher.");
                } finally {
                  setVoucherSaving(false);
                }
              }}
            >
              <Field label="Voucher Code">
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
              <Field label="Discount Type">
                <select
                  className="w-full rounded-md border bg-background p-2.5"
                  value={voucherForm.discountType}
                  onChange={(e) => {
                    const type = e.target.value as DiscountType;
                    updateVoucher("discountType", type);
                    if (type === "FIXED_AMOUNT") updateVoucher("maxDiscountAmount", "");
                    else updateVoucher("maxDiscountAmount", "100000");
                  }}
                >
                  <option value="PERCENTAGE">Giảm theo phần trăm (%)</option>
                  <option value="FIXED_AMOUNT">Giảm theo giá tiền (đ)</option>
                </select>
              </Field>
              <Field
                label={
                  voucherForm.discountType === "PERCENTAGE"
                    ? "Discount Value (%)"
                    : "Discount Value (đ)"
                }
              >
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
                <Field label="Mức giảm tối đa (đ)">
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
              <Field label="Giá trị đơn tối thiểu (đ)">
                <Input
                  required
                  type="number"
                  min="0"
                  step="1000"
                  value={voucherForm.minOrderAmount}
                  onChange={(e) => updateVoucher("minOrderAmount", e.target.value)}
                />
              </Field>
              <Field label="Ngày bắt đầu áp dụng">
                <Input
                  required
                  type="date"
                  value={voucherForm.startDate}
                  onChange={(e) => updateVoucher("startDate", e.target.value)}
                />
              </Field>
              <Field label="Ngày kết thúc áp dụng">
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
              <Field label="Giới hạn dùng mỗi khách (theo SĐT)">
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
              <Button
                type="button"
                variant="outline"
                onClick={() => void loadVouchers()}
                disabled={voucherLoading}
              >
                {voucherLoading ? "Đang tải..." : "Làm mới"}
              </Button>
            </div>
            {voucherLoading && vouchersList.length === 0 ? (
              <p className="text-sm text-muted-foreground">Đang tải dữ liệu...</p>
            ) : vouchersList.length === 0 ? (
              <p className="text-sm text-muted-foreground">Chưa có voucher nào được tạo.</p>
            ) : (
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b text-left">
                    <th className="py-2">Mã / Chương trình</th>
                    <th>Ưu đãi</th>
                    <th>Hiệu lực</th>
                    <th>Đã dùng / phát hành</th>
                    <th>Giới hạn/khách</th>
                    <th>Trạng thái</th>
                    <th>Thao tác</th>
                  </tr>
                </thead>
                <tbody>
                  {vouchersList.map((v) => (
                    <tr key={v.id} className="border-b last:border-0">
                      <td className="py-3">
                        <b>{v.voucherCode}</b>
                        <p>{v.title}</p>
                        <p className="max-w-xs text-xs text-muted-foreground">{v.description}</p>
                      </td>
                      <td>
                        {v.discountType === "PERCENTAGE"
                          ? `${v.discountValue}%`
                          : `${formatVND(v.discountValue)}`}
                        <p className="text-xs text-muted-foreground">
                          Đơn tối thiểu {formatVND(v.minOrderAmount)}
                        </p>
                        {v.maxDiscountAmount != null && (
                          <p className="text-xs text-muted-foreground">
                            Tối đa {formatVND(v.maxDiscountAmount)}
                          </p>
                        )}
                      </td>
                      <td>
                        {formatVoucherDate(v.startDate)} – {formatVoucherDate(v.endDate)}
                      </td>
                      <td>
                        {v.usedQuantity} / {v.totalQuantity}
                      </td>
                      <td>{v.maxUsesPerCustomer} lượt / SĐT</td>
                      <td>
                        <span className={v.active ? "text-green-600" : "text-muted-foreground"}>
                          {v.active ? "Đang bật" : "Đã tắt"}
                        </span>
                      </td>
                      <td>
                        <div className="flex gap-1">
                          <Button
                            type="button"
                            size="sm"
                            variant="outline"
                            onClick={async () => {
                              try {
                                await voucherApi.setActive(v.id, !v.active);
                                await loadVouchers();
                              } catch (error) {
                                toast.error(
                                  error instanceof Error
                                    ? error.message
                                    : "Không cập nhật được trạng thái.",
                                );
                              }
                            }}
                          >
                            {v.active ? "Tắt" : "Bật"}
                          </Button>
                          <Button
                            type="button"
                            size="icon"
                            variant="ghost"
                            aria-label="Xóa voucher"
                            onClick={async () => {
                              if (!window.confirm(`Xóa voucher ${v.voucherCode}?`)) return;
                              try {
                                await voucherApi.remove(v.id);
                                await loadVouchers();
                                toast.success("Đã xóa voucher.");
                              } catch (error) {
                                toast.error(
                                  error instanceof Error
                                    ? error.message
                                    : "Không xóa được voucher.",
                                );
                              }
                            }}
                          >
                            <Trash2 className="h-4 w-4" />
                          </Button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </section>
      )}
      {activeTab === "loyalty" && (
        <section className="surface-card max-w-2xl space-y-5 p-5">
          <div>
            <h2 className="text-2xl font-medium">Cấu hình tỷ lệ tích điểm</h2>
            <p className="mt-1 text-sm text-muted-foreground">
              Tỷ lệ được lưu trong database và áp dụng cho giao dịch VNPAY thành công. Điểm = số
              tiền thanh toán × tỷ lệ / 100.
            </p>
          </div>
          <form
            className="space-y-4"
            onSubmit={async (e) => {
              e.preventDefault();
              const rate = Number(loyaltyRate);
              if (!Number.isFinite(rate) || rate < 0 || rate > 100) {
                toast.error("Tỷ lệ tích điểm phải từ 0 đến 100%.");
                return;
              }
              setLoyaltySaving(true);
              try {
                const saved = await paymentApi.updateLoyaltyRate(rate);
                setLoyaltyRate(String(saved.earnRatePercent));
                toast.success("Đã cập nhật tỷ lệ tích điểm.");
              } catch (error) {
                toast.error(
                  error instanceof Error ? error.message : "Không lưu được cấu hình tích điểm.",
                );
              } finally {
                setLoyaltySaving(false);
              }
            }}
          >
            <Field label="Tỷ lệ tích điểm (%)">
              <Input
                type="number"
                min="0"
                max="100"
                step="0.001"
                value={loyaltyRate}
                onChange={(e) => setLoyaltyRate(e.target.value)}
                required
              />
            </Field>
            <p className="text-sm text-muted-foreground">
              Ví dụ: thanh toán 200.000đ với tỷ lệ 5% sẽ được cộng 10.000 điểm.
            </p>
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
