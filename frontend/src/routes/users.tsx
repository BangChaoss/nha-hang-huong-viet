import { createFileRoute, redirect } from "@tanstack/react-router";

export const Route = createFileRoute("/users")({
  beforeLoad: () => {
    throw redirect({
      to: "/manager",
      search: { tab: "permissions" },
    });
  },
  head: () => ({ meta: [{ title: "Quản lý nhân sự — Hương Việt" }] }),
  component: () => null,
});

