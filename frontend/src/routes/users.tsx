import { createFileRoute } from "@tanstack/react-router";
import { AccountLayout } from "@/components/hv/AccountLayout";
import { StaffManagement } from "@/components/hv/StaffManagement";
import { isUserManager } from "@/lib/auth-session";

export const Route = createFileRoute("/users")({
  head: () => ({ meta: [{ title: "Quản lý nhân sự — Hương Việt" }] }),
  component: () => (
    <AccountLayout allow={(s) => isUserManager(s.role)}>
      {(session) => <StaffManagement actor={session} />}
    </AccountLayout>
  ),
});
