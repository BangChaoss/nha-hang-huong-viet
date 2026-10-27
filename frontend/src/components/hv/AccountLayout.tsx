import { useEffect, type ReactNode } from "react";
import { Link, useNavigate } from "@tanstack/react-router";
import { Loader2, ShieldAlert, UtensilsCrossed } from "lucide-react";
import { AccountMenu } from "@/components/hv/AccountMenu";
import { getHomeForRole } from "@/lib/role-routing";
import { useSession, type AuthSession } from "@/lib/auth-session";
import { cn } from "@/lib/utils";

type Props = {
  /** Only users passing this check see the page; others get a "no permission" notice. */
  allow?: (session: AuthSession) => boolean;
  children: (session: AuthSession) => ReactNode;
};

/** Shell for account pages: header with pill navigation and a client-side login guard. */
export function AccountLayout({ allow, children }: Props) {
  const { session, ready } = useSession();
  const navigate = useNavigate();

  useEffect(() => {
    if (ready && !session) void navigate({ to: "/login" });
  }, [ready, session, navigate]);

  return (
    <div className="min-h-screen">
      <header className="sticky top-0 z-40 border-b bg-background/90 backdrop-blur">
        <div className="mx-auto flex max-w-7xl items-center justify-between gap-3 px-4 py-3 sm:px-6 lg:px-8 2xl:max-w-[1440px]">
          <Link
            to={session ? getHomeForRole(session.role) : "/"}
            className="flex items-center gap-3"
          >
            <div className="flex h-10 w-10 items-center justify-center rounded-full bg-primary text-primary-foreground">
              <UtensilsCrossed className="h-5 w-5" />
            </div>
            <div className="hidden leading-tight sm:block">
              <p className="font-display text-2xl font-semibold tracking-wide">HƯƠNG VIỆT</p>
              <p className="text-xs uppercase tracking-[0.25em] text-muted-foreground">
                Ẩm Thực Cố Đô
              </p>
            </div>
          </Link>
          <div className="flex items-center gap-2 sm:gap-3">
            <AccountMenu />
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-7xl px-4 py-6 sm:px-6 sm:py-10 lg:px-8 2xl:max-w-[1440px]">
        {!ready || !session ? (
          <div className="flex justify-center py-24 text-muted-foreground">
            <Loader2 className="h-6 w-6 animate-spin" />
          </div>
        ) : allow && !allow(session) ? (
          <div className="surface-card mx-auto max-w-md p-6 text-center sm:p-8">
            <ShieldAlert className="mx-auto mb-3 h-8 w-8 text-primary" />
            <h1 className="text-2xl font-semibold">Không có quyền truy cập</h1>
            <p className="mt-2 text-sm text-muted-foreground">
              Trang này chỉ dành cho Quản trị viên và Quản lý.
            </p>
            <Link to="/account" className="mt-5 inline-block text-sm font-medium text-primary">
              Về hồ sơ của tôi
            </Link>
          </div>
        ) : (
          children(session)
        )}
      </main>
    </div>
  );
}

/** Labelled form field used across account forms. */
export function FormField({
  label,
  htmlFor,
  error,
  hint,
  children,
}: {
  label: string;
  htmlFor: string;
  error?: string | undefined;
  hint?: string | undefined;
  children: ReactNode;
}) {
  return (
    <div className="space-y-1.5">
      <label htmlFor={htmlFor} className="text-sm font-medium">
        {label}
      </label>
      {children}
      {error ? (
        <p className="text-xs text-destructive">{error}</p>
      ) : (
        hint && <p className="text-xs text-muted-foreground">{hint}</p>
      )}
    </div>
  );
}
