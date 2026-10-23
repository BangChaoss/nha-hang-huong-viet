import { useEffect, useState, type ChangeEvent, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  CalendarDays,
  Eye,
  EyeOff,
  KeyRound,
  Loader2,
  Mail,
  PencilLine,
  Phone,
  RefreshCw,
  ShieldCheck,
} from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { FormField } from "@/components/hv/AccountLayout";
import { initials } from "@/components/hv/AccountMenu";
import {
  RoleBadge,
  StatusBadge,
  validateAccountFields,
  type AccountFields,
} from "@/components/hv/AccountBadges";
import {
  ApiError,
  formatDate,
  profileApi,
  type ChangePasswordPayload,
  type UserAccount,
} from "@/lib/account-api";
import { cn } from "@/lib/utils";
import { LoyaltyWallet } from "@/components/hv/LoyaltyWallet";

const PROFILE_KEY = ["account", "profile"] as const;

export function ProfilePage() {
  const profile = useQuery({ queryKey: PROFILE_KEY, queryFn: profileApi.get });

  return (
    <div className="space-y-6">
      <div>
        <p className="text-xs uppercase tracking-[0.2em] text-muted-foreground">Tài khoản</p>
        <h1 className="text-3xl font-semibold sm:text-4xl">Hồ sơ cá nhân</h1>
      </div>
      {profile.isPending && (
        <div className="flex justify-center py-20 text-muted-foreground">
          <Loader2 className="h-6 w-6 animate-spin" />
        </div>
      )}
      {profile.isError && (
        <div className="surface-card p-8 text-center">
          <p className="text-sm text-muted-foreground">{profile.error.message}</p>
          <Button variant="outline" className="mt-4" onClick={() => profile.refetch()}>
            <RefreshCw className="mr-2 h-4 w-4" />
            Thử lại
          </Button>
        </div>
      )}
      {profile.data && (
        <div className="grid gap-6 lg:grid-cols-[300px_1fr] xl:grid-cols-[340px_1fr] 2xl:grid-cols-[380px_1fr]">
          <ProfileSummary user={profile.data} />
          <div className="space-y-6">
            <ProfileForm user={profile.data} />
            <LoyaltyWallet />
            <PasswordForm />
          </div>
        </div>
      )}
    </div>
  );
}

function ProfileSummary({ user }: { user: UserAccount }) {
  const rows = [
    { icon: Mail, label: "Email", value: user.email },
    { icon: Phone, label: "Điện thoại", value: user.phoneNumber },
    { icon: CalendarDays, label: "Thành viên từ", value: formatDate(user.createdAt) },
    { icon: RefreshCw, label: "Cập nhật", value: formatDate(user.updatedAt) },
  ];
  return (
    <aside className="surface-card h-fit p-5 sm:p-6 lg:sticky lg:top-24">
      <div className="flex flex-col items-center text-center">
        <div className="flex h-16 w-16 items-center sm:h-20 sm:w-20 justify-center rounded-full bg-primary font-display text-2xl font-semibold text-primary-foreground sm:text-3xl shadow-sm">
          {initials(user.username)}
        </div>
        <h2 className="mt-4 break-all text-2xl font-medium sm:text-3xl">{user.username}</h2>
        <div className="mt-2 flex flex-wrap justify-center gap-2">
          <RoleBadge role={user.role} />
          <StatusBadge status={user.status} />
        </div>
      </div>
      <dl className="mt-6 space-y-3 border-t pt-5 text-sm">
        {rows.map(({ icon: Icon, label, value }) => (
          <div key={label} className="flex items-center justify-between gap-3">
            <dt className="flex shrink-0 items-center gap-2 text-muted-foreground">
              <Icon className="h-4 w-4" />
              {label}
            </dt>
            <dd className="min-w-0 truncate text-right" title={value}>
              {value}
            </dd>
          </div>
        ))}
      </dl>
    </aside>
  );
}

function ProfileForm({ user }: { user: UserAccount }) {
  const queryClient = useQueryClient();
  const fromUser = (u: UserAccount): AccountFields => ({
    username: u.username,
    email: u.email,
    phoneNumber: u.phoneNumber,
  });
  const [editing, setEditing] = useState(false);
  const [form, setForm] = useState(() => fromUser(user));
  const [errors, setErrors] = useState<Partial<Record<keyof AccountFields, string>>>({});

  useEffect(() => {
    if (!editing) setForm(fromUser(user));
  }, [user, editing]);

  const save = useMutation({
    mutationFn: profileApi.update,
    onSuccess: (updated) => {
      queryClient.setQueryData(PROFILE_KEY, updated);
      setEditing(false);
      toast.success("Đã cập nhật thông tin cá nhân.");
    },
    onError: (error) => {
      if (error instanceof ApiError) setErrors(error.fieldErrors);
      toast.error(error.message);
    },
  });

  function submit(e: FormEvent) {
    e.preventDefault();
    const found = validateAccountFields(form);
    setErrors(found);
    if (Object.keys(found).length) return;
    save.mutate({
      username: form.username.trim(),
      email: form.email.trim(),
      phoneNumber: form.phoneNumber.trim(),
    });
  }

  const set = (key: keyof AccountFields) => (e: ChangeEvent<HTMLInputElement>) =>
    setForm((f) => ({ ...f, [key]: e.target.value }));

  return (
    <section className="surface-card p-5 sm:p-6">
      <div className="mb-5 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="text-xl font-medium sm:text-2xl">Thông tin cá nhân</h2>
          <p className="text-sm text-muted-foreground">
            Tên đăng nhập, email và số điện thoại liên hệ.
          </p>
        </div>
        {!editing && (
          <Button variant="outline" size="sm" onClick={() => setEditing(true)}>
            <PencilLine className="mr-2 h-4 w-4" />
            Chỉnh sửa
          </Button>
        )}
      </div>
      <form onSubmit={submit} className="space-y-4" noValidate>
        <FormField label="Username" htmlFor="p-username" error={errors.username}>
          <Input
            id="p-username"
            autoComplete="username"
            disabled={!editing}
            value={form.username}
            onChange={set("username")}
          />
        </FormField>
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField label="Email" htmlFor="p-email" error={errors.email}>
            <Input
              id="p-email"
              type="email"
              autoComplete="email"
              disabled={!editing}
              value={form.email}
              onChange={set("email")}
            />
          </FormField>
          <FormField label="Số điện thoại" htmlFor="p-phone" error={errors.phoneNumber}>
            <Input
              id="p-phone"
              inputMode="numeric"
              maxLength={10}
              autoComplete="tel"
              disabled={!editing}
              value={form.phoneNumber}
              onChange={set("phoneNumber")}
            />
          </FormField>
        </div>
        {editing && (
          <div className="flex justify-end gap-2 border-t pt-4 [&>button]:flex-1 sm:[&>button]:flex-none">
            <Button
              type="button"
              variant="outline"
              disabled={save.isPending}
              onClick={() => {
                setEditing(false);
                setErrors({});
              }}
            >
              Huỷ
            </Button>
            <Button type="submit" disabled={save.isPending}>
              {save.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}Lưu thay đổi
            </Button>
          </div>
        )}
      </form>
    </section>
  );
}

const emptyPassword: ChangePasswordPayload = {
  currentPassword: "",
  newPassword: "",
  confirmPassword: "",
};

function passwordStrength(pw: string) {
  let score = 0;
  if (pw.length >= 8) score++;
  if (/[A-Z]/.test(pw) && /[a-z]/.test(pw)) score++;
  if (/\d/.test(pw)) score++;
  if (/[^A-Za-z0-9]/.test(pw)) score++;
  return { score, label: ["Rất yếu", "Yếu", "Trung bình", "Khá", "Mạnh"][score] };
}

function PasswordForm() {
  const [form, setForm] = useState(emptyPassword);
  const [show, setShow] = useState(false);
  const [errors, setErrors] = useState<Partial<Record<keyof ChangePasswordPayload, string>>>({});
  const strength = passwordStrength(form.newPassword);

  const change = useMutation({
    mutationFn: profileApi.changePassword,
    onSuccess: (res) => {
      setForm(emptyPassword);
      toast.success(res.message);
    },
    onError: (error) => {
      if (error instanceof ApiError) setErrors(error.fieldErrors);
      toast.error(error.message);
    },
  });

  function submit(e: FormEvent) {
    e.preventDefault();
    const found: typeof errors = {};
    if (!form.currentPassword) found.currentPassword = "Mật khẩu hiện tại không được để trống";
    if (form.newPassword.length < 8 || form.newPassword.length > 100)
      found.newPassword = "Mật khẩu mới phải từ 8 đến 100 ký tự";
    else if (form.newPassword === form.currentPassword)
      found.newPassword = "Mật khẩu mới không được trùng với mật khẩu cũ";
    if (form.confirmPassword !== form.newPassword)
      found.confirmPassword = "Xác nhận mật khẩu mới không khớp";
    setErrors(found);
    if (!Object.keys(found).length) change.mutate(form);
  }

  const set = (key: keyof ChangePasswordPayload) => (e: ChangeEvent<HTMLInputElement>) =>
    setForm((f) => ({ ...f, [key]: e.target.value }));
  const type = show ? "text" : "password";

  return (
    <section className="surface-card p-5 sm:p-6">
      <div className="mb-5 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="text-xl font-medium sm:text-2xl">Đổi mật khẩu</h2>
          <p className="text-sm text-muted-foreground">Mật khẩu mới tối thiểu 8 ký tự.</p>
        </div>
        <button
          type="button"
          onClick={() => setShow((s) => !s)}
          className="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground"
        >
          {show ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
          {show ? "Ẩn" : "Hiện"}
        </button>
      </div>
      <form onSubmit={submit} className="space-y-4" noValidate>
        <FormField label="Mật khẩu hiện tại" htmlFor="pw-current" error={errors.currentPassword}>
          <Input
            id="pw-current"
            type={type}
            autoComplete="current-password"
            value={form.currentPassword}
            onChange={set("currentPassword")}
          />
        </FormField>
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField label="Mật khẩu mới" htmlFor="pw-new" error={errors.newPassword}>
            <Input
              id="pw-new"
              type={type}
              autoComplete="new-password"
              value={form.newPassword}
              onChange={set("newPassword")}
            />
          </FormField>
          <FormField
            label="Xác nhận mật khẩu mới"
            htmlFor="pw-confirm"
            error={errors.confirmPassword}
          >
            <Input
              id="pw-confirm"
              type={type}
              autoComplete="new-password"
              value={form.confirmPassword}
              onChange={set("confirmPassword")}
            />
          </FormField>
        </div>
        {form.newPassword && (
          <div className="space-y-1.5">
            <div className="flex gap-1">
              {[0, 1, 2, 3].map((i) => (
                <span
                  key={i}
                  className={cn(
                    "h-1 flex-1 rounded-full bg-border",
                    i < strength.score && (strength.score >= 3 ? "bg-moss" : "bg-primary"),
                  )}
                />
              ))}
            </div>
            <p className="text-xs text-muted-foreground">Độ mạnh: {strength.label}</p>
          </div>
        )}
        <div className="flex items-center gap-3 rounded-lg bg-secondary px-4 py-3 text-sm text-muted-foreground">
          <ShieldCheck className="h-4 w-4 shrink-0 text-moss" />
          Sau khi đổi, phiên đăng nhập trên các thiết bị khác sẽ bị thu hồi.
        </div>
        <Button type="submit" className="h-11 w-full" disabled={change.isPending}>
          {change.isPending ? (
            <Loader2 className="mr-2 h-4 w-4 animate-spin" />
          ) : (
            <KeyRound className="mr-2 h-4 w-4" />
          )}
          Cập nhật mật khẩu
        </Button>
      </form>
    </section>
  );
}
