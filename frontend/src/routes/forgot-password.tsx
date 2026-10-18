import { createFileRoute, Link, useNavigate } from "@tanstack/react-router";
import { useEffect, useState, type FormEvent } from "react";
import { CheckCircle2, Loader2, MailCheck } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { InputOTP, InputOTPGroup, InputOTPSlot } from "@/components/ui/input-otp";
import { AuthLayout, Field, PasswordInput } from "@/components/hv/AuthLayout";
import { authApi } from "@/lib/auth-api";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/forgot-password")({
  head: () => ({ meta: [{ title: "Quên mật khẩu — Hương Việt" }] }),
  component: ForgotPasswordPage,
});

type Step = "email" | "reset" | "done";
const steps: { key: Step; label: string }[] = [
  { key: "email", label: "Email" },
  { key: "reset", label: "Mã OTP" },
  { key: "done", label: "Hoàn tất" },
];
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const RESEND_SECONDS = 60;

function ForgotPasswordPage() {
  const [step, setStep] = useState<Step>("email");
  const [email, setEmail] = useState("");

  const copy: Record<Step, { title: string; description: string }> = {
    email: { title: "Quên mật khẩu", description: "Nhập email đã đăng ký, chúng tôi sẽ gửi mã OTP gồm 6 chữ số để đặt lại mật khẩu." },
    reset: { title: "Đặt lại mật khẩu", description: "Nhập mã OTP trong email và mật khẩu mới của bạn." },
    done: { title: "Thành công", description: "Mật khẩu của bạn đã được cập nhật." },
  };

  return (
    <AuthLayout
      eyebrow="Khôi phục tài khoản"
      title={copy[step].title}
      description={copy[step].description}
      footer={step !== "done" && <>Nhớ mật khẩu rồi? <Link to="/login" className="font-medium text-primary hover:underline">Đăng nhập</Link></>}
    >
      <Stepper current={step} />
      {step === "email" && <EmailStep initial={email} onSent={(e) => { setEmail(e); setStep("reset"); }} />}
      {step === "reset" && <ResetStep email={email} onBack={() => setStep("email")} onDone={() => setStep("done")} />}
      {step === "done" && <DoneStep />}
    </AuthLayout>
  );
}

function Stepper({ current }: { current: Step }) {
  const idx = steps.findIndex((s) => s.key === current);
  return (
    <ol className="mb-6 flex items-center gap-2" aria-label="Các bước khôi phục">
      {steps.map((s, i) => (
        <li key={s.key} className="flex flex-1 items-center gap-2" aria-current={i === idx ? "step" : undefined}>
          <span className={cn(
            "flex h-6 w-6 shrink-0 items-center justify-center rounded-full border text-xs font-medium tabular-nums",
            i < idx && "border-moss bg-moss text-moss-foreground",
            i === idx && "border-primary bg-primary text-primary-foreground",
            i > idx && "bg-card text-muted-foreground",
          )}>{i + 1}</span>
          <span className={cn("hidden text-xs sm:inline", i === idx ? "font-medium text-foreground" : "text-muted-foreground")}>{s.label}</span>
          {i < steps.length - 1 && <span className={cn("h-px flex-1", i < idx ? "bg-moss" : "bg-border")} />}
        </li>
      ))}
    </ol>
  );
}

function EmailStep({ initial, onSent }: { initial: string; onSent: (email: string) => void }) {
  const [email, setEmail] = useState(initial);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const value = email.trim().toLowerCase();
    if (!EMAIL_RE.test(value)) { setError("Email không hợp lệ"); return; }
    setLoading(true);
    setError("");
    try {
      await authApi.forgotPassword(value);
      // Backend luôn trả 200 kể cả khi email không tồn tại (tránh dò tài khoản).
      toast.success("Nếu email đã đăng ký, mã OTP sẽ được gửi trong giây lát");
      onSent(value);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Không thể gửi mã OTP");
    } finally {
      setLoading(false);
    }
  }

  return (
    <form onSubmit={submit} noValidate className="space-y-4">
      <Field id="email" label="Email" error={error}>
        <Input id="email" type="email" autoComplete="email" autoFocus className="bg-card" placeholder="email@vidu.com"
          value={email} onChange={(e) => { setEmail(e.target.value); setError(""); }}
          aria-invalid={!!error} aria-describedby={error ? "email-error" : undefined} />
      </Field>
      <Button type="submit" disabled={loading} className="w-full bg-primary hover:bg-primary-hover">
        {loading && <Loader2 className="h-4 w-4 animate-spin" />}Gửi Mã OTP
      </Button>
    </form>
  );
}

type ResetErrors = Partial<Record<"otp" | "password" | "confirm", string | undefined>>;

function ResetStep({ email, onBack, onDone }: { email: string; onBack: () => void; onDone: () => void }) {
  const [otp, setOtp] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [errors, setErrors] = useState<ResetErrors>({});
  const [formError, setFormError] = useState("");
  const [loading, setLoading] = useState(false);
  const [cooldown, setCooldown] = useState(RESEND_SECONDS);
  const [resending, setResending] = useState(false);

  useEffect(() => {
    if (cooldown <= 0) return;
    const t = window.setTimeout(() => setCooldown((c) => c - 1), 1000);
    return () => window.clearTimeout(t);
  }, [cooldown]);

  async function resend() {
    setResending(true);
    try {
      await authApi.forgotPassword(email);
      toast.success("Đã gửi lại mã OTP");
      setCooldown(RESEND_SECONDS);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Không thể gửi lại mã");
    } finally {
      setResending(false);
    }
  }

  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const next: ResetErrors = {};
    if (!/^\d{6}$/.test(otp)) next.otp = "Vui lòng nhập đủ 6 chữ số";
    if (password.length < 8 || password.length > 100) next.password = "Mật khẩu từ 8 đến 100 ký tự";
    if (confirm !== password) next.confirm = "Mật khẩu xác nhận không khớp";
    setErrors(next);
    setFormError("");
    if (Object.keys(next).length) return;

    setLoading(true);
    try {
      await authApi.resetPassword({ token: otp, newPassword: password });
      toast.success("Đặt lại mật khẩu thành công");
      onDone();
    } catch (err) {
      setFormError(err instanceof Error ? err.message : "Không thể đặt lại mật khẩu");
    } finally {
      setLoading(false);
    }
  }

  return (
    <form onSubmit={submit} noValidate className="space-y-4">
      <div className="flex items-start gap-3 rounded-lg bg-accent p-3 text-sm text-accent-foreground">
        <MailCheck className="mt-0.5 h-4 w-4 shrink-0" />
        <p>Mã OTP đã được gửi tới <span className="font-medium break-all">{email}</span>.{" "}
          <button type="button" onClick={onBack} className="font-medium underline-offset-2 hover:underline">Đổi email</button></p>
      </div>
      {formError && <p role="alert" className="status-cancelled rounded-lg px-3 py-2 text-sm">{formError}</p>}

      <Field id="otp" label="Mã OTP" error={errors.otp}>
        <InputOTP id="otp" maxLength={6} inputMode="numeric" pattern="^\d*$" autoFocus autoComplete="one-time-code"
          value={otp} onChange={(v) => { setOtp(v); setErrors((p) => ({ ...p, otp: undefined })); }}
          containerClassName="w-full" aria-invalid={!!errors.otp}>
          <InputOTPGroup className="w-full gap-1.5 sm:w-auto sm:gap-2">
            {Array.from({ length: 6 }, (_, i) => (
              <InputOTPSlot key={i} index={i}
                className={cn("h-11 w-auto min-w-0 flex-1 rounded-md border bg-card text-base font-medium tabular-nums first:rounded-md last:rounded-md sm:h-12 sm:w-12 sm:flex-none",
                  errors.otp && "border-destructive")} />
            ))}
          </InputOTPGroup>
        </InputOTP>
      </Field>
      <div className="flex items-center justify-between text-xs text-muted-foreground">
        <span>Mã có hiệu lực trong 30 phút.</span>
        <button type="button" onClick={resend} disabled={cooldown > 0 || resending}
          className="font-medium text-primary hover:underline disabled:cursor-not-allowed disabled:text-muted-foreground disabled:no-underline">
          {resending ? "Đang gửi…" : cooldown > 0 ? `Gửi lại sau ${cooldown}s` : "Gửi lại mã"}
        </button>
      </div>

      <Field id="newPassword" label="Mật khẩu mới" error={errors.password} hint="Tối thiểu 8 ký tự">
        <PasswordInput id="newPassword" autoComplete="new-password"
          value={password} onChange={(e) => { setPassword(e.target.value); setErrors((p) => ({ ...p, password: undefined })); }}
          aria-invalid={!!errors.password} aria-describedby={errors.password ? "newPassword-error" : undefined} />
      </Field>
      <Field id="confirm" label="Xác nhận mật khẩu mới" error={errors.confirm}>
        <PasswordInput id="confirm" autoComplete="new-password"
          value={confirm} onChange={(e) => { setConfirm(e.target.value); setErrors((p) => ({ ...p, confirm: undefined })); }}
          aria-invalid={!!errors.confirm} aria-describedby={errors.confirm ? "confirm-error" : undefined} />
      </Field>
      <Button type="submit" disabled={loading} className="w-full bg-primary hover:bg-primary-hover">
        {loading && <Loader2 className="h-4 w-4 animate-spin" />}Đặt Lại Mật Khẩu
      </Button>
    </form>
  );
}

function DoneStep() {
  const navigate = useNavigate();
  return (
    <div className="flex flex-col items-center py-2 text-center">
      <div className="status-available flex h-14 w-14 items-center justify-center rounded-full"><CheckCircle2 className="h-7 w-7" /></div>
      <p className="mt-4 font-display text-2xl font-semibold">Mật khẩu đã được đặt lại</p>
      <p className="mt-1 text-sm text-muted-foreground">Hãy đăng nhập bằng mật khẩu mới để tiếp tục.</p>
      <Button onClick={() => navigate({ to: "/login" })} className="mt-6 w-full bg-primary hover:bg-primary-hover">Về Trang Đăng Nhập</Button>
    </div>
  );
}
