import * as React from "react";
import { Sparkles, Users, Clock, CheckCircle2, AlertCircle } from "lucide-react";
import { cn } from "@/lib/utils";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import type { ZoneAvailabilityResponse } from "../types/reservation.types";

interface ZoneTimeSelectorProps {
  selectedZoneId?: string;
  selectedTime?: string;
  onSelectZone: (zoneId: string) => void;
  onSelectTime: (time: string) => void;
  availabilityData?: ZoneAvailabilityResponse[];
  isLoading?: boolean;
  partySize: number;
}

export function ZoneTimeSelector({
  selectedZoneId,
  selectedTime,
  onSelectZone,
  onSelectTime,
  availabilityData,
  isLoading = false,
  partySize,
}: ZoneTimeSelectorProps) {
  if (isLoading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-6 w-48" />
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <Skeleton className="h-40 rounded-xl" />
          <Skeleton className="h-40 rounded-xl" />
          <Skeleton className="h-40 rounded-xl" />
        </div>
      </div>
    );
  }

  if (!availabilityData || availabilityData.length === 0) {
    return (
      <div className="rounded-lg border border-dashed p-6 text-center text-muted-foreground">
        <AlertCircle className="mx-auto mb-2 h-8 w-8 text-muted-foreground/60" />
        <p>Vui lòng chọn ngày dùng bữa để kiểm tra khu vực và khung giờ khả dụng.</p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div>
        <label className="text-sm font-medium leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70">
          Chọn Khu Vực Ăn Uống
        </label>
        <p className="mt-1 text-xs text-muted-foreground">
          Chọn một khu vực để xem chi tiết các khung giờ phục vụ
        </p>
      </div>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {availabilityData.map((zone) => {
          const isSelected = selectedZoneId === zone.zoneId;
          const availableSlotsCount = zone.timeSlots.filter((s) => s.available).length;

          return (
            <div
              key={zone.zoneId}
              role="button"
              tabIndex={0}
              onClick={() => onSelectZone(zone.zoneId)}
              onKeyDown={(e) => {
                if (e.key === "Enter" || e.key === " ") {
                  e.preventDefault();
                  onSelectZone(zone.zoneId);
                }
              }}
              className={cn(
                "group relative flex flex-col justify-between rounded-xl border p-4 text-left transition-all hover:border-primary/50 hover:shadow-sm cursor-pointer",
                isSelected
                  ? "border-primary bg-primary/5 ring-2 ring-primary/20 shadow-sm"
                  : "border-border bg-card",
              )}
            >
              <div>
                <div className="flex items-center justify-between gap-2">
                  <h4 className="font-semibold text-foreground group-hover:text-primary transition-colors">
                    {zone.zoneName}
                  </h4>
                  {zone.isVip && (
                    <Badge
                      variant="outline"
                      className="border-amber-500/30 bg-amber-500/10 text-amber-700 dark:text-amber-400 gap-1 text-[10px]"
                    >
                      <Sparkles className="h-3 w-3" />
                      VIP
                    </Badge>
                  )}
                </div>

                <div className="mt-2 flex items-center gap-3 text-xs text-muted-foreground">
                  <span className="flex items-center gap-1">
                    <Users className="h-3.5 w-3.5" />
                    Sức chứa: {zone.totalCapacity} chỗ
                  </span>
                </div>
              </div>

              <div className="mt-4 flex items-center justify-between border-t pt-3 text-xs">
                <span
                  className={cn(
                    "font-medium",
                    availableSlotsCount > 0
                      ? "text-emerald-600 dark:text-emerald-400"
                      : "text-rose-500",
                  )}
                >
                  {availableSlotsCount > 0
                    ? `Còn ${availableSlotsCount} khung giờ trống`
                    : "Đã hết chỗ toàn bộ giờ"}
                </span>
                {isSelected && <CheckCircle2 className="h-4 w-4 text-primary" />}
              </div>
            </div>
          );
        })}
      </div>

      {selectedZoneId && (
        <div className="space-y-3 rounded-xl border bg-muted/30 p-5">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Clock className="h-4 w-4 text-primary" />
              <h4 className="text-sm font-semibold text-foreground">
                Khung giờ khả dụng cho đoàn {partySize} khách:
              </h4>
            </div>
            <span className="text-xs text-muted-foreground">
              (Ca trưa: 11:00–13:30 · Ca tối: 17:30–20:30)
            </span>
          </div>

          {(() => {
            const activeZone = availabilityData.find((z) => z.zoneId === selectedZoneId);
            if (!activeZone) return null;

            return (
              <div className="grid grid-cols-3 gap-2 sm:grid-cols-4 md:grid-cols-6">
                {activeZone.timeSlots.map((slot) => {
                  const slotTimeFormatted = slot.label || slot.time.substring(0, 5);
                  const isSelected = selectedTime === slotTimeFormatted;
                  const isAvailable = slot.available;

                  return (
                    <button
                      key={slotTimeFormatted}
                      type="button"
                      disabled={!isAvailable}
                      onClick={() => onSelectTime(slotTimeFormatted)}
                      className={cn(
                        "flex flex-col items-center justify-center rounded-lg border py-2.5 px-2 text-xs font-medium transition-all",
                        !isAvailable &&
                          "cursor-not-allowed border-dashed bg-muted/60 text-muted-foreground/50 opacity-60",
                        isAvailable &&
                          !isSelected &&
                          "border-border bg-background hover:border-primary/50 hover:bg-accent text-foreground",
                        isSelected && "border-primary bg-primary text-primary-foreground shadow-sm",
                      )}
                    >
                      <span className="text-sm font-semibold">{slotTimeFormatted}</span>
                      <span
                        className={cn(
                          "mt-0.5 text-[10px]",
                          isSelected
                            ? "text-primary-foreground/90"
                            : isAvailable
                              ? "text-muted-foreground"
                              : "text-rose-500",
                        )}
                      >
                        {isAvailable ? `Còn ${slot.remainingCapacity} chỗ` : "Hết chỗ"}
                      </span>
                    </button>
                  );
                })}
              </div>
            );
          })()}
        </div>
      )}
    </div>
  );
}
