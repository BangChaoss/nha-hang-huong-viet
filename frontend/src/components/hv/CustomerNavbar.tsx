import { Link, useRouterState } from "@tanstack/react-router";
import { useState } from "react";
import {
  UtensilsCrossed,
  BookOpen,
  CalendarDays,
  History,
  MessageSquareHeart,
  Menu as MenuIcon,
  Home,
  PhoneCall,
  Sparkles,
} from "lucide-react";
import { AccountMenu } from "@/components/hv/AccountMenu";
import { Button } from "@/components/ui/button";
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetTrigger } from "@/components/ui/sheet";
import { cn } from "@/lib/utils";

interface CustomerNavbarProps {
  activeView?: string;
  onViewChange?: (view: string) => void;
}

export function CustomerNavbar({ activeView, onViewChange }: CustomerNavbarProps) {
  const routerState = useRouterState();
  const pathname = routerState.location.pathname;
  const [mobileOpen, setMobileOpen] = useState(false);

  const navLinks = [
    {
      title: "Trang Chủ",
      icon: Home,
      view: "home",
      to: "/",
      isActive: pathname === "/" && (!activeView || activeView === "home" || activeView === "menu"),
    },
    {
      title: "Thực Đơn",
      icon: BookOpen,
      view: "menu",
      to: "/",
      isActive: pathname === "/" && activeView === "menu",
      onClick: () => {
        if (onViewChange) {
          onViewChange("menu");
          const el = document.getElementById("menu-section");
          if (el) el.scrollIntoView({ behavior: "smooth" });
        }
      },
    },
    {
      title: "Đặt Bàn",
      icon: CalendarDays,
      view: "book",
      to: "/dat-ban",
      isActive: pathname === "/dat-ban" || (pathname === "/" && activeView === "book"),
    },
    {
      title: "Lịch Sử Đặt Bàn",
      icon: History,
      view: "history",
      to: "/?view=history",
      isActive: pathname === "/" && activeView === "history",
      onClick: () => {
        if (onViewChange) onViewChange("history");
      },
    },
    {
      title: "Đánh Giá",
      icon: MessageSquareHeart,
      view: "reviews",
      to: "/feedback",
      isActive: pathname === "/feedback" || (pathname === "/" && activeView === "reviews"),
    },
  ];

  const handleNavClick = (link: (typeof navLinks)[0]) => {
    if (pathname === "/" && onViewChange && link.onClick) {
      link.onClick();
    } else if (pathname === "/" && onViewChange) {
      onViewChange(link.view);
    }
    setMobileOpen(false);
  };

  return (
    <header className="sticky top-0 z-40 w-full border-b bg-background/95 backdrop-blur-md shadow-xs transition-all">
      <div className="mx-auto flex max-w-7xl items-center justify-between gap-4 px-4 py-3 sm:px-6">
        {/* Brand Logo */}
        <Link to="/" className="group flex items-center gap-3 transition-opacity hover:opacity-90">
          <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-primary text-primary-foreground shadow-sm transition-transform group-hover:scale-105">
            <UtensilsCrossed className="h-5 w-5" />
          </div>
          <div className="leading-tight">
            <div className="flex items-center gap-1.5">
              <span className="font-display text-2xl font-bold tracking-wide text-foreground sm:text-3xl">
                HƯƠNG VIỆT
              </span>
              <Sparkles className="h-4 w-4 text-primary/70 hidden sm:inline" />
            </div>
            <p className="text-[11px] font-semibold uppercase tracking-[0.22em] text-muted-foreground">
              Ẩm Thực Cố Đô Huế
            </p>
          </div>
        </Link>

        {/* Desktop Navigation Links */}
        <nav className="hidden lg:flex items-center gap-1 rounded-full border bg-muted/40 p-1.5 shadow-2xs">
          {navLinks.map((link) => {
            const Icon = link.icon;
            const active = link.isActive;

            if (pathname === "/" && onViewChange) {
              return (
                <button
                  key={link.title}
                  type="button"
                  onClick={() => handleNavClick(link)}
                  className={cn(
                    "flex items-center gap-2 rounded-full px-4 py-1.5 text-sm font-medium transition-all duration-200 cursor-pointer",
                    active
                      ? "bg-primary text-primary-foreground shadow-xs font-semibold"
                      : "text-muted-foreground hover:bg-background hover:text-foreground",
                  )}
                >
                  <Icon
                    className={cn(
                      "h-4 w-4",
                      active ? "text-primary-foreground" : "text-muted-foreground",
                    )}
                  />
                  <span>{link.title}</span>
                </button>
              );
            }

            return (
              <Link
                key={link.title}
                to={link.to}
                className={cn(
                  "flex items-center gap-2 rounded-full px-4 py-1.5 text-sm font-medium transition-all duration-200",
                  active
                    ? "bg-primary text-primary-foreground shadow-xs font-semibold"
                    : "text-muted-foreground hover:bg-background hover:text-foreground",
                )}
              >
                <Icon
                  className={cn(
                    "h-4 w-4",
                    active ? "text-primary-foreground" : "text-muted-foreground",
                  )}
                />
                <span>{link.title}</span>
              </Link>
            );
          })}
        </nav>

        {/* Right Section: CTA & Account Menu */}
        <div className="flex items-center gap-3">
          {pathname !== "/dat-ban" && (
            <Button
              asChild
              size="sm"
              className="hidden md:inline-flex rounded-full bg-primary font-semibold hover:bg-primary-hover shadow-xs gap-1.5 px-4"
            >
              <Link to="/dat-ban">
                <CalendarDays className="h-4 w-4" />
                <span>Đặt Bàn Ngay</span>
              </Link>
            </Button>
          )}

          <AccountMenu />

          {/* Mobile Menu Trigger */}
          <Sheet open={mobileOpen} onOpenChange={setMobileOpen}>
            <SheetTrigger asChild>
              <Button
                variant="outline"
                size="icon"
                className="lg:hidden rounded-lg border-border"
                aria-label="Mở menu điều hướng"
              >
                <MenuIcon className="h-5 w-5" />
              </Button>
            </SheetTrigger>
            <SheetContent
              side="right"
              className="w-[300px] sm:w-[350px] p-6 flex flex-col justify-between"
            >
              <div className="space-y-6">
                <SheetHeader className="text-left pb-4 border-b">
                  <div className="flex items-center gap-3">
                    <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-primary text-primary-foreground">
                      <UtensilsCrossed className="h-5 w-5" />
                    </div>
                    <div>
                      <SheetTitle className="font-display text-xl font-bold">HƯƠNG VIỆT</SheetTitle>
                      <p className="text-xs text-muted-foreground">Ẩm Thực Cố Đô</p>
                    </div>
                  </div>
                </SheetHeader>

                <nav className="flex flex-col space-y-2">
                  {navLinks.map((link) => {
                    const Icon = link.icon;
                    const active = link.isActive;

                    if (pathname === "/" && onViewChange) {
                      return (
                        <button
                          key={link.title}
                          type="button"
                          onClick={() => handleNavClick(link)}
                          className={cn(
                            "flex items-center gap-3.5 rounded-xl px-4 py-3 text-base font-medium transition-all text-left",
                            active
                              ? "bg-primary text-primary-foreground font-semibold shadow-xs"
                              : "text-foreground hover:bg-muted",
                          )}
                        >
                          <Icon className="h-5 w-5" />
                          <span>{link.title}</span>
                        </button>
                      );
                    }

                    return (
                      <Link
                        key={link.title}
                        to={link.to}
                        onClick={() => setMobileOpen(false)}
                        className={cn(
                          "flex items-center gap-3.5 rounded-xl px-4 py-3 text-base font-medium transition-all",
                          active
                            ? "bg-primary text-primary-foreground font-semibold shadow-xs"
                            : "text-foreground hover:bg-muted",
                        )}
                      >
                        <Icon className="h-5 w-5" />
                        <span>{link.title}</span>
                      </Link>
                    );
                  })}
                </nav>
              </div>

              {/* Mobile Drawer Footer */}
              <div className="border-t pt-4 space-y-3">
                <div className="flex items-center gap-2 text-sm text-muted-foreground">
                  <PhoneCall className="h-4 w-4 text-primary" />
                  <span>Hotline: 1900 6868</span>
                </div>
                <Button asChild className="w-full rounded-xl py-5 font-semibold text-base">
                  <Link to="/dat-ban" onClick={() => setMobileOpen(false)}>
                    Đặt Bàn Giữ Chỗ
                  </Link>
                </Button>
              </div>
            </SheetContent>
          </Sheet>
        </div>
      </div>
    </header>
  );
}
