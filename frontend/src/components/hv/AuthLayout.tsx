import { forwardRef, useState, type ComponentProps, type ReactNode } from "react";
import { Link } from "@tanstack/react-router";
import { ArrowLeft, CalendarCheck, Eye, EyeOff, Gift, Star, UtensilsCrossed } from "lucide-react";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { cn } from "@/lib/utils";

const perks = [
  { icon: CalendarCheck, text: "Đặt bàn trước và theo dõi trạng thái xác nhận" },
  { icon: Gift, text: "Tích luỹ điểm hoàn 5% cho mỗi hoá đơn" },
  { icon: Star, text: "Đánh giá bữa ăn và nhận phản hồi từ nhà hàng" },
];

export function BrandMark({ subtitle = "Ẩm Thực Cố Đô", inverted = false }: { subtitle?: string; inverted?: boolean }) {
  return (
    <Link to="/" className="flex items-center gap-3">
      <div className={cn("flex h-10 w-10 shrink-0 items-center justify-center rounded-full", inverted ? "bg-moss-foreground text-moss" : "bg-primary text-primary-foreground")}>
        <UtensilsCrossed className="h-5 w-5" />
      </div>
      <div className="leading-tight">
        <p className="font-display text-2xl font-semibold tracking-wide">HƯƠNG VIỆT</p>
        <p className={cn("text-xs uppercase tracking-[0.25em]", inverted ? "text-moss-foreground/70" : "text-muted-foreground")}>{subtitle}</p>
      </div>
    </Link>
  );
}

type AuthLayoutProps = {
  eyebrow: string;
  title: string;
  description?: ReactNode;
  children: ReactNode;
  footer?: ReactNode;
  wide?: boolean;
};

/** Khung chung cho các trang Đăng nhập / Đăng ký / Quên mật khẩu. */
export function AuthLayout({ eyebrow, title, description, children, footer, wide = false }: AuthLayoutProps) {
  return (
    <div className="flex min-h-screen bg-background">
      <aside className="relative hidden w-[44%] max-w-xl overflow-hidden bg-moss p-10 text-moss-foreground lg:flex lg:flex-col xl:p-14">
        <div aria-hidden className="pointer-events-none absolute -right-24 -top-24 h-80 w-80 rounded-full border border-moss-foreground/10" />
        <div aria-hidden className="pointer-events-none absolute -right-10 -top-10 h-52 w-52 rounded-full border border-moss-foreground/10" />
        <div aria-hidden className="pointer-events-none absolute -bottom-32 -left-20 h-96 w-96 rounded-full bg-primary/25 blur-3xl" />

        <BrandMark inverted />

        <div className="relative mt-16 space-y-8 xl:mt-20">
          <div>
            <p className="text-xs uppercase tracking-[0.2em] text-moss-foreground/70">Kính chào quý khách</p>
            <h2 className="mt-3 text-5xl font-semibold leading-[1.05]">Hương vị Cố Đô,<br />trọn vẹn từng bữa ăn.</h2>
          </div>
          <ul className="space-y-4">
            {perks.map(({ icon: Icon, text }) => (
              <li key={text} className="flex items-center gap-3 text-sm text-moss-foreground/90">
                <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-moss-foreground/10"><Icon className="h-4 w-4" /></span>
                {text}
              </li>
            ))}
          </ul>
        </div>
        <p className="relative mt-auto border-t border-moss-foreground/15 pt-6 text-xs text-moss-foreground/60">© {new Date().getFullYear()} Nhà hàng Hương Việt</p>
      </aside>

      <main className="flex flex-1 flex-col">
        <header className="flex items-center justify-between gap-4 px-4 py-4 sm:px-6 lg:justify-end">
          <div className="lg:hidden"><BrandMark /></div>
          <Link to="/" className="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground">
            <ArrowLeft className="h-4 w-4" /><span className="hidden sm:inline">Về trang chủ</span>
          </Link>
        </header>

        <div className="flex flex-1 items-start justify-center px-4 pb-10 pt-4 sm:items-center sm:px-6">
          <div className={cn("w-full", wide ? "max-w-xl" : "max-w-md")}>
            <div className="mb-6">
              <p className="text-xs uppercase tracking-[0.2em] text-muted-foreground">{eyebrow}</p>
              <h1 className="mt-1 text-4xl font-semibold">{title}</h1>
              {description && <p className="mt-2 text-sm text-muted-foreground">{description}</p>}
            </div>
            <div className="surface-card p-5 sm:p-6">{children}</div>
            {footer && <div className="mt-6 text-center text-sm text-muted-foreground">{footer}</div>}
          </div>
        </div>
      </main>
    </div>
  );
}

export function Field({ id, label, error, hint, children, action }: { id: string; label: string; error?: string | undefined; hint?: string | undefined; children: ReactNode; action?: ReactNode }) {
  return (
    <div className="space-y-1.5">
      <div className="flex items-center justify-between gap-2">
        <Label htmlFor={id}>{label}</Label>
        {action}
      </div>
      {children}
      {error ? <p id={`${id}-error`} className="text-xs text-destructive">{error}</p>
        : hint ? <p className="text-xs text-muted-foreground">{hint}</p> : null}
    </div>
  );
}

export const PasswordInput = forwardRef<HTMLInputElement, Omit<ComponentProps<"input">, "type">>(
  ({ className, ...props }, ref) => {
    const [show, setShow] = useState(false);
    return (
      <div className="relative">
        <Input ref={ref} type={show ? "text" : "password"} className={cn("bg-card pr-10", className)} {...props} />
        <button
          type="button"
          onClick={() => setShow((s) => !s)}
          aria-label={show ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
          className="absolute inset-y-0 right-0 flex w-10 items-center justify-center text-muted-foreground hover:text-foreground"
        >
          {show ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
        </button>
      </div>
    );
  },
);
PasswordInput.displayName = "PasswordInput";
