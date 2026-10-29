import { useRouter, useNavigate } from "@tanstack/react-router";
import { ArrowLeft } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useSession } from "@/lib/auth-session";
import { getHomeForRole } from "@/lib/role-routing";
import { cn } from "@/lib/utils";

export interface BackButtonProps {
  to?: string;
  fallbackTo?: string;
  label?: string;
  className?: string;
  variant?: "ghost" | "outline" | "default" | "secondary";
  size?: "default" | "sm" | "lg" | "icon";
}

export function BackButton({
  to,
  fallbackTo,
  label = "Quay lại",
  className,
  variant = "outline",
  size = "sm",
}: BackButtonProps) {
  const router = useRouter();
  const navigate = useNavigate();
  const { session } = useSession();

  const handleBack = () => {
    // If a direct target is explicitly specified, navigate directly to it
    if (to) {
      void navigate({ to });
      return;
    }

    // If browser has history entries, navigate back in history
    if (typeof window !== "undefined" && window.history.length > 1) {
      router.history.back();
    } else {
      const target =
        fallbackTo ??
        (session?.role === "ADMIN"
          ? "/admin"
          : session
          ? getHomeForRole(session.role)
          : "/");
      void navigate({ to: target });
    }
  };

  return (
    <Button
      type="button"
      variant={variant}
      size={size}
      onClick={handleBack}
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full border border-border/80 bg-background/80 px-3 py-1.5 text-xs font-medium text-foreground transition-all hover:bg-accent hover:text-foreground shadow-xs cursor-pointer",
        className
      )}
      title="Quay lại"
      aria-label="Quay lại"
    >
      <ArrowLeft className="h-3.5 w-3.5 shrink-0" />
      {label && <span>{label}</span>}
    </Button>
  );
}
