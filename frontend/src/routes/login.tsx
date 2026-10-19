import { createFileRoute, Link, useNavigate } from "@tanstack/react-router";
import { useState, type FormEvent } from "react";
import { ArrowLeft, Loader2, UtensilsCrossed } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { authApi } from "@/lib/account-api";
import { useEffect, useState, type FormEvent } from "react";
import { Loader2 } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { AuthLayout, Field, PasswordInput } from "@/components/hv/AuthLayout";
import { useAuth } from "@/lib/auth-store";
import { getHomeForRole } from "@/lib/role-routing";

export const Route = createFileRoute("/login")({
  head: () => ({ meta: [{ title: "Đăng nhập — Hương Việt" }] }),
  component: LoginPage,
});

type Errors = Partial<Record<"usernameOrEmail" | "password", string | undefined>>;

function LoginPage() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const navigate = useNavigate();
  const [f, setF] = useState({ usernameOrEmail: "", password: "" });
  const [errors, setErrors] = useState<Errors>({});
  const [formError, setFormError] = useState("");
  const [loading, setLoading] = useState(false);
  const set = (k: keyof typeof f, v: string) => { setF((p) => ({ ...p, [k]: v })); setErrors((p) => ({ ...p, [k]: undefined })); };

  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setError("");
    setSubmitting(true);
    try {
      // Role comes from the backend response; never grant a role from client input.
      const session = await authApi.login(email.trim(), password);
      void navigate({ to: getHomeForRole(session.role) });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Đăng nhập thất bại.");
    } finally {
      setSubmitting(false);
    }
  }

  return <main className="flex min-h-screen items-center justify-center bg-background px-4 py-10">
    <div className="w-full max-w-md rounded-2xl border bg-card p-8 shadow-sm">
      <Link to="/" className="mb-8 inline-flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground"><ArrowLeft className="h-4 w-4" /> Về trang khách hàng</Link>
      <div className="mb-6 flex items-center gap-3"><div className="flex h-11 w-11 items-center justify-center rounded-full bg-primary text-primary-foreground"><UtensilsCrossed className="h-5 w-5" /></div><div><p className="font-display text-xl font-semibold">HƯƠNG VIỆT</p><p className="text-xs text-muted-foreground">Đăng nhập hệ thống</p></div></div>
      <h1 className="text-2xl font-semibold">Đăng nhập</h1>
      <p className="mt-2 text-sm text-muted-foreground">Sử dụng tài khoản được cấp để truy cập hệ thống.</p>
      <form onSubmit={submit} className="mt-6 space-y-4">
        <div className="space-y-2"><Label htmlFor="email">Email / Tên đăng nhập</Label><input id="email" type="text" autoComplete="username" required value={email} onChange={e=>setEmail(e.target.value)} className="h-10 w-full rounded-md border border-input bg-background px-3 text-sm" /></div>
        <div className="space-y-2"><Label htmlFor="password">Mật khẩu</Label><input id="password" type="password" autoComplete="current-password" required value={password} onChange={e=>setPassword(e.target.value)} className="h-10 w-full rounded-md border border-input bg-background px-3 text-sm" /></div>
        {error && <p role="alert" className="rounded-md status-cancelled px-3 py-2 text-sm">{error}</p>}
        <Button type="submit" className="w-full" disabled={submitting}>{submitting && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}Đăng nhập</Button>
      </form>
    </div>
  </main>;
}
