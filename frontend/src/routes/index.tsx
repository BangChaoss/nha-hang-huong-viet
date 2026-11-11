import { createFileRoute, Link } from "@tanstack/react-router";
import { useState, useEffect } from "react";
import {
  CalendarDays,
  UtensilsCrossed,
  Sparkles,
  ShieldCheck,
  Award,
  Clock,
  MapPin,
  ChevronRight,
} from "lucide-react";
import { RestaurantProvider } from "@/lib/restaurant-store";
import { CustomerPortal } from "@/components/hv/CustomerPortal";
import { CustomerNavbar } from "@/components/hv/CustomerNavbar";
import { Button } from "@/components/ui/button";

export const Route = createFileRoute("/")({
  head: () => ({
    meta: [
      { title: "Hương Việt — Nhà hàng Ẩm Thực Cố Đô Huế" },
      {
        name: "description",
        content:
          "Thưởng thức tinh hoa ẩm thực Cố Đô Huế tại Nhà hàng Hương Việt. Đặt bàn trực tuyến nhanh chóng, khám phá thực đơn truyền thống đa dạng.",
      },
    ],
  }),
  component: HomePage,
});

function HomePage() {
  const [activeView, setActiveView] = useState<string>("menu");

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const viewParam = params.get("view");
    if (viewParam) {
      setActiveView(viewParam);
    }
  }, []);

  const handleViewChange = (view: string) => {
    setActiveView(view);
    const url = new URL(window.location.href);
    if (view === "menu" || view === "home") {
      url.searchParams.delete("view");
    } else {
      url.searchParams.set("view", view);
    }
    window.history.replaceState({}, "", url.toString());
  };

  return (
    <RestaurantProvider>
      <div className="min-h-screen bg-background flex flex-col">
        {/* Modern Customer Navbar with Mobile Drawer */}
        <CustomerNavbar activeView={activeView} onViewChange={handleViewChange} />

        {/* Hero Banner Section (Show when on Home / Menu view) */}
        {(activeView === "home" || activeView === "menu") && (
          <section className="relative overflow-hidden bg-stone-900 text-white min-h-[480px] sm:min-h-[540px] flex items-center">
            {/* Background Image with optimized Unsplash CDN */}
            <div className="absolute inset-0">
              <img
                src="https://images.unsplash.com/photo-1555396273-367ea4eb4db5?w=1920&q=80&auto=format&fit=crop"
                alt="Không gian nhà hàng Hương Việt"
                className="h-full w-full object-cover object-center brightness-60 scale-105 transform transition-transform duration-1000 ease-out"
              />
              {/* Refined gradient overlay */}
              <div className="absolute inset-0 bg-gradient-to-t from-background via-stone-950/70 to-stone-950/80" />
              <div className="absolute inset-0 bg-radial-[circle_at_top_right] from-primary/30 via-transparent to-transparent opacity-80" />
            </div>

            {/* Hero Content */}
            <div className="relative mx-auto max-w-7xl px-4 py-16 sm:px-6 sm:py-24 lg:py-28 w-full z-10">
              <div className="max-w-3xl space-y-6">
                <div className="inline-flex items-center gap-2 rounded-full border border-amber-300/30 bg-amber-500/10 px-4 py-1.5 text-xs sm:text-sm font-medium text-amber-200 backdrop-blur-md">
                  <Sparkles className="h-4 w-4 text-amber-300" />
                  <span>Tinh hoa Ẩm thực Cung đình Huế</span>
                </div>

                <h1 className="font-display text-4xl sm:text-5xl lg:text-6xl font-bold tracking-tight text-white leading-tight">
                  Hương Vị Cố Đô, <br className="hidden sm:inline" />
                  <span className="text-amber-300">Trọn Vẹn Từng Khoảnh Khắc</span>
                </h1>

                <p className="text-base sm:text-lg text-stone-200 max-w-2xl vn-text leading-relaxed font-light">
                  Chào mừng quý thực khách đến với{" "}
                  <strong className="font-semibold text-white">Hương Việt</strong>. Nơi gìn giữ và
                  truyền tải trọn vẹn nét tinh tế, tao nhã của món ăn xứ Huế trong không gian ấm
                  cúng, sang trọng và đậm đà bản sắc.
                </p>

                {/* Call to Actions */}
                <div className="flex flex-wrap items-center gap-4 pt-3">
                  <Button
                    asChild
                    size="lg"
                    className="rounded-full bg-primary hover:bg-primary-hover text-primary-foreground font-semibold px-7 py-6 text-base shadow-lg shadow-primary/25 gap-2 transition-all hover:scale-105"
                  >
                    <Link to="/dat-ban">
                      <CalendarDays className="h-5 w-5" />
                      <span>Đặt Bàn Giữ Chỗ</span>
                    </Link>
                  </Button>

                  <Button
                    asChild
                    variant="outline"
                    size="lg"
                    className="rounded-full border-white/30 bg-white/10 hover:bg-white/20 text-white font-medium px-7 py-6 text-base backdrop-blur-md gap-2"
                  >
                    <a href="#menu-section" onClick={() => handleViewChange("menu")}>
                      <UtensilsCrossed className="h-5 w-5 text-amber-300" />
                      <span>Khám Phá Thực Đơn</span>
                    </a>
                  </Button>
                </div>

                {/* Quick Value Badges */}
                <div className="grid grid-cols-2 sm:grid-cols-3 gap-4 pt-8 border-t border-white/15 text-stone-300 text-xs sm:text-sm">
                  <div className="flex items-center gap-2">
                    <ShieldCheck className="h-4 w-4 text-amber-400 shrink-0" />
                    <span>Nguyên liệu tươi sạch mỗi ngày</span>
                  </div>
                  <div className="flex items-center gap-2">
                    <Award className="h-4 w-4 text-amber-400 shrink-0" />
                    <span>Đầu bếp xứ Huế chuẩn vị</span>
                  </div>
                  <div className="flex items-center gap-2 col-span-2 sm:col-span-1">
                    <Clock className="h-4 w-4 text-amber-400 shrink-0" />
                    <span>Mở cửa 10:00 – 22:00</span>
                  </div>
                </div>
              </div>
            </div>
          </section>
        )}

        {/* Main Content Area */}
        <main
          id="menu-section"
          className="mx-auto max-w-7xl px-4 py-8 sm:px-6 sm:py-12 flex-1 w-full"
        >
          <CustomerPortal activeTab={activeView} onTabChange={handleViewChange} />
        </main>

        {/* Footer */}
        <footer className="border-t bg-card/60 py-12 mt-auto">
          <div className="mx-auto max-w-7xl px-4 sm:px-6">
            <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
              <div className="space-y-3">
                <div className="flex items-center gap-3">
                  <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-primary text-primary-foreground">
                    <UtensilsCrossed className="h-4 w-4" />
                  </div>
                  <span className="font-display text-xl font-bold">HƯƠNG VIỆT</span>
                </div>
                <p className="text-sm text-muted-foreground vn-text">
                  Ẩm thực Cố Đô — Tinh hoa món ăn truyền thống xứ Huế giữa lòng phố thị.
                </p>
              </div>

              <div className="space-y-2 text-sm text-muted-foreground">
                <h4 className="font-semibold text-foreground">Địa chỉ & Liên hệ</h4>
                <p className="flex items-center gap-2">
                  <MapPin className="h-4 w-4 text-primary shrink-0" />
                  <span>Khu Đô Thị Mới, TP. Huế, Thừa Thiên Huế</span>
                </p>
                <p className="flex items-center gap-2">
                  <Clock className="h-4 w-4 text-primary shrink-0" />
                  <span>10:00 - 22:00 (Thứ 2 - Chủ Nhật)</span>
                </p>
              </div>

              <div className="space-y-3">
                <h4 className="font-semibold text-foreground text-sm">Đặt Bàn Nhanh</h4>
                <p className="text-sm text-muted-foreground">
                  Quý khách vui lòng đặt bàn trước 30 phút để nhận được sự phục vụ chu đáo nhất.
                </p>
                <Button asChild size="sm" className="rounded-full gap-1.5">
                  <Link to="/dat-ban">
                    <span>Đặt Bàn Ngay</span>
                    <ChevronRight className="h-4 w-4" />
                  </Link>
                </Button>
              </div>
            </div>

            <div className="mt-8 pt-6 border-t border-border/60 text-center text-xs text-muted-foreground">
              © {new Date().getFullYear()} Nhà hàng Hương Việt. Mọi quyền được bảo lưu.
            </div>
          </div>
        </footer>
      </div>
    </RestaurantProvider>
  );
}
