import { createFileRoute } from "@tanstack/react-router";
import { AccountLayout } from "@/components/hv/AccountLayout";
import { ProfilePage } from "@/components/hv/ProfilePage";

export const Route = createFileRoute("/account")({
  head: () => ({ meta: [{ title: "Hồ sơ cá nhân — Hương Việt" }] }),
  component: () => <AccountLayout>{() => <ProfilePage />}</AccountLayout>,
});
