import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { Banknote, Users, CheckCircle2, Award } from "lucide-react";
import { toast } from "sonner";
import { Switch } from "@/components/ui/switch";
import { formatVND, useRestaurant } from "@/lib/restaurant-store";
import { cn } from "@/lib/utils";

const week = [
  { d: "T2", v: 18.2 }, { d: "T3", v: 21.5 }, { d: "T4", v: 19.8 }, { d: "T5", v: 24.1 }, { d: "T6", v: 31.4 }, { d: "T7", v: 38.9 },
];

export function ManagerDashboard() {
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
    </div>
  );
}
