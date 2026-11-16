import * as React from "react";
import { toast } from "sonner";
import {
  Calendar as CalendarIcon,
  Filter,
  RefreshCw,
  Search,
  CheckCircle2,
  Clock,
  UserCheck,
  AlertCircle,
  Inbox,
  LayoutGrid,
  List,
  CalendarDays,
  Bell,
  BellOff,
  Sparkles,
} from "lucide-react";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Skeleton } from "@/components/ui/skeleton";
import { useAdminReservations } from "../hooks/use-admin-reservations";
import { useUpdateReservationStatus } from "../hooks/use-update-reservation-status";
import { StaffBookingRow } from "./staff-booking-row";
import { StaffBookingCard } from "./staff-booking-card";
import { ConfirmBookingDialog } from "./confirm-booking-dialog";
import { CancelBookingDialog } from "./cancel-booking-dialog";
import type { ReservationResponse, ReservationStatus } from "../types/reservation.types";

type DashboardTab = "needs-action" | "today" | "upcoming" | "calendar";
type ViewMode = "cards" | "table";

const EXCLUDED_FROM_UPCOMING_STATUSES = new Set<ReservationStatus>([
  "ARRIVED",
  "PAID",
  "REVIEWED",
  "CANCELLED",
  "NO_SHOW",
]);

function playNotificationSound() {
  try {
    const AudioContextClass =
      window.AudioContext ||
      (window as unknown as { webkitAudioContext: typeof AudioContext })
        .webkitAudioContext;
    if (!AudioContextClass) return;
    const ctx = new AudioContextClass();
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    osc.type = "sine";
    osc.frequency.setValueAtTime(587.33, ctx.currentTime);
    osc.frequency.exponentialRampToValueAtTime(880, ctx.currentTime + 0.15);
    gain.gain.setValueAtTime(0.08, ctx.currentTime);
    gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.3);
    osc.connect(gain);
    gain.connect(ctx.destination);
    osc.start();
    osc.stop(ctx.currentTime + 0.3);
  } catch {
    // Ignore audio restriction errors
  }
}

function formatDisplayDate(dateStr: string): string {
  if (!dateStr) return "";
  try {
    const [year, month, day] = dateStr.split("-").map(Number);
    const d = new Date(year ?? 2026, (month ?? 1) - 1, day ?? 1);
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const target = new Date(d);
    target.setHours(0, 0, 0, 0);

    const diffDays = Math.round(
      (target.getTime() - today.getTime()) / (1000 * 60 * 60 * 24),
    );
    const weekday = [
      "Chủ Nhật",
      "Thứ Hai",
      "Thứ Ba",
      "Thứ Tư",
      "Thứ Năm",
      "Thứ Sáu",
      "Thứ Bảy",
    ][d.getDay()];
    const dateFormatted = `${String(day).padStart(2, "0")}/${String(month).padStart(2, "0")}/${year}`;

    if (diffDays === 0) return `Hôm nay — ${weekday}, ${dateFormatted}`;
    if (diffDays === 1) return `Ngày mai — ${weekday}, ${dateFormatted}`;
    if (diffDays === 2) return `Ngày kia — ${weekday}, ${dateFormatted}`;
    if (diffDays > 0) return `${weekday}, ${dateFormatted} (${diffDays} ngày nữa)`;
    if (diffDays === -1) return `Hôm qua — ${weekday}, ${dateFormatted}`;
    return `${weekday}, ${dateFormatted}`;
  } catch {
    return dateStr;
  }
}

export function StaffBookingDashboard() {
  const todayStr = React.useMemo(() => {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}, []);

  const [activeTab, setActiveTab] = React.useState<DashboardTab>("needs-action");
  const [viewMode, setViewMode] = React.useState<ViewMode>(() => {
    return (localStorage.getItem("staff_booking_view_mode") as ViewMode) || "cards";
  });
  const [soundEnabled, setSoundEnabled] = React.useState<boolean>(true);

  const [selectedDate, setSelectedDate] = React.useState<string>(todayStr);
  const [selectedStatus, setSelectedStatus] = React.useState<string>("ALL");
  const [searchQuery, setSearchQuery] = React.useState<string>("");

  // Dialog state
  const [confirmDialogBooking, setConfirmDialogBooking] =
    React.useState<ReservationResponse | null>(null);
  const [isConfirmOpen, setIsConfirmOpen] = React.useState<boolean>(false);
  const [cancelDialogBooking, setCancelDialogBooking] =
    React.useState<ReservationResponse | null>(null);
  const [isCancelOpen, setIsCancelOpen] = React.useState<boolean>(false);

  // Queries
  // 1. Pending reservations across all dates
  const pendingQuery = useAdminReservations({ scope: "pending", refetchInterval: 15_000 });

  // 2. Upcoming reservations from today onwards
  const upcomingQuery = useAdminReservations({ scope: "upcoming", refetchInterval: 20_000 });

  // 3. Specific date for calendar search tab
  const calendarQuery = useAdminReservations({
    date: selectedDate,
    scope: "date",
    refetchInterval: 30_000,
  });

  const updateStatusMutation = useUpdateReservationStatus();

  // Handle View Mode Change
  const handleChangeViewMode = (mode: ViewMode) => {
    setViewMode(mode);
    localStorage.setItem("staff_booking_view_mode", mode);
  };

  // Sound ping on new pending orders
  const prevPendingCountRef = React.useRef<number>(0);
  React.useEffect(() => {
    const currentCount = pendingQuery.data?.length ?? 0;
    if (prevPendingCountRef.current > 0 && currentCount > prevPendingCountRef.current) {
      if (soundEnabled) {
        playNotificationSound();
      }
      toast.info(`Có ${currentCount - prevPendingCountRef.current} đơn đặt bàn mới cần duyệt!`);
    }
    prevPendingCountRef.current = currentCount;
  }, [pendingQuery.data, soundEnabled]);

  // Combined metrics
  const metrics = React.useMemo(() => {
    const allPending = pendingQuery.data ?? [];
    const upcomingList = upcomingQuery.data ?? [];

    const todayBookings = upcomingList.filter((b) => b.reservationDate === todayStr);
    const todayArrived = todayBookings.filter((b) => b.status === "ARRIVED").length;
    const todayConfirmed = todayBookings.filter((b) => b.status === "CONFIRMED").length;
    const upcomingActiveList = upcomingList.filter(
      (b) => !EXCLUDED_FROM_UPCOMING_STATUSES.has(b.status),
    );

    return {
      pending: allPending.length,
      todayTotal: todayBookings.length,
      todayConfirmed,
      todayArrived,
      upcomingTotal: upcomingActiveList.length,
    };
  }, [pendingQuery.data, upcomingQuery.data, todayStr]);

  // Select active source list based on tab
  const rawList = React.useMemo(() => {
    if (activeTab === "needs-action") {
      return pendingQuery.data ?? [];
    }
    if (activeTab === "today") {
      return (upcomingQuery.data ?? []).filter((b) => b.reservationDate === todayStr);
    }
    if (activeTab === "upcoming") {
      return (upcomingQuery.data ?? []).filter(
        (b) => !EXCLUDED_FROM_UPCOMING_STATUSES.has(b.status),
      );
    }
    // calendar
    return calendarQuery.data ?? [];
  }, [activeTab, pendingQuery.data, upcomingQuery.data, calendarQuery.data, todayStr]);

  // Status and search filtering + sort by nearest date and time
  const processedBookings = React.useMemo(() => {
    let result = [...rawList];

    // Status filter (skip when tab is 'needs-action' as it is always PENDING_HOLD)
    if (activeTab !== "needs-action" && selectedStatus !== "ALL") {
      result = result.filter((b) => b.status === selectedStatus);
    }

    // Search query filter
    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase().trim();
      result = result.filter(
        (b) =>
          b.guestName.toLowerCase().includes(q) ||
          b.guestPhone.toLowerCase().includes(q) ||
          b.id.toLowerCase().includes(q) ||
          (b.tableNumber && b.tableNumber.toLowerCase().includes(q)),
      );
    }

    // Sort by: PENDING_HOLD first, then nearest reservationDate ASC, then reservationTime ASC
    result.sort((a, b) => {
      if (a.status === "PENDING_HOLD" && b.status !== "PENDING_HOLD") return -1;
      if (a.status !== "PENDING_HOLD" && b.status === "PENDING_HOLD") return 1;

      const dateComp = a.reservationDate.localeCompare(b.reservationDate);
      if (dateComp !== 0) return dateComp;

      return (a.reservationTime || "").localeCompare(b.reservationTime || "");
    });

    return result;
  }, [rawList, activeTab, selectedStatus, searchQuery]);

  // Group bookings by date when in 'upcoming' or 'needs-action' tab
  const groupedBookings = React.useMemo(() => {
    if (activeTab === "today") {
      return [{ date: todayStr, items: processedBookings }];
    }

    const groups: { date: string; items: ReservationResponse[] }[] = [];
    const map = new Map<string, ReservationResponse[]>();

    for (const b of processedBookings) {
      const d = b.reservationDate || todayStr;
      if (!map.has(d)) {
        map.set(d, []);
      }
      map.get(d)!.push(b);
    }

    for (const [date, items] of map.entries()) {
      groups.push({ date, items });
    }

    return groups;
  }, [processedBookings, activeTab, todayStr]);

  // Refetch active query
  const isFetchingAny =
    pendingQuery.isFetching || upcomingQuery.isFetching || calendarQuery.isFetching;
  const isLoadingAny =
    activeTab === "needs-action"
      ? pendingQuery.isLoading
      : activeTab === "today" || activeTab === "upcoming"
        ? upcomingQuery.isLoading
        : calendarQuery.isLoading;

  const handleRefetchAll = () => {
    void pendingQuery.refetch();
    void upcomingQuery.refetch();
    if (activeTab === "calendar") {
      void calendarQuery.refetch();
    }
  };

  // Handlers
  const handleOpenConfirm = (booking: ReservationResponse) => {
    setConfirmDialogBooking(booking);
    setIsConfirmOpen(true);
  };

  const handleOpenCancel = (booking: ReservationResponse) => {
    setCancelDialogBooking(booking);
    setIsCancelOpen(true);
  };

  const handleStaffCancel = async (booking: ReservationResponse, reason?: string) => {
    await updateStatusMutation.mutateAsync({
      id: booking.id,
      payload: {
        status: "CANCELLED",
        reason: reason || "Nhân viên hủy đơn",
      },
    });
    void pendingQuery.refetch();
    void upcomingQuery.refetch();
  };

  const handleCheckIn = async (booking: ReservationResponse) => {
    const code = booking.id ? booking.id.substring(0, 8).toUpperCase() : "";
    try {
      await updateStatusMutation.mutateAsync({
        id: booking.id,
        payload: {
          status: "ARRIVED",
        },
      });
      toast.success(
        `Check-in thành công cho đơn HV-${code}. Bàn đã chuyển sang trạng thái Đang Phục Vụ.`,
      );
      void upcomingQuery.refetch();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "Check-in thất bại";
      toast.error(msg);
    }
  };

  return (
    <div className="space-y-6">
      {/* Top Header & Quick View Controls */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b pb-4">
        <div>
          <h2 className="text-xl font-bold tracking-tight text-foreground flex items-center gap-2">
            Quản Lý Đặt Bàn
          </h2>
          <p className="text-xs text-muted-foreground mt-0.5">
            Tiếp nhận, xếp bàn và điều phối lịch hẹn của khách theo thời gian thực
          </p>
        </div>

        <div className="flex items-center gap-2 flex-wrap">
          {/* Sound Alert Toggle */}
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => setSoundEnabled(!soundEnabled)}
            title={soundEnabled ? "Tắt âm thông báo đơn mới" : "Bật âm thông báo đơn mới"}
            className="h-8 px-2.5 text-xs text-muted-foreground"
          >
            {soundEnabled ? (
              <Bell className="h-3.5 w-3.5 text-primary mr-1" />
            ) : (
              <BellOff className="h-3.5 w-3.5 mr-1" />
            )}
            <span className="hidden sm:inline">
              {soundEnabled ? "Âm báo: Bật" : "Âm báo: Tắt"}
            </span>
          </Button>

          {/* View Mode Toggle: Cards vs Table */}
          <div className="flex items-center border rounded-md p-0.5 bg-muted/40">
            <Button
              type="button"
              variant={viewMode === "cards" ? "secondary" : "ghost"}
              size="sm"
              onClick={() => handleChangeViewMode("cards")}
              className="h-7 px-2.5 text-xs font-medium"
            >
              <LayoutGrid className="h-3.5 w-3.5 mr-1" />
              Thẻ
            </Button>
            <Button
              type="button"
              variant={viewMode === "table" ? "secondary" : "ghost"}
              size="sm"
              onClick={() => handleChangeViewMode("table")}
              className="h-7 px-2.5 text-xs font-medium"
            >
              <List className="h-3.5 w-3.5 mr-1" />
              Bảng
            </Button>
          </div>

          {/* Refresh Button */}
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={handleRefetchAll}
            disabled={isFetchingAny}
            className="h-8 text-xs"
          >
            <RefreshCw
              className={`h-3.5 w-3.5 mr-1.5 ${isFetchingAny ? "animate-spin text-primary" : ""}`}
            />
            Làm mới
          </Button>
        </div>
      </div>

      {/* Action Metrics Bar */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
        {/* Needs Action / Pending Hold */}
        <Card
          onClick={() => {
            setActiveTab("needs-action");
            setSelectedStatus("ALL");
          }}
          className={`p-3 cursor-pointer transition-all border ${
            activeTab === "needs-action"
              ? "ring-2 ring-amber-500 bg-amber-500/10 border-amber-500/40"
              : "hover:bg-accent/40 bg-card"
          }`}
        >
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-amber-700 dark:text-amber-400">
              Cần xử lý ngay
            </span>
            <Clock className="h-4 w-4 text-amber-600 dark:text-amber-400" />
          </div>
          <div className="flex items-baseline gap-2 mt-1">
            <p className="text-2xl font-bold text-amber-700 dark:text-amber-400">
              {metrics.pending}
            </p>
            <span className="text-[11px] text-muted-foreground">đơn chờ duyệt</span>
          </div>
        </Card>

        {/* Today's Service */}
        <Card
          onClick={() => {
            setActiveTab("today");
            setSelectedStatus("ALL");
          }}
          className={`p-3 cursor-pointer transition-all border ${
            activeTab === "today" && selectedStatus !== "ARRIVED"
              ? "ring-2 ring-primary bg-primary/5 border-primary/40"
              : "hover:bg-accent/40 bg-card"
          }`}
        >
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-foreground">Hôm nay</span>
            <CalendarIcon className="h-4 w-4 text-primary" />
          </div>
          <div className="flex items-baseline gap-2 mt-1">
            <p className="text-2xl font-bold text-foreground">{metrics.todayTotal}</p>
            <span className="text-[11px] text-muted-foreground">lịch hẹn</span>
          </div>
        </Card>

        {/* Upcoming in 7 Days */}
        <Card
          onClick={() => {
            setActiveTab("upcoming");
            setSelectedStatus("ALL");
          }}
          className={`p-3 cursor-pointer transition-all border ${
            activeTab === "upcoming"
              ? "ring-2 ring-blue-500 bg-blue-500/10 border-blue-500/40"
              : "hover:bg-accent/40 bg-card"
          }`}
        >
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-blue-700 dark:text-blue-400">
              Sắp tới
            </span>
            <CalendarDays className="h-4 w-4 text-blue-600 dark:text-blue-400" />
          </div>
          <div className="flex items-baseline gap-2 mt-1">
            <p className="text-2xl font-bold text-blue-700 dark:text-blue-400">
              {metrics.upcomingTotal}
            </p>
            <span className="text-[11px] text-muted-foreground">từ hôm nay trở đi</span>
          </div>
        </Card>

        {/* Arrived today */}
        <Card
          onClick={() => {
            setActiveTab("today");
            setSelectedStatus("ARRIVED");
          }}
          className={`p-3 cursor-pointer transition-all border ${
            activeTab === "today" && selectedStatus === "ARRIVED"
              ? "ring-2 ring-emerald-500 bg-emerald-500/10 border-emerald-500/40"
              : "hover:bg-accent/40 bg-card"
          }`}
        >
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-emerald-700 dark:text-emerald-400">
              Đã đến nhận bàn hôm nay
            </span>
            <UserCheck className="h-4 w-4 text-emerald-600 dark:text-emerald-400" />
          </div>
          <div className="flex items-baseline gap-2 mt-1">
            <p className="text-2xl font-bold text-emerald-700 dark:text-emerald-400">
              {metrics.todayArrived}
            </p>
            <span className="text-[11px] text-muted-foreground">khách đã đến</span>
          </div>
        </Card>
      </div>

      {/* Navigation Tabs Bar */}
      <div className="flex items-center gap-2 border-b pb-1 overflow-x-auto">
        <button
          type="button"
          onClick={() => {
            setActiveTab("calendar");
            setSelectedStatus("ALL");
          }}
          className={`flex items-center gap-2 px-3.5 py-2 text-xs font-medium rounded-t-md transition-colors border-b-2 whitespace-nowrap ${
            activeTab === "calendar"
              ? "border-primary text-primary bg-primary/10 font-semibold"
              : "border-transparent text-muted-foreground hover:text-foreground"
          }`}
        >
          <Filter className="h-3.5 w-3.5" />
          <span>Tra cứu theo ngày</span>
        </button>
      </div>

      {/* Control Filters (Search & Date & Status) */}
      <Card className="p-3 border">
        <div className="flex flex-col md:flex-row items-center gap-3">
          {/* Specific Date input if on calendar tab */}
          {activeTab === "calendar" && (
            <div className="flex items-center gap-2 w-full md:w-auto">
              <CalendarIcon className="h-4 w-4 text-muted-foreground shrink-0" />
              <Input
                type="date"
                value={selectedDate}
                onChange={(e) => setSelectedDate(e.target.value)}
                className="text-xs h-9 w-full md:w-44"
              />
            </div>
          )}

          {/* Status Filter (hidden on needs-action tab as it's always pending) */}
          {activeTab !== "needs-action" && (
            <div className="flex items-center gap-2 w-full md:w-52">
              <Filter className="h-4 w-4 text-muted-foreground shrink-0" />
              <Select value={selectedStatus} onValueChange={setSelectedStatus}>
                <SelectTrigger className="text-xs h-9">
                  <SelectValue placeholder="Lọc trạng thái" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ALL">Tất cả trạng thái</SelectItem>
                  <SelectItem value="PENDING_HOLD">Chờ xác nhận</SelectItem>
                  <SelectItem value="CONFIRMED">Đã xác nhận</SelectItem>
                  <SelectItem value="ARRIVED">Đã nhận bàn</SelectItem>
                  <SelectItem value="PAID">Đã thanh toán</SelectItem>
                  <SelectItem value="REVIEWED">Đã đánh giá</SelectItem>
                  <SelectItem value="CANCELLED">Đã hủy</SelectItem>
                  <SelectItem value="NO_SHOW">Không đến (No-Show)</SelectItem>
                </SelectContent>
              </Select>
            </div>
          )}

          {/* Search Input */}
          <div className="relative w-full md:flex-1">
            <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
            <Input
              type="text"
              placeholder="Tìm theo tên khách, SĐT (hoặc 3-4 số cuối), mã HV-..., số bàn..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="pl-8 text-xs h-9"
            />
          </div>

          {searchQuery && (
            <Button
              type="button"
              variant="ghost"
              size="sm"
              onClick={() => setSearchQuery("")}
              className="h-9 px-2.5 text-xs text-muted-foreground"
            >
              Xóa tìm kiếm
            </Button>
          )}
        </div>
      </Card>

      {/* Main Content Area: Grouped by Date */}
      {isLoadingAny ? (
        <div className="space-y-4">
          {Array.from({ length: 3 }).map((_, i) => (
            <div key={i} className="space-y-2">
              <Skeleton className="h-6 w-48 rounded" />
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
                <Skeleton className="h-36 w-full rounded-lg" />
                <Skeleton className="h-36 w-full rounded-lg" />
              </div>
            </div>
          ))}
        </div>
      ) : processedBookings.length === 0 ? (
        <Card className="p-12 text-center border">
          <div className="flex flex-col items-center justify-center gap-2">
            <AlertCircle className="h-8 w-8 text-muted-foreground/60 mb-1" />
            <p className="text-sm font-semibold text-foreground">
              {activeTab === "needs-action"
                ? "Không có đơn nào đang chờ duyệt"
                : activeTab === "today"
                  ? selectedStatus === "ARRIVED"
                    ? "Chưa có khách nào đến nhận bàn trong ngày hôm nay"
                    : "Chưa có đơn đặt bàn nào trong ngày hôm nay"
                  : activeTab === "upcoming"
                    ? "Không có đơn đặt bàn nào sắp tới"
                    : "Không tìm thấy yêu cầu đặt bàn nào phù hợp"}
            </p>
            <p className="text-xs text-muted-foreground max-w-sm">
              {activeTab === "needs-action"
                ? "Tất cả các yêu cầu đặt bàn trực tuyến đều đã được nhân viên xử lý."
                : "Các đơn đặt bàn mới từ khách hàng sẽ xuất hiện tự động theo thời gian thực."}
            </p>
          </div>
        </Card>
      ) : viewMode === "cards" ? (
        /* Smart Cards View */
        <div className="space-y-6">
          {groupedBookings.map((group) => (
            <div key={group.date} className="space-y-3">
              {/* Group Date Header */}
              <div className="flex items-center gap-2 border-b pb-1.5 pt-1">
                <CalendarIcon className="h-4 w-4 text-primary" />
                <h3 className="text-xs font-bold uppercase tracking-wider text-foreground">
                  {formatDisplayDate(group.date)}
                </h3>
                <Badge variant="secondary" className="text-[10px] h-4.5 px-1.5 font-normal">
                  {group.items.length} đơn
                </Badge>
              </div>

              {/* Cards Grid */}
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
                {group.items.map((booking) => (
                  <StaffBookingCard
                    key={booking.id}
                    booking={booking}
                    onOpenConfirm={handleOpenConfirm}
                    onCheckIn={(b) => void handleCheckIn(b)}
                    onCancel={handleOpenCancel}
                    isActionPending={updateStatusMutation.isPending}
                  />
                ))}
              </div>
            </div>
          ))}
        </div>
      ) : (
        /* Compact Table View */
        <div className="space-y-6">
          {groupedBookings.map((group) => (
            <div key={group.date} className="space-y-2">
              <div className="flex items-center gap-2 pt-2">
                <CalendarIcon className="h-4 w-4 text-primary" />
                <h3 className="text-xs font-bold uppercase tracking-wider text-foreground">
                  {formatDisplayDate(group.date)}
                </h3>
                <Badge variant="secondary" className="text-[10px] h-4.5 px-1.5 font-normal">
                  {group.items.length} đơn
                </Badge>
              </div>

              <Card className="border overflow-hidden">
                <div className="overflow-x-auto">
                  <Table>
                    <TableHeader className="bg-muted/50">
                      <TableRow className="text-xs">
                        <TableHead className="w-24">Mã Đặt</TableHead>
                        <TableHead>Khách Hàng</TableHead>
                        <TableHead className="text-center">Số Khách</TableHead>
                        <TableHead>Giờ Hẹn</TableHead>
                        <TableHead>Bàn / Khu Vực</TableHead>
                        <TableHead>Trạng Thái</TableHead>
                        <TableHead className="text-right">Thao Tác</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {group.items.map((booking) => (
                        <StaffBookingRow
                          key={booking.id}
                          booking={booking}
                          showDate={activeTab !== "today"}
                          onOpenConfirm={handleOpenConfirm}
                          onCheckIn={(b) => void handleCheckIn(b)}
                          onCancel={handleOpenCancel}
                          isActionPending={updateStatusMutation.isPending}
                        />
                      ))}
                    </TableBody>
                  </Table>
                </div>
              </Card>
            </div>
          ))}
        </div>
      )}

      {/* Confirm & Assign Table Dialog */}
      <ConfirmBookingDialog
        booking={confirmDialogBooking}
        open={isConfirmOpen}
        onOpenChange={setIsConfirmOpen}
      />

      {/* Cancel Dialog */}
      <CancelBookingDialog
        booking={cancelDialogBooking}
        open={isCancelOpen}
        onOpenChange={setIsCancelOpen}
        onConfirmCancel={handleStaffCancel}
      />
    </div>
  );
}
