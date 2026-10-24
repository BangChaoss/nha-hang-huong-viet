import { createFileRoute, Link, useNavigate } from "@tanstack/react-router";
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
  const { login, user, ready } = useAuth();
  const navigate = useNavigate();
  const [f, setF] = useState({ usernameOrEmail: "", password: "" });
  const [errors, setErrors] = useState<Errors>({});
  const [formError, setFormError] = useState("");
  const [loading, setLoading] = useState(false);
  const set = (k: keyof typeof f, v: string) => { setF((p) => ({ ...p, [k]: v })); setErrors((p) => ({ ...p, [k]: undefined })); };

  useEffect(() => {
    if (ready && user && !loading) navigate({ to: getHomeForRole(user.role), replace: true });
  }, [ready, user, loading, navigate]);

  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const next: Errors = {};
    if (!f.usernameOrEmail.trim()) next.usernameOrEmail = "Vui lòng nhập tên đăng nhập hoặc email";
    if (!f.password) next.password = "Vui lòng nhập mật khẩu";
    setErrors(next);
    setFormError("");
    if (Object.keys(next).length) return;

    setLoading(true);
    try {
      const u = await login({ usernameOrEmail: f.usernameOrEmail.trim(), password: f.password });
      toast.success(`Chào mừng ${u.username} quay lại!`);
      void navigate({ to: getHomeForRole(u.role), replace: true });
    } catch (err) {
      setFormError(err instanceof Error ? err.message : "Đăng nhập thất bại");
    } finally {
      setLoading(false);
    }
  }

  return (
    <AuthLayout
      eyebrow="Chào mừng trở lại"
      title="Đăng nhập"
      description="Đăng nhập để đặt bàn, tích điểm và truy cập hệ thống."
      footer={<>Chưa có tài khoản? <Link to="/register" className="font-medium text-primary hover:underline">Đăng ký ngay</Link></>}
    >
      <form onSubmit={submit} noValidate className="space-y-4">
        {formError && <p role="alert" className="status-cancelled rounded-lg px-3 py-2 text-sm">{formError}</p>}
        <Field id="usernameOrEmail" label="Tên đăng nhập hoặc Email" error={errors.usernameOrEmail}>
          <Input id="usernameOrEmail" autoComplete="username" autoFocus className="bg-card" placeholder="nguyenvana hoặc email@vidu.com"
            value={f.usernameOrEmail} onChange={(e) => set("usernameOrEmail", e.target.value)}
            aria-invalid={!!errors.usernameOrEmail} aria-describedby={errors.usernameOrEmail ? "usernameOrEmail-error" : undefined} />
        </Field>
        <Field id="password" label="Mật khẩu" error={errors.password}
          action={<Link to="/forgot-password" className="text-xs font-medium text-primary hover:underline">Quên mật khẩu?</Link>}>
          <PasswordInput id="password" autoComplete="current-password"
            value={f.password} onChange={(e) => set("password", e.target.value)}
            aria-invalid={!!errors.password} aria-describedby={errors.password ? "password-error" : undefined} />
        </Field>
        <Button type="submit" disabled={loading} className="w-full bg-primary hover:bg-primary-hover">
          {loading && <Loader2 className="h-4 w-4 animate-spin" />}Đăng Nhập
        </Button>
      </form>
    </AuthLayout>
  );
}
