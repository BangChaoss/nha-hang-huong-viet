import { useEffect } from "react";
import { useNavigate } from "@tanstack/react-router";
import { useAuth } from "@/lib/auth-store";
import { getHomeForRole, type AppRole } from "@/lib/role-routing";

export function useWorkplaceAccess(allowedRoles: readonly AppRole[]) {
  const { user, ready } = useAuth();
  const navigate = useNavigate();
  const allowed = user !== null && allowedRoles.includes(user.role);

  useEffect(() => {
    if (!ready) return;
    if (!user) {
      void navigate({ to: "/login", replace: true });
    } else if (!allowedRoles.includes(user.role)) {
      void navigate({ to: getHomeForRole(user.role), replace: true });
    }
  }, [ready, user, navigate, allowedRoles]);

  return ready && allowed;
}
