import { ROLE_LABEL, STATUS_LABEL, type UserStatus } from "@/lib/account-api";
import type { AppRole } from "@/lib/role-routing";
import { cn } from "@/lib/utils";

const STATUS_CLASS: Record<UserStatus, string> = {
  ACTIVE: "status-available",
  INACTIVE: "status-maintenance",
  LOCKED: "status-cancelled",
};

const ROLE_CLASS: Record<AppRole, string> = {
  ADMIN: "border-primary/25 bg-primary/10 text-primary",
  MANAGER: "border-moss/20 bg-accent text-accent-foreground",
  STAFF: "status-reserved",
  CHEF: "status-occupied",
  CUSTOMER: "status-confirmed",
};

const pill = "inline-flex items-center whitespace-nowrap rounded-full border px-2.5 py-0.5 text-xs";

export function StatusBadge({ status }: { status: UserStatus }) {
  return (
    <span className={cn(pill, STATUS_CLASS[status] ?? STATUS_CLASS.INACTIVE)}>
      {STATUS_LABEL[status] ?? status}
    </span>
  );
}

export function RoleBadge({ role }: { role: AppRole }) {
  return <span className={cn(pill, ROLE_CLASS[role])}>{ROLE_LABEL[role]}</span>;
}

export type AccountFields = { username: string; email: string; phoneNumber: string };

/** Client-side mirror of backend bean validation, so errors show before a round trip. */
export function validateAccountFields(
  f: AccountFields,
): Partial<Record<keyof AccountFields, string>> {
  const errors: Partial<Record<keyof AccountFields, string>> = {};
  const username = f.username.trim();
  if (username.length < 3 || username.length > 50)
    errors.username = "Username phải từ 3 đến 50 ký tự";
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(f.email.trim()))
    errors.email = "Email không đúng định dạng";
  if (!/^[0-9]{10}$/.test(f.phoneNumber.trim()))
    errors.phoneNumber = "Số điện thoại phải gồm đúng 10 chữ số";
  return errors;
}
