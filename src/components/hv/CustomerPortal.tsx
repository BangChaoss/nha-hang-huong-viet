import { useState } from "react";
import { Star, CalendarDays, Users } from "lucide-react";
import { toast } from "sonner";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";
import { formatVND, useRestaurant, type Booking, type BookingStatus, type Category } from "@/lib/restaurant-store";
import { cn } from "@/lib/utils";

const cats: ("Tất cả" | Category)[] = ["Tất cả", "Khai vị", "Món chính", "Lẩu & Nướng", "Tráng miệng & Đồ uống"];
const slots = ["11:00", "11:30", "12:00", "12:30", "13:00", "17:30", "18:00", "18:30", "19:00", "19:30", "20:00", "20:30"];
const bStatus: Record<BookingStatus, { l: string; cls: string }> = {
  pending: { l: "Chờ Xác Nhận", cls: "status-pending" },
  confirmed: { l: "Đã Xác Nhận", cls: "status-confirmed" },
  cancelled: { l: "Đã Huỷ", cls: "status-cancelled" },
  completed: { l: "Hoàn Thành", cls: "status-available" },
};
const selectCls = "h-9 w-full rounded-md border border-input bg-card px-3 text-sm";

export function CustomerPortal() {
  const [tab, setTab] = useState("menu");
  return (
    <Tabs value={tab} onValueChange={setTab} className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="text-xs uppercase tracking-[0.2em] text-muted-foreground">Kính chào quý khách</p>
          <h1 className="text-4xl font-semibold">Ẩm thực Cố Đô</h1>
        </div>
        <TabsList>
          <TabsTrigger value="menu">Thực Đơn</TabsTrigger>
          <TabsTrigger value="book">Đặt Bàn Trước</TabsTrigger>
          <TabsTrigger value="history">Lịch Sử & Đánh Giá</TabsTrigger>
        </TabsList>
      </div>
      <TabsContent value="menu"><Menu /></TabsContent>
      <TabsContent value="book"><BookForm onDone={() => setTab("history")} /></TabsContent>
      <TabsContent value="history"><History /></TabsContent>
    </Tabs>
  );
}

function Menu() {
  const { dishes } = useRestaurant();
  const [cat, setCat] = useState<(typeof cats)[number]>("Tất cả");
  const list = dishes.filter((d) => cat === "Tất cả" || d.category === cat);
  return (
    <div className="space-y-6">
      <div className="flex flex-wrap gap-2">
        {cats.map((c) => (
          <button key={c} onClick={() => setCat(c)} className={cn("rounded-full border px-4 py-1.5 text-sm transition", cat === c ? "border-moss bg-moss text-moss-foreground" : "bg-card hover:border-moss/40")}>{c}</button>
        ))}
      </div>
      <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
        {list.map((d) => (
          <article key={d.id} className="surface-card overflow-hidden">
            <div className="relative aspect-[4/3] bg-muted">
              <img src={d.img} alt={d.name} loading="lazy" className={cn("h-full w-full object-cover", !d.inStock && "grayscale")} />
              {!d.inStock && <span className="status-cancelled absolute left-3 top-3 rounded-full px-2.5 py-0.5 text-xs font-medium">Hết món</span>}
            </div>
            <div className="p-5">
              <p className="text-xs uppercase tracking-widest text-muted-foreground">{d.category}</p>
              <div className="mt-1 flex items-baseline justify-between gap-3">
                <h3 className="text-2xl font-semibold leading-tight">{d.name}</h3>
                <span className="shrink-0 font-medium tabular-nums text-primary">{formatVND(d.price)}</span>
              </div>
              <p className="mt-2 text-sm text-muted-foreground">{d.desc}</p>
            </div>
          </article>
        ))}
      </div>
    </div>
  );
}

function BookForm({ onDone }: { onDone: () => void }) {
  const { addBooking } = useRestaurant();
  const [f, setF] = useState({ date: "", time: "19:00", party: 2, name: "", phone: "", note: "" });
  const set = (k: keyof typeof f, v: string | number) => setF((p) => ({ ...p, [k]: v }));
  return (
    <form
      className="surface-card mx-auto grid max-w-2xl gap-5 p-6 sm:grid-cols-2"
      onSubmit={(e) => {
        e.preventDefault();
        if (!f.date || !f.name || !f.phone) { toast.error("Vui lòng điền ngày, họ tên và số điện thoại"); return; }
        addBooking(f); toast.success("Đã gửi yêu cầu đặt bàn — đang chờ xác nhận"); onDone();
      }}
    >
      <h2 className="text-3xl font-semibold sm:col-span-2">Đặt bàn trước</h2>
      <div className="space-y-1.5"><Label>Ngày</Label><Input type="date" value={f.date} onChange={(e) => set("date", e.target.value)} /></div>
      <div className="space-y-1.5"><Label>Giờ</Label>
        <select className={selectCls} value={f.time} onChange={(e) => set("time", e.target.value)}>{slots.map((s) => <option key={s}>{s}</option>)}</select></div>
      <div className="space-y-1.5"><Label>Số khách (1–15)</Label>
        <select className={selectCls} value={f.party} onChange={(e) => set("party", Number(e.target.value))}>
          {Array.from({ length: 15 }, (_, i) => <option key={i} value={i + 1}>{i + 1} khách</option>)}</select></div>
      <div className="space-y-1.5"><Label>Số điện thoại</Label><Input value={f.phone} onChange={(e) => set("phone", e.target.value)} placeholder="09xx xxx xxx" /></div>
      <div className="space-y-1.5 sm:col-span-2"><Label>Họ tên</Label><Input value={f.name} onChange={(e) => set("name", e.target.value)} /></div>
      <div className="space-y-1.5 sm:col-span-2"><Label>Yêu cầu ăn uống đặc biệt</Label>
        <Textarea value={f.note} onChange={(e) => set("note", e.target.value)} placeholder="Ăn chay, dị ứng đậu phộng, sinh nhật…" /></div>
      <Button type="submit" className="bg-primary hover:bg-primary-hover sm:col-span-2">Gửi Yêu Cầu Đặt Bàn</Button>
    </form>
  );
}

function History() {
  const { bookings } = useRestaurant();
  return <div className="mx-auto max-w-3xl space-y-4">{bookings.map((b) => <BookingCard key={b.id} b={b} />)}</div>;
}

function BookingCard({ b }: { b: Booking }) {
  const { reviewBooking } = useRestaurant();
  const [stars, setStars] = useState(0);
  const [comment, setComment] = useState("");
  return (
    <div className="surface-card p-5">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-4 text-sm">
          <span className="flex items-center gap-1.5"><CalendarDays className="h-4 w-4 text-muted-foreground" />{new Date(b.date).toLocaleDateString("vi-VN")} · {b.time}</span>
          <span className="flex items-center gap-1.5"><Users className="h-4 w-4 text-muted-foreground" />{b.party} khách</span>
        </div>
        <div className="flex gap-2">
          {b.review && <span className="status-confirmed rounded-full px-2.5 py-0.5 text-xs font-medium">Đã Đánh Giá</span>}
          <span className={cn("rounded-full px-2.5 py-0.5 text-xs font-medium", bStatus[b.status].cls)}>{bStatus[b.status].l}</span>
        </div>
      </div>
      {b.note && <p className="mt-2 text-sm text-muted-foreground">Ghi chú: {b.note}</p>}
      {b.status === "completed" && (b.review ? (
        <div className="mt-4 space-y-2 border-t pt-4">
          <div className="flex">{[1, 2, 3, 4, 5].map((i) => <Star key={i} className={cn("h-4 w-4", i <= b.review!.stars ? "fill-primary text-primary" : "text-border")} />)}</div>
          <p className="text-sm">“{b.review.comment}”</p>
          <p className="rounded-lg bg-accent p-3 text-sm text-accent-foreground"><span className="font-medium">Quản lý phản hồi:</span> {b.review.reply}</p>
        </div>
      ) : (
        <div className="mt-4 space-y-3 border-t pt-4">
          <p className="text-sm font-medium">Đánh giá bữa ăn của bạn</p>
          <div className="flex gap-1">{[1, 2, 3, 4, 5].map((i) => (
            <button key={i} type="button" onClick={() => setStars(i)} aria-label={`${i} sao`}>
              <Star className={cn("h-6 w-6 transition", i <= stars ? "fill-primary text-primary" : "text-border hover:text-primary/50")} />
            </button>))}</div>
          <Textarea value={comment} onChange={(e) => setComment(e.target.value)} placeholder="Chia sẻ cảm nhận…" />
          <Button size="sm" className="bg-primary hover:bg-primary-hover" disabled={!stars}
            onClick={() => { reviewBooking(b.id, stars, comment); toast.success("Cảm ơn bạn đã đánh giá!"); }}>Gửi Đánh Giá</Button>
        </div>
      ))}
    </div>
  );
}
