import { useState, type ReactNode } from "react";
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { Banknote, Users, CheckCircle2, Award, ShieldCheck, TicketPercent, Plus, Trash2 } from "lucide-react";
import { toast } from "sonner";
import { Switch } from "@/components/ui/switch";
import { formatVND, useRestaurant } from "@/lib/restaurant-store";
import { cn } from "@/lib/utils";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";

const week = [
  { d: "T2", v: 18.2 }, { d: "T3", v: 21.5 }, { d: "T4", v: 19.8 }, { d: "T5", v: 24.1 }, { d: "T6", v: 31.4 }, { d: "T7", v: 38.9 },
];

type UserRole = "CUSTOMER" | "STAFF" | "CHEF" | "MANAGER";
type Voucher = {
  id: string; phone: string; code: string; title: string; description: string;
  discountType: "Percentage" | "Fixed_Amount" | "Free_Item"; discountValue: string; maxDiscount: string;
  minOrder: string; branches: string; items: string; tiers: string; days: string; startHour: string; endHour: string;
  channels: string; startAt: string; endAt: string; quota: string; perCustomer: string;
  status: "Draft" | "Active" | "Paused" | "Expired"; usedCount: number; createdBy: string; createdAt: string;
};
const emptyVoucher = (): Omit<Voucher, "id" | "status" | "usedCount" | "createdBy" | "createdAt"> => ({
  phone: "", code: "", title: "", description: "", discountType: "Percentage", discountValue: "20", maxDiscount: "",
  minOrder: "0", branches: "Toàn hệ thống", items: "Toàn bộ menu", tiers: "Tất cả", days: "Tất cả các ngày",
  startHour: "00:00", endHour: "23:59", channels: "Dine-in, Takeaway, Delivery", startAt: "", endAt: "", quota: "500", perCustomer: "1",
});

export function ManagerDashboard() {
  const [activeTab, setActiveTab] = useState<"overview" | "permissions" | "vouchers">("overview");
  const [userRoles, setUserRoles] = useState([
    { phone: "0903123456", name: "Nguyễn Văn A", role: "CUSTOMER" as UserRole },
    { phone: "0903987654", name: "Trần Thị B", role: "STAFF" as UserRole },
    { phone: "0903555666", name: "Lê Văn C", role: "CHEF" as UserRole },
  ]);
  const [voucherForm, setVoucherForm] = useState(emptyVoucher);
  const [vouchersList, setVouchersList] = useState<Voucher[]>([]);
  const updateVoucher = (key: keyof ReturnType<typeof emptyVoucher>, value: string) => setVoucherForm((v) => ({ ...v, [key]: value }));

  const { transactions, dishes, toggleStock, tables } = useRestaurant();
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
        <p className="text-xs uppercase tracking-[0.2em] text-muted-foreground">Tổng quan vận hành</p>
        <h1 className="text-4xl font-semibold">Bảng điều khiển Quản lý</h1>
      </div>
      <div className="flex flex-wrap gap-2 border-b pb-3">
        <Button variant={activeTab === "overview" ? "default" : "outline"} onClick={() => setActiveTab("overview")}>Tổng quan</Button>
        <Button variant={activeTab === "permissions" ? "default" : "outline"} onClick={() => setActiveTab("permissions")}><ShieldCheck className="mr-2 h-4 w-4" />Phân quyền người dùng</Button>
        <Button variant={activeTab === "vouchers" ? "default" : "outline"} onClick={() => setActiveTab("vouchers")}><TicketPercent className="mr-2 h-4 w-4" />Voucher nhà hàng</Button>
      </div>
      {activeTab === "permissions" && <section className="surface-card space-y-4 p-5">
        <h2 className="text-2xl font-medium">Phân quyền người dùng</h2>
        <p className="text-sm text-muted-foreground">Gán vai trò theo số điện thoại. Bản frontend hiện lưu trạng thái trong bộ nhớ trình duyệt; cần API backend để lưu và áp dụng quyền thật.</p>
        <div className="overflow-x-auto"><table className="w-full text-sm"><thead><tr className="border-b text-left"><th className="py-2">Họ tên</th><th>Số điện thoại</th><th>Vai trò</th></tr></thead><tbody>
          {userRoles.map((u) => <tr key={u.phone} className="border-b last:border-0"><td className="py-3">{u.name}</td><td>{u.phone}</td><td><select className="rounded-md border bg-background p-2" value={u.role} onChange={(e) => setUserRoles((all) => all.map((x) => x.phone === u.phone ? { ...x, role: e.target.value as UserRole } : x))}>{(["CUSTOMER","STAFF","CHEF","MANAGER"] as UserRole[]).map((r) => <option key={r} value={r}>{r}</option>)}</select></td></tr>)}
        </tbody></table></div>
      </section>}
      {activeTab === "vouchers" && <section className="space-y-6">
        <div className="surface-card space-y-4 p-5">
          <h2 className="text-2xl font-medium">Tạo voucher nhà hàng</h2>
          <p className="text-sm text-muted-foreground">Trường trạng thái, lượt dùng và thông tin tạo được hệ thống gán tự động khi tạo voucher.</p>
          <form className="grid gap-4 md:grid-cols-2 xl:grid-cols-3" onSubmit={(e) => {
            e.preventDefault();
            if (!voucherForm.phone.trim() || !voucherForm.code.trim() || !voucherForm.title.trim()) { toast.error("Vui lòng nhập số điện thoại, mã voucher và tiêu đề."); return; }
            if (vouchersList.some((v) => v.code.toLowerCase() === voucherForm.code.trim().toLowerCase())) { toast.error("Voucher Code đã tồn tại."); return; }
            setVouchersList((list) => [{ ...voucherForm, id: crypto.randomUUID(), code: voucherForm.code.trim().toUpperCase(), status: voucherForm.startAt && new Date(voucherForm.startAt) > new Date() ? "Draft" : "Active", usedCount: 0, createdBy: "MANAGER (phiên hiện tại)", createdAt: new Date().toLocaleString("vi-VN") }, ...list]);
            setVoucherForm(emptyVoucher()); toast.success("Đã tạo voucher trong phiên hiện tại.");
          }}>
            <Field label="Số điện thoại"><Input value={voucherForm.phone} onChange={(e) => updateVoucher("phone", e.target.value)} placeholder="09xxxxxxxx" /></Field>
            <Field label="Voucher Code"><Input value={voucherForm.code} onChange={(e) => updateVoucher("code", e.target.value)} placeholder="HUONGVIET20" /></Field>
            <Field label="Tên chương trình / Title"><Input value={voucherForm.title} onChange={(e) => updateVoucher("title", e.target.value)} placeholder="Ưu đãi khách hàng mới" /></Field>
            <Field label="Mô tả chi tiết"><Input value={voucherForm.description} onChange={(e) => updateVoucher("description", e.target.value)} placeholder="Điều kiện và nội dung ưu đãi" /></Field>
            <Field label="Discount Type"><select className="w-full rounded-md border bg-background p-2.5" value={voucherForm.discountType} onChange={(e) => updateVoucher("discountType", e.target.value)}><option value="Percentage">Percentage (%)</option><option value="Fixed_Amount">Fixed Amount (đ)</option><option value="Free_Item">Free Item</option></select></Field>
            <Field label="Discount Value"><Input type="number" min="0" value={voucherForm.discountValue} onChange={(e) => updateVoucher("discountValue", e.target.value)} /></Field>
            <Field label="Mức giảm tối đa (đ)"><Input type="number" min="0" value={voucherForm.maxDiscount} onChange={(e) => updateVoucher("maxDiscount", e.target.value)} placeholder="100000" /></Field>
            <Field label="Giá trị đơn tối thiểu (đ)"><Input type="number" min="0" value={voucherForm.minOrder} onChange={(e) => updateVoucher("minOrder", e.target.value)} /></Field>
            <Field label="Chi nhánh áp dụng"><Input value={voucherForm.branches} onChange={(e) => updateVoucher("branches", e.target.value)} placeholder="Toàn hệ thống hoặc chi nhánh A, B" /></Field>
            <Field label="Danh mục / món áp dụng"><Input value={voucherForm.items} onChange={(e) => updateVoucher("items", e.target.value)} /></Field>
            <Field label="Phân hạng khách hàng"><Input value={voucherForm.tiers} onChange={(e) => updateVoucher("tiers", e.target.value)} placeholder="Tất cả, VIP, Platinum..." /></Field>
            <Field label="Ngày áp dụng"><Input value={voucherForm.days} onChange={(e) => updateVoucher("days", e.target.value)} placeholder="T2-T5 hoặc tất cả" /></Field>
            <Field label="Giờ bắt đầu áp dụng"><Input type="time" value={voucherForm.startHour} onChange={(e) => updateVoucher("startHour", e.target.value)} /></Field>
            <Field label="Giờ kết thúc áp dụng"><Input type="time" value={voucherForm.endHour} onChange={(e) => updateVoucher("endHour", e.target.value)} /></Field>
            <Field label="Kênh đặt hàng"><Input value={voucherForm.channels} onChange={(e) => updateVoucher("channels", e.target.value)} placeholder="Dine-in, Takeaway, Delivery" /></Field>
            <Field label="Ngày giờ bắt đầu"><Input type="datetime-local" value={voucherForm.startAt} onChange={(e) => updateVoucher("startAt", e.target.value)} /></Field>
            <Field label="Ngày giờ kết thúc"><Input type="datetime-local" value={voucherForm.endAt} onChange={(e) => updateVoucher("endAt", e.target.value)} /></Field>
            <Field label="Tổng số lượng phát hành"><Input type="number" min="1" value={voucherForm.quota} onChange={(e) => updateVoucher("quota", e.target.value)} /></Field>
            <Field label="Giới hạn dùng mỗi khách"><Input type="number" min="1" value={voucherForm.perCustomer} onChange={(e) => updateVoucher("perCustomer", e.target.value)} /></Field>
            <div className="flex items-end"><Button type="submit" className="w-full"><Plus className="mr-2 h-4 w-4" />Tạo voucher</Button></div>
          </form>
        </div>
        <div className="surface-card overflow-x-auto p-5"><h2 className="mb-4 text-2xl font-medium">Danh sách voucher</h2>
          {vouchersList.length === 0 ? <p className="text-sm text-muted-foreground">Chưa có voucher nào được tạo.</p> : <table className="w-full text-sm"><thead><tr className="border-b text-left"><th className="py-2">Mã / Chương trình</th><th>SĐT</th><th>Ưu đãi</th><th>Hiệu lực</th><th>Đã dùng / quota</th><th>Trạng thái</th><th></th></tr></thead><tbody>{vouchersList.map((v) => <tr key={v.id} className="border-b last:border-0"><td className="py-3"><b>{v.code}</b><p className="text-muted-foreground">{v.title}</p></td><td>{v.phone}</td><td>{v.discountType}: {v.discountValue}{v.discountType === "Percentage" ? "%" : v.discountType === "Fixed_Amount" ? "đ" : ""}</td><td>{v.startAt || "Ngay"} – {v.endAt || "Không đặt hạn"}</td><td>{v.usedCount} / {v.quota}</td><td><select className="rounded-md border bg-background p-1.5" value={v.status} onChange={(e) => setVouchersList((all) => all.map((x) => x.id === v.id ? { ...x, status: e.target.value as Voucher["status"] } : x))}>{(["Draft","Active","Paused","Expired"] as const).map((st) => <option key={st}>{st}</option>)}</select><p className="mt-1 text-xs text-muted-foreground">Tạo bởi {v.createdBy} · {v.createdAt}</p></td><td><Button type="button" size="icon" variant="ghost" aria-label="Xóa voucher" onClick={() => setVouchersList((all) => all.filter((x) => x.id !== v.id))}><Trash2 className="h-4 w-4" /></Button></td></tr>)}</tbody></table>}
        </div>
      </section>}
      {activeTab === "overview" && <>
      <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
        {kpis.map((k) => (
          <div key={k.l} className="surface-card p-5">
            <div className="flex items-center justify-between text-muted-foreground"><span className="text-sm">{k.l}</span><k.i className="h-4 w-4" /></div>
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
                <Tooltip cursor={{ fill: "var(--muted)" }} formatter={(v: number) => [`${v} triệu đ`, "Doanh thu"]} />
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
                <span className="text-sm tabular-nums text-muted-foreground">{d.sold} phần</span>
              </li>
            ))}
          </ol>
        </div>
      </div>
      <div className="surface-card overflow-x-auto p-5">
        <h2 className="mb-4 text-2xl font-medium">Quản lý Thực đơn</h2>
        <table className="w-full text-sm">
          <thead><tr className="border-b text-left text-muted-foreground"><th className="py-2 font-medium">Món</th><th className="font-medium">Danh mục</th><th className="text-right font-medium">Giá</th><th className="text-right font-medium">Tình trạng</th></tr></thead>
          <tbody>
            {dishes.map((d) => (
              <tr key={d.id} className="border-b last:border-0">
                <td className="py-3 font-medium">{d.name}</td>
                <td className="text-muted-foreground">{d.category}</td>
                <td className="text-right tabular-nums">{formatVND(d.price)}</td>
                <td className="text-right">
                  <div className="flex items-center justify-end gap-3">
                    <span className={cn("rounded-full px-2.5 py-0.5 text-xs", d.inStock ? "status-available" : "status-cancelled")}>{d.inStock ? "Còn món" : "Hết món"}</span>
                    <Switch checked={d.inStock} onCheckedChange={() => { toggleStock(d.id); toast.success(`${d.name}: ${d.inStock ? "Hết món" : "Còn món"}`); }} />
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      </>}
    </div>
  );
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return <label className="block space-y-1.5 text-sm"><span className="font-medium">{label}</span>{children}</label>;
}
