import { createFileRoute, Link, useNavigate } from "@tanstack/react-router";
import { useEffect, useState, type FormEvent } from "react";
import { Loader2 } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { AuthLayout, Field, PasswordInput } from "@/components/hv/AuthLayout";
import { useAuth } from "@/lib/auth-store";
import { getHomeForRole } from "@/lib/role-routing";

export const Route = createFileRoute("/register")({
  head: () => ({ meta: [{ title: "Đăng ký — Hương Việt" }] }),
  component: RegisterPage,
});

type FormState = {
  username: string;
  email: string;
  phoneNumber: string;
  password: string;
  confirm: string;
};
type Errors = Partial<Record<keyof FormState, string | undefined>>;

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const PHONE_RE = /^(\+?84|0)\d{9,10}$/;

// Ràng buộc khớp với RegisterRequest ở backend.
function validate(f: FormState): Errors {
  const e: Errors = {};
  const username = f.username.trim();
  if (username.length < 3 || username.length > 50) e.username = "Tên đăng nhập từ 3 đến 50 ký tự";
  else if (/\s|@/.test(username)) e.username = "Không chứa khoảng trắng hoặc ký tự @";
  if (!EMAIL_RE.test(f.email.trim())) e.email = "Email không hợp lệ";
  else if (f.email.trim().length > 100) e.email = "Email tối đa 100 ký tự";
  if (!PHONE_RE.test(f.phoneNumber.replace(/[\s.-]/g, "")))
    e.phoneNumber = "Số điện thoại không hợp lệ";
  if (f.password.length < 8 || f.password.length > 100) e.password = "Mật khẩu từ 8 đến 100 ký tự";
  if (f.confirm !== f.password) e.confirm = "Mật khẩu xác nhận không khớp";
  return e;
}

function RegisterPage() {
  const { register, user, ready } = useAuth();
  const navigate = useNavigate();
  const [f, setF] = useState<FormState>({
    username: "",
    email: "",
    phoneNumber: "",
    password: "",
    confirm: "",
  });
  const [errors, setErrors] = useState<Errors>({});
  const [formError, setFormError] = useState("");
  const [loading, setLoading] = useState(false);
  const set = (k: keyof FormState, v: string) => {
    setF((p) => ({ ...p, [k]: v }));
    setErrors((p) => ({ ...p, [k]: undefined }));
  };

  useEffect(() => {
    if (ready && user && !loading) navigate({ to: getHomeForRole(user.role), replace: true });
  }, [ready, user, loading, navigate]);

  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const next = validate(f);
    setErrors(next);
    setFormError("");
    if (Object.keys(next).length) return;

    setLoading(true);
    try {
      const u = await register({
        username: f.username.trim(),
        email: f.email.trim(),
        phoneNumber: f.phoneNumber.replace(/[\s.-]/g, ""),
        password: f.password,
      });
      toast.success(`Tạo tài khoản thành công. Chào mừng ${u.username}!`);
    } catch (err) {
      setFormError(err instanceof Error ? err.message : "Đăng ký thất bại");
    } finally {
      setLoading(false);
    }
  }

  const describedBy = (k: keyof FormState) => (errors[k] ? `${k}-error` : undefined);

  return (
    <AuthLayout
      wide
      eyebrow="Thành viên Hương Việt"
      title="Tạo tài khoản"
      description="Đăng ký để đặt bàn nhanh hơn và tích luỹ điểm sau mỗi bữa ăn."
      footer={
        <>
          Đã có tài khoản?{" "}
          <Link to="/login" className="font-medium text-primary hover:underline">
            Đăng nhập
          </Link>
        </>
      }
    >
      <form onSubmit={submit} noValidate className="grid gap-4 sm:grid-cols-2">
        {formError && (
          <p role="alert" className="status-cancelled rounded-lg px-3 py-2 text-sm sm:col-span-2">
            {formError}
          </p>
        )}
        <Field id="username" label="Tên đăng nhập" error={errors.username}>
          <Input
            id="username"
            autoComplete="username"
            autoFocus
            className="bg-card"
            placeholder="nguyenvana"
            value={f.username}
            onChange={(e) => set("username", e.target.value)}
            aria-invalid={!!errors.username}
            aria-describedby={describedBy("username")}
          />
        </Field>
        <Field id="phoneNumber" label="Số điện thoại" error={errors.phoneNumber}>
          <Input
            id="phoneNumber"
            type="tel"
            inputMode="tel"
            autoComplete="tel"
            className="bg-card"
            placeholder="09xx xxx xxx"
            value={f.phoneNumber}
            onChange={(e) => set("phoneNumber", e.target.value)}
            aria-invalid={!!errors.phoneNumber}
            aria-describedby={describedBy("phoneNumber")}
          />
        </Field>
        <div className="sm:col-span-2">
          <Field id="email" label="Email" error={errors.email}>
            <Input
              id="email"
              type="email"
              autoComplete="email"
              className="bg-card"
              placeholder="email@vidu.com"
              value={f.email}
              onChange={(e) => set("email", e.target.value)}
              aria-invalid={!!errors.email}
              aria-describedby={describedBy("email")}
            />
          </Field>
        </div>
        <Field id="password" label="Mật khẩu" error={errors.password} hint="Tối thiểu 8 ký tự">
          <PasswordInput
            id="password"
            autoComplete="new-password"
            value={f.password}
            onChange={(e) => set("password", e.target.value)}
            aria-invalid={!!errors.password}
            aria-describedby={describedBy("password")}
          />
        </Field>
        <Field id="confirm" label="Xác nhận mật khẩu" error={errors.confirm}>
          <PasswordInput
            id="confirm"
            autoComplete="new-password"
            value={f.confirm}
            onChange={(e) => set("confirm", e.target.value)}
            aria-invalid={!!errors.confirm}
            aria-describedby={describedBy("confirm")}
          />
        </Field>
        <Button
          type="submit"
          disabled={loading}
          className="bg-primary hover:bg-primary-hover sm:col-span-2"
        >
          {loading && <Loader2 className="h-4 w-4 animate-spin" />}Tạo Tài Khoản
        </Button>
      </form>
    </AuthLayout>
  );
}
