import { useEffect, useState, type ChangeEvent, type FormEvent, type ReactNode } from "react";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  ChevronLeft,
  ChevronRight,
  Eye,
  Loader2,
  Lock,
  LockOpen,
  PencilLine,
  Plus,
  RefreshCw,
  Search,
  Trash2,
  UsersRound,
} from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
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
  assignableRoles,
  canModify,
  formatDate,
  ROLE_LABEL,
  staffApi,
  STATUS_LABEL,
  type StaffRole,
  type UserAccount,
  type UserStatus,
} from "@/lib/account-api";
import type { AuthSession } from "@/lib/auth-session";
import type { AppRole } from "@/lib/role-routing";
import { cn } from "@/lib/utils";

const PAGE_SIZE = 10;
// Keeps dialogs off the screen edges and scrollable on short/narrow viewports.
const DIALOG_CLASS =
  "w-[calc(100%-2rem)] max-h-[calc(100dvh-2rem)] overflow-y-auto rounded-lg p-5 sm:p-6";
const ROLE_FILTERS: StaffRole[] = ["STAFF", "CHEF", "MANAGER", "ADMIN"];
const STATUSES: UserStatus[] = ["ACTIVE", "INACTIVE", "LOCKED"];

export function StaffManagement({
  actor,
  title = "Quản lý nhân sự",
  subtitle = "Quản trị & Phân quyền",
}: {
  actor: AuthSession;
  title?: string;
  subtitle?: string;
}) {
  const queryClient = useQueryClient();
  const [keywordInput, setKeywordInput] = useState("");
  const [keyword, setKeyword] = useState("");
  const [role, setRole] = useState<AppRole | "">("");
  const [status, setStatus] = useState<UserStatus | "">("");
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<UserAccount | "new" | null>(null);
  const [deleting, setDeleting] = useState<UserAccount | null>(null);
  const [viewingId, setViewingId] = useState<string | null>(null);

  useEffect(() => {
    const t = setTimeout(() => {
      setKeyword(keywordInput);
      setPage(0);
    }, 300);
    return () => clearTimeout(t);
  }, [keywordInput]);

  const filter = { keyword, role, status, page, size: PAGE_SIZE };
  const list = useQuery({
    queryKey: ["admin", "staff", filter],
    queryFn: () => staffApi.list(filter),
    placeholderData: keepPreviousData,
  });
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ["admin", "staff"] });

  const statusMutation = useMutation({
    mutationFn: ({ id, next }: { id: string; next: UserStatus }) => staffApi.setStatus(id, next),
    onSuccess: (u) => {
      toast.success(`${u.username}: ${STATUS_LABEL[u.status]}`);
      void invalidate();
    },
    onError: (e) => toast.error(e.message),
  });
  const deleteMutation = useMutation({
    mutationFn: (id: string) => staffApi.remove(id),
    onSuccess: () => {
      toast.success("Đã xoá tài khoản nhân viên.");
      setDeleting(null);
      void invalidate();
    },
    onError: (e) => toast.error(e.message),
  });

  const data = list.data;
  const rows = data?.content ?? [];
  const from = data && data.totalElements ? data.page * data.size + 1 : 0;
  const to = data ? data.page * data.size + rows.length : 0;
  const pendingId = statusMutation.isPending ? statusMutation.variables?.id : undefined;
  // Customers are listed for ADMIN only (backend enforces it too) and are lock/unlock-only.
  const roleFilters: AppRole[] =
    actor.role === "ADMIN" ? [...ROLE_FILTERS, "CUSTOMER"] : ROLE_FILTERS;
  const showingCustomers = role === "CUSTOMER";
  // Derived from the list so the dialog reflects status changes made from inside it.
  const viewing = rows.find((r) => r.id === viewingId) ?? null;

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <p className="text-xs uppercase tracking-[0.2em] text-muted-foreground">{subtitle}</p>
          <h1 className="text-3xl font-semibold sm:text-4xl">{title}</h1>
        </div>
        {/* Scrolls sideways on phones instead of wrapping into several rows. */}
        <div className="-mx-4 flex gap-2 overflow-x-auto px-4 pb-1 sm:mx-0 sm:flex-wrap sm:overflow-visible sm:px-0 sm:pb-0">
          {[
            { value: "" as const, label: "Tất cả" },
            ...roleFilters.map((r) => ({ value: r, label: ROLE_LABEL[r] })),
          ].map((f) => (
            <button
              key={f.label}
              onClick={() => {
                setRole(f.value);
                setPage(0);
              }}
              className={cn(
                "shrink-0 whitespace-nowrap rounded-full border px-3 py-1.5 text-xs transition-colors sm:py-1",
                role === f.value
                  ? "border-moss bg-moss text-moss-foreground"
                  : "bg-card text-muted-foreground hover:text-foreground",
              )}
            >
              {f.label}
              {role === f.value && data ? ` · ${data.totalElements}` : ""}
            </button>
          ))}
        </div>
      </div>

      <section className="surface-card p-4 sm:p-5">
        <div className="mb-4 flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
          <h2 className="text-xl font-medium sm:text-2xl">
            {showingCustomers ? "Danh sách khách hàng" : "Danh sách nhân viên"}
          </h2>
          <div className="flex flex-wrap items-center gap-2 md:justify-end">
            <div className="relative w-full md:w-64 xl:w-80">
              <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                value={keywordInput}
                onChange={(e) => setKeywordInput(e.target.value)}
                placeholder="Tìm username, email, SĐT"
                className="pl-9"
                aria-label="Tìm nhân viên"
              />
            </div>
            <select
              value={status}
              onChange={(e) => {
                setStatus(e.target.value as UserStatus | "");
                setPage(0);
              }}
              aria-label="Lọc trạng thái"
              className="h-10 min-w-0 flex-1 rounded-md border border-input bg-background px-3 text-sm md:flex-none"
            >
              <option value="">Mọi trạng thái</option>
              {STATUSES.map((s) => (
                <option key={s} value={s}>
                  {STATUS_LABEL[s]}
                </option>
              ))}
            </select>
            {!showingCustomers && (
              <Button className="flex-1 md:flex-none" onClick={() => setEditing("new")}>
                <Plus className="mr-2 h-4 w-4" />
                Thêm nhân viên
              </Button>
            )}
          </div>
        </div>

        {list.isPending && (
          <div className="py-16 text-center text-muted-foreground">
            <Loader2 className="mx-auto h-5 w-5 animate-spin" />
          </div>
        )}
        {list.isError && (
          <div className="py-12 text-center">
            <p className="text-muted-foreground">{list.error.message}</p>
            <Button variant="outline" size="sm" className="mt-3" onClick={() => list.refetch()}>
              <RefreshCw className="mr-2 h-4 w-4" />
              Thử lại
            </Button>
          </div>
        )}
        {data && !rows.length && (
          <div className="py-16 text-center text-muted-foreground">
            <UsersRound className="mx-auto mb-2 h-6 w-6" />
            {showingCustomers ? "Không có khách hàng phù hợp." : "Không có nhân viên phù hợp."}
          </div>
        )}

        {rows.length > 0 && (
          <>
            {/* Phones & tablets: one card per account. */}
            <ul
              className={cn(
                "grid gap-3 md:grid-cols-2 lg:hidden",
                list.isPlaceholderData && "opacity-60",
              )}
            >
              {rows.map((u) => (
                <li key={u.id} className="rounded-lg border bg-card p-4">
                  <AccountIdentity user={u} isSelf={u.id === actor.id} />
                  <dl className="mt-3 grid grid-cols-2 gap-x-3 gap-y-2 text-xs">
                    <div>
                      <dt className="text-muted-foreground">Số điện thoại</dt>
                      <dd className="mt-0.5 text-sm">{u.phoneNumber}</dd>
                    </div>
                    <div>
                      <dt className="text-muted-foreground">Vai trò</dt>
                      <dd className="mt-0.5">
                        <RoleBadge role={u.role} />
                      </dd>
                    </div>
                    <div>
                      <dt className="text-muted-foreground">Ngày tạo</dt>
                      <dd className="mt-0.5 text-sm">{formatDate(u.createdAt)}</dd>
                    </div>
                    <div>
                      <dt className="text-muted-foreground">ID</dt>
                      <dd className="-ml-1.5 mt-0.5">
                        <IdChip id={u.id} />
                      </dd>
                    </div>
                  </dl>
                  <div className="mt-3 flex items-center justify-between gap-3 border-t pt-2">
                    <StatusBadge status={u.status} />
                    <RowActions
                      user={u}
                      actor={actor}
                      pending={pendingId === u.id}
                      onView={() => setViewingId(u.id)}
                      onEdit={() => setEditing(u)}
                      onDelete={() => setDeleting(u)}
                      onStatus={(next) => statusMutation.mutate({ id: u.id, next })}
                    />
                  </div>
                </li>
              ))}
            </ul>

            {/* Desktop: full table. */}
            <div className="hidden overflow-x-auto lg:block">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b text-left font-medium">
                    <th className="py-2.5 font-medium">
                      {showingCustomers ? "Khách hàng" : "Nhân viên"}
                    </th>
                    <th className="font-medium">ID</th>
                    <th className="font-medium">Số điện thoại</th>
                    <th className="font-medium">Vai trò</th>
                    <th className="font-medium">Ngày tạo</th>
                    <th className="text-right font-medium">Tình trạng</th>
                    <th className="w-36 text-right font-medium">
                      <span className="sr-only">Thao tác</span>
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((u) => (
                    <tr
                      key={u.id}
                      className={cn(
                        "border-b last:border-0",
                        list.isPlaceholderData && "opacity-60",
                      )}
                    >
                      <td className="max-w-64 py-3 pr-3 2xl:max-w-80">
                        <AccountIdentity user={u} isSelf={u.id === actor.id} />
                      </td>
                      <td className="pr-3">
                        <IdChip id={u.id} />
                      </td>
                      <td className="whitespace-nowrap pr-3 text-muted-foreground">
                        {u.phoneNumber}
                      </td>
                      <td className="pr-3">
                        <RoleBadge role={u.role} />
                      </td>
                      <td className="whitespace-nowrap pr-3 text-muted-foreground">
                        {formatDate(u.createdAt)}
                      </td>
                      <td>
                        <div className="flex items-center justify-end gap-3">
                          <StatusBadge status={u.status} />
                        </div>
                      </td>
                      <td>
                        <RowActions
                          user={u}
                          actor={actor}
                          pending={pendingId === u.id}
                          onView={() => setViewingId(u.id)}
                          onEdit={() => setEditing(u)}
                          onDelete={() => setDeleting(u)}
                          onStatus={(next) => statusMutation.mutate({ id: u.id, next })}
                        />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </>
        )}

        {data && data.totalElements > 0 && (
          <div className="mt-4 flex flex-wrap items-center justify-between gap-3 border-t pt-4 text-sm text-muted-foreground">
            <span>
              Hiển thị {from}–{to} / {data.totalElements}
            </span>
            <div className="flex items-center gap-2">
              <Button
                variant="outline"
                size="sm"
                disabled={page === 0}
                onClick={() => setPage((p) => p - 1)}
                aria-label="Trang trước"
              >
                <ChevronLeft className="h-4 w-4" />
              </Button>
              <span className="tabular-nums">
                {data.page + 1} / {Math.max(1, data.totalPages)}
              </span>
              <Button
                variant="outline"
                size="sm"
                disabled={data.last}
                onClick={() => setPage((p) => p + 1)}
                aria-label="Trang sau"
              >
                <ChevronRight className="h-4 w-4" />
              </Button>
            </div>
          </div>
        )}
      </section>

      <Dialog open={!!viewing} onOpenChange={(open) => !open && setViewingId(null)}>
        <DialogContent className={cn(DIALOG_CLASS, "max-w-md")}>
          {viewing && (
            <StaffDetail
              user={viewing}
              isSelf={viewing.id === actor.id}
              canLock={canModify(actor, viewing)}
              canEdit={canModify(actor, viewing) && viewing.role !== "CUSTOMER"}
              locking={pendingId === viewing.id}
              onToggleLock={() =>
                statusMutation.mutate({
                  id: viewing.id,
                  next: viewing.status === "LOCKED" ? "ACTIVE" : "LOCKED",
                })
              }
              onEdit={() => {
                setViewingId(null);
                setEditing(viewing);
              }}
            />
          )}
        </DialogContent>
      </Dialog>

      <StaffDialog
        actor={actor}
        target={editing}
        onClose={() => setEditing(null)}
        onSaved={() => {
          setEditing(null);
          void invalidate();
        }}
      />

      <AlertDialog open={!!deleting} onOpenChange={(open) => !open && setDeleting(null)}>
        <AlertDialogContent className={DIALOG_CLASS}>
          <AlertDialogHeader>
            <AlertDialogTitle className="font-display text-2xl">Xoá tài khoản?</AlertDialogTitle>
            <AlertDialogDescription>
              Tài khoản <b className="text-foreground">{deleting?.username}</b> sẽ bị vô hiệu hoá và
              đăng xuất khỏi mọi thiết bị.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={deleteMutation.isPending}>Huỷ</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
              disabled={deleteMutation.isPending}
              onClick={(e) => {
                e.preventDefault();
                if (deleting) deleteMutation.mutate(deleting.id);
              }}
            >
              {deleteMutation.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}Xoá tài
              khoản
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}

function AccountIdentity({ user, isSelf }: { user: UserAccount; isSelf: boolean }) {
  return (
    <div className="flex min-w-0 items-center gap-3">
      <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-secondary text-xs font-semibold text-primary">
        {initials(user.username)}
      </span>
      <div className="min-w-0">
        <p className="truncate font-medium">
          {user.username}
          {isSelf && (
            <span className="ml-1.5 text-xs font-normal text-muted-foreground">(bạn)</span>
          )}
        </p>
        <p className="truncate text-xs text-muted-foreground">{user.email}</p>
      </div>
    </div>
  );
}

function IdChip({ id }: { id: string }) {
  return (
    <button
      type="button"
      title={`${id} — bấm để sao chép`}
      onClick={() => copyId(id)}
      className="rounded px-1.5 py-0.5 font-mono text-xs text-muted-foreground hover:bg-secondary hover:text-foreground"
    >
      {id.slice(0, 8)}
    </button>
  );
}

function RowActions({
  user,
  actor,
  pending,
  onView,
  onEdit,
  onDelete,
  onStatus,
}: {
  user: UserAccount;
  actor: AuthSession;
  pending: boolean;
  onView: () => void;
  onEdit: () => void;
  onDelete: () => void;
  onStatus: (next: UserStatus) => void;
}) {
  const editable = canModify(actor, user);
  // Customer profiles/roles are theirs to manage; admins can only (un)lock them.
  const fullyEditable = editable && user.role !== "CUSTOMER";
  const locked = user.status === "LOCKED";
  return (
    <div className="flex justify-end gap-0.5">
      <IconAction label="Xem chi tiết" onClick={onView}>
        <Eye className="h-4 w-4" />
      </IconAction>
      <IconAction label="Chỉnh sửa" disabled={!fullyEditable} onClick={onEdit}>
        <PencilLine className="h-4 w-4" />
      </IconAction>
      <IconAction
        label={locked ? "Mở khoá" : "Khoá tài khoản"}
        disabled={!editable || pending}
        onClick={() => onStatus(locked ? "ACTIVE" : "LOCKED")}
      >
        {locked ? <LockOpen className="h-4 w-4" /> : <Lock className="h-4 w-4" />}
      </IconAction>
      <IconAction label="Xoá" danger disabled={!fullyEditable} onClick={onDelete}>
        <Trash2 className="h-4 w-4" />
      </IconAction>
    </div>
  );
}

function copyId(id: string) {
  navigator.clipboard.writeText(id).then(
    () => toast.success("Đã sao chép ID tài khoản."),
    () => toast.error("Không thể sao chép ID."),
  );
}

function IconAction({
  label,
  danger,
  disabled,
  onClick,
  children,
}: {
  label: string;
  danger?: boolean;
  disabled?: boolean;
  onClick: () => void;
  children: ReactNode;
}) {
  return (
    <button
      type="button"
      title={label}
      aria-label={label}
      disabled={disabled}
      onClick={onClick}
      className={cn(
        "rounded-md p-2 text-muted-foreground transition-colors hover:bg-secondary disabled:pointer-events-none disabled:opacity-30",
        danger ? "hover:text-destructive" : "hover:text-primary",
      )}
    >
      {children}
    </button>
  );
}

const formatDateTime = (value: string | null) =>
  value
    ? new Date(value).toLocaleString("vi-VN", {
        hour: "2-digit",
        minute: "2-digit",
        day: "2-digit",
        month: "2-digit",
        year: "numeric",
      })
    : "—";

function StaffDetail({
  user,
  isSelf,
  canLock,
  canEdit,
  locking,
  onToggleLock,
  onEdit,
}: {
  user: UserAccount;
  isSelf: boolean;
  canLock: boolean;
  canEdit: boolean;
  locking: boolean;
  onToggleLock: () => void;
  onEdit: () => void;
}) {
  const locked = user.status === "LOCKED";
  const rows: [string, ReactNode][] = [
    ["Email", user.email],
    ["Số điện thoại", user.phoneNumber],
    ["Vai trò", <RoleBadge role={user.role} />],
    ["Tình trạng", <StatusBadge status={user.status} />],
    ["Ngày tạo", formatDateTime(user.createdAt)],
    ["Cập nhật lần cuối", formatDateTime(user.updatedAt)],
    ["Mã tài khoản", <span className="break-all font-mono text-xs">{user.id}</span>],
  ];

  return (
    <>
      <DialogHeader>
        <DialogTitle className="font-display text-2xl font-medium sm:text-3xl">
          Chi tiết tài khoản
        </DialogTitle>
        <DialogDescription>Thông tin tài khoản {user.username}.</DialogDescription>
      </DialogHeader>
      <div className="flex items-center gap-3">
        <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-secondary text-sm font-semibold text-primary">
          {initials(user.username)}
        </span>
        <p className="truncate text-lg font-medium">
          {user.username}
          {isSelf && (
            <span className="ml-1.5 text-xs font-normal text-muted-foreground">(bạn)</span>
          )}
        </p>
      </div>
      <dl className="divide-y rounded-lg border text-sm">
        {rows.map(([label, value]) => (
          <div key={label} className="flex items-center justify-between gap-4 px-4 py-2.5">
            <dt className="shrink-0 text-muted-foreground">{label}</dt>
            <dd className="min-w-0 break-all text-right">{value}</dd>
          </div>
        ))}
      </dl>
      {(canLock || canEdit) && (
        <div className="flex flex-wrap justify-end gap-2 [&>button]:flex-1 sm:[&>button]:flex-none">
          {canEdit && (
            <Button variant="outline" onClick={onEdit}>
              <PencilLine className="mr-2 h-4 w-4" />
              Chỉnh sửa
            </Button>
          )}
          {canLock && (
            <Button
              variant={locked ? "outline" : "destructive"}
              disabled={locking}
              onClick={onToggleLock}
            >
              {locking ? (
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              ) : locked ? (
                <LockOpen className="mr-2 h-4 w-4" />
              ) : (
                <Lock className="mr-2 h-4 w-4" />
              )}
              {locked ? "Mở khoá" : "Khoá tài khoản"}
            </Button>
          )}
        </div>
      )}
    </>
  );
}

type StaffForm = AccountFields & { role: StaffRole; password: string };
type StaffErrors = Partial<Record<keyof StaffForm, string>>;

function StaffDialog({
  actor,
  target,
  onClose,
  onSaved,
}: {
  actor: AuthSession;
  target: UserAccount | "new" | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const isNew = target === "new";
  const roles = assignableRoles(actor.role);
  const [form, setForm] = useState<StaffForm>({
    username: "",
    email: "",
    phoneNumber: "",
    role: "STAFF",
    password: "",
  });
  const [errors, setErrors] = useState<StaffErrors>({});

  useEffect(() => {
    if (!target) return;
    setErrors({});
    setForm(
      target === "new"
        ? { username: "", email: "", phoneNumber: "", role: "STAFF", password: "" }
        : {
            username: target.username,
            email: target.email,
            phoneNumber: target.phoneNumber,
            role: target.role as StaffRole,
            password: "",
          },
    );
  }, [target]);

  const save = useMutation({
    mutationFn: (f: StaffForm) => {
      const base = {
        username: f.username.trim(),
        email: f.email.trim(),
        phoneNumber: f.phoneNumber.trim(),
        role: f.role,
      };
      return isNew
        ? staffApi.create({ ...base, ...(f.password ? { password: f.password } : {}) })
        : staffApi.update((target as UserAccount).id, base);
    },
    onSuccess: (u) => {
      toast.success(isNew ? `Đã tạo tài khoản ${u.username}.` : `Đã cập nhật ${u.username}.`);
      onSaved();
    },
    onError: (e) => {
      if (e instanceof ApiError) setErrors(e.fieldErrors);
      toast.error(e.message);
    },
  });

  function submit(e: FormEvent) {
    e.preventDefault();
    const found: StaffErrors = validateAccountFields(form);
    if (isNew && form.password && (form.password.length < 8 || form.password.length > 100))
      found.password = "Mật khẩu phải từ 8 đến 100 ký tự";
    setErrors(found);
    if (!Object.keys(found).length) save.mutate(form);
  }

  const set = (key: keyof StaffForm) => (e: ChangeEvent<HTMLInputElement>) =>
    setForm((f) => ({ ...f, [key]: e.target.value }));

  return (
    <Dialog open={!!target} onOpenChange={(open) => !open && !save.isPending && onClose()}>
      <DialogContent className={cn(DIALOG_CLASS, "max-w-md")}>
        <DialogHeader>
          <DialogTitle className="font-display text-2xl font-medium sm:text-3xl">
            {isNew ? "Thêm nhân viên" : "Cập nhật nhân viên"}
          </DialogTitle>
          <DialogDescription>
            {isNew
              ? "Tạo tài khoản đăng nhập cho nhân sự nhà hàng."
              : `Chỉnh sửa thông tin tài khoản ${(target as UserAccount | null)?.username ?? ""}.`}
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={submit} className="space-y-4" noValidate>
          <FormField label="Username" htmlFor="s-username" error={errors.username}>
            <Input
              id="s-username"
              autoComplete="off"
              value={form.username}
              onChange={set("username")}
              placeholder="nguyenvana"
            />
          </FormField>
          <FormField label="Email" htmlFor="s-email" error={errors.email}>
            <Input
              id="s-email"
              type="email"
              autoComplete="off"
              value={form.email}
              onChange={set("email")}
              placeholder="nhanvien@huongviet.vn"
            />
          </FormField>
          <FormField label="Số điện thoại" htmlFor="s-phone" error={errors.phoneNumber}>
            <Input
              id="s-phone"
              inputMode="numeric"
              maxLength={10}
              autoComplete="off"
              value={form.phoneNumber}
              onChange={set("phoneNumber")}
              placeholder="0903123456"
            />
          </FormField>
          <div className="space-y-1.5">
            <p className="text-sm font-medium">Vai trò</p>
            <div className="grid grid-cols-2 gap-2">
              {roles.map((r) => (
                <button
                  key={r}
                  type="button"
                  onClick={() => setForm((f) => ({ ...f, role: r }))}
                  aria-pressed={form.role === r}
                  className={cn(
                    "rounded-lg border px-3 py-2 text-sm transition-colors",
                    form.role === r
                      ? "border-primary bg-primary/10 text-primary"
                      : "bg-card hover:border-primary/40",
                  )}
                >
                  {ROLE_LABEL[r]}
                </button>
              ))}
            </div>
            {errors.role && <p className="text-xs text-destructive">{errors.role}</p>}
          </div>
          {isNew && (
            <FormField
              label="Mật khẩu (tuỳ chọn)"
              htmlFor="s-password"
              error={errors.password}
              hint="Để trống để dùng mật khẩu mặc định của hệ thống."
            >
              <Input
                id="s-password"
                type="password"
                autoComplete="new-password"
                value={form.password}
                onChange={set("password")}
              />
            </FormField>
          )}
          <Button type="submit" className="h-11 w-full" disabled={save.isPending}>
            {save.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
            {isNew ? "Tạo tài khoản" : "Lưu thay đổi"}
          </Button>
        </form>
      </DialogContent>
    </Dialog>
  );
}
