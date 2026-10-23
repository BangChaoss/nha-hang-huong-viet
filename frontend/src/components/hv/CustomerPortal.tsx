import { useState } from "react";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { formatVND, useRestaurant, type Category } from "@/lib/restaurant-store";
import { cn } from "@/lib/utils";
import { BookForm } from "@/features/reservation/components/book-form";
import { BookingHistoryList } from "@/features/reservation/components/booking-history-list";

const cats: ("Tất cả" | Category)[] = [
  "Tất cả",
  "Khai vị",
  "Món chính",
  "Lẩu & Nướng",
  "Tráng miệng & Đồ uống",
];

export function CustomerPortal() {
  const [tab, setTab] = useState("menu");
  return (
    <Tabs value={tab} onValueChange={setTab} className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="text-xs uppercase tracking-[0.2em] text-muted-foreground">
            Kính chào quý khách
          </p>
          <h1 className="text-4xl font-semibold">Ẩm thực Cố Đô</h1>
        </div>
        <TabsList>
          <TabsTrigger value="menu">Thực Đơn</TabsTrigger>
          <TabsTrigger value="book">Đặt Bàn Trước</TabsTrigger>
          <TabsTrigger value="history">Lịch Sử Đặt Bàn</TabsTrigger>
        </TabsList>
      </div>
      <TabsContent value="menu">
        <Menu />
      </TabsContent>
      <TabsContent value="book">
        <BookForm onDone={() => setTab("history")} />
      </TabsContent>
      <TabsContent value="history">
        <BookingHistoryList />
      </TabsContent>
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
          <button
            key={c}
            onClick={() => setCat(c)}
            className={cn(
              "rounded-full border px-4 py-1.5 text-sm transition",
              cat === c
                ? "border-moss bg-moss text-moss-foreground"
                : "bg-card hover:border-moss/40",
            )}
          >
            {c}
          </button>
        ))}
      </div>
      <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
        {list.map((d) => (
          <article key={d.id} className="surface-card overflow-hidden">
            <div className="relative aspect-[4/3] bg-muted">
              <img
                src={d.img}
                alt={d.name}
                loading="lazy"
                className={cn("h-full w-full object-cover", !d.inStock && "grayscale")}
              />
              {!d.inStock && (
                <span className="status-cancelled absolute left-3 top-3 rounded-full px-2.5 py-0.5 text-xs font-medium">
                  Hết món
                </span>
              )}
            </div>
            <div className="p-5">
              <p className="text-xs uppercase tracking-widest text-muted-foreground">
                {d.category}
              </p>
              <div className="mt-1 flex items-baseline justify-between gap-3">
                <h3 className="text-2xl font-semibold leading-tight">{d.name}</h3>
                <span className="shrink-0 font-medium tabular-nums text-primary">
                  {formatVND(d.price)}
                </span>
              </div>
              <p className="mt-2 text-sm text-muted-foreground">{d.desc}</p>
            </div>
          </article>
        ))}
      </div>
    </div>
  );
}
