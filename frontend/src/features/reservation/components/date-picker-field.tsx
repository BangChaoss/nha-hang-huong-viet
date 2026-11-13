import * as React from "react";
import { CalendarIcon } from "lucide-react";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { Calendar } from "@/components/ui/calendar";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";

function getStartOfToday(): Date {
  const d = new Date();
  d.setHours(0, 0, 0, 0);
  return d;
}

function formatDateToIso(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

function formatDisplayDate(date: Date): string {
  try {
    const weekday = new Intl.DateTimeFormat("vi-VN", { weekday: "long" }).format(date);
    const day = String(date.getDate()).padStart(2, "0");
    const month = String(date.getMonth() + 1).padStart(2, "0");
    const year = date.getFullYear();
    const capitalizedWeekday = weekday.charAt(0).toUpperCase() + weekday.slice(1);
    return `${capitalizedWeekday}, ngày ${day}/${month}/${year}`;
  } catch {
    return formatDateToIso(date);
  }
}

interface DatePickerFieldProps {
  value?: string; // "YYYY-MM-DD"
  onChange: (dateStr: string) => void;
  disabled?: boolean;
  minDate?: Date;
  placeholder?: string;
  className?: string;
}

export function DatePickerField({
  value,
  onChange,
  disabled = false,
  minDate = getStartOfToday(),
  placeholder = "Chọn ngày dùng bữa",
  className,
}: DatePickerFieldProps) {
  const [open, setOpen] = React.useState(false);

  const selectedDate = React.useMemo(() => {
    if (!value) return undefined;
    const parts = value.split("-").map(Number);
    if (parts.length === 3 && !isNaN(parts[0]) && !isNaN(parts[1]) && !isNaN(parts[2])) {
      const d = new Date(parts[0], parts[1] - 1, parts[2]);
      return !isNaN(d.getTime()) ? d : undefined;
    }
    return undefined;
  }, [value]);

  const handleSelect = (date: Date | undefined) => {
    if (date) {
      onChange(formatDateToIso(date));
      setOpen(false);
    }
  };

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          type="button"
          variant="outline"
          disabled={disabled}
          className={cn(
            "w-full justify-start text-left font-normal",
            !selectedDate && "text-muted-foreground",
            className,
          )}
        >
          <CalendarIcon className="mr-2 h-4 w-4" />
          {selectedDate ? (
            formatDisplayDate(selectedDate)
          ) : (
            <span>{placeholder}</span>
          )}
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-auto p-0" align="start">
        <Calendar
          mode="single"
          selected={selectedDate}
          onSelect={handleSelect}
          disabled={(date) => date < minDate}
          initialFocus
        />
      </PopoverContent>
    </Popover>
  );
}
