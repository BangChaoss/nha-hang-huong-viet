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
import { ConfirmBookingDialog } from "./confirm-booking-dialog";
import { CancelBookingDialog } from "./cancel-booking-dialog";
import type { ReservationResponse, ReservationStatus } from "../types/reservation.types";

export function StaffBookingDashboard() {
  const [selectedDate, setSelectedDate] = React.useState<string>(() => {
    return new Date().toISOString().slice(0, 10);
  });
  const [selectedStatus, setSelectedStatus] = React.useState<string>("ALL");
  const [searchQuery, setSearchQuery] = React.useState<string>("");

  // Dialog state
  const [confirmDialogBooking, setConfirmDialogBooking] =
    React.useState<ReservationResponse | null>(null);
  const [isConfirmOpen, setIsConfirmOpen] = React.useState<boolean>(false);
  const [cancelDialogBooking, setCancelDialogBooking] = React.useState<ReservationResponse | null>(
    null,
  );
  const [isCancelOpen, setIsCancelOpen] = React.useState<boolean>(false);

  // Queries & Mutations
  const statusParam = selectedStatus !== "ALL" ? (selectedStatus as ReservationStatus) : undefined;
  const { data: bookings, isLoading, isFetching, refetch } = useAdminReservations(
    selectedDate,
    statusParam,
  );

  const updateStatusMutation = useUpdateReservationStatus();

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
      toast.success(`Check-in thành công cho đơn HV-${code}. Bàn đã chuyển sang trạng thái Đang Phục Vụ.`);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "Check-in thất bại";
      toast.error(msg);
    }
  };

  // Filter by search query (guest name, phone, code)
  const filteredBookings = React.useMemo(() => {
    if (!bookings) return [];
    if (!searchQuery.trim()) return bookings;
    const q = searchQuery.toLowerCase().trim();
    return bookings.filter(
      (b) =>
        b.guestName.toLowerCase().includes(q) ||
        b.guestPhone.toLowerCase().includes(q) ||
        b.id.toLowerCase().includes(q) ||
        (b.tableNumber && b.tableNumber.toLowerCase().includes(q)),
    );
  }, [bookings, searchQuery]);

  // Metrics
  const metrics = React.useMemo(() => {
    if (!bookings) return { total: 0, pending: 0, confirmed: 0, arrived: 0 };
    return {
      total: bookings.length,
      pending: bookings.filter((b) => b.status === "PENDING_HOLD").length,
      confirmed: bookings.filter((b) => b.status === "CONFIRMED").length,
      arrived: bookings.filter((b) => b.status === "ARRIVED").length,
    };
  }, [bookings]);

  return (
    <div className="space-y-6">
      {/* Top Header & Actions */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold tracking-tight text-foreground flex items-center gap-2">
            Quản Lý Đặt Bàn (Staff Dashboard)
          </h2>
          <p className="text-xs text-muted-foreground mt-0.5">
            Theo dõi, xác nhận yêu cầu đặt chỗ và thực hiện check-in bàn cho khách
          </p>
        </div>

        <div className="flex items-center gap-2">
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => void refetch()}
            disabled={isFetching}
            className="text-xs h-8"
          >
            <RefreshCw className={`h-3.5 w-3.5 mr-1 ${isFetching ? "animate-spin" : ""}`} />
            Làm mới
          </Button>
        </div>
      </div>

      {/* Metrics Bar */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
        <Card className="p-3 bg-card border">
          <div className="flex items-center justify-between">
            <span className="text-xs text-muted-foreground">Tổng đơn ngày</span>
            <Inbox className="h-4 w-4 text-muted-foreground" />
          </div>
          <p className="text-xl font-bold mt-1 text-foreground">{metrics.total}</p>
        </Card>

        <Card className="p-3 bg-amber-500/10 border-amber-500/20">
          <div className="flex items-center justify-between">
            <span className="text-xs text-amber-700 dark:text-amber-400 font-medium">
              Chờ xác nhận
            </span>
            <Clock className="h-4 w-4 text-amber-600 dark:text-amber-400" />
          </div>
          <p className="text-xl font-bold mt-1 text-amber-700 dark:text-amber-400">
            {metrics.pending}
          </p>
        </Card>

        <Card className="p-3 bg-emerald-500/10 border-emerald-500/20">
          <div className="flex items-center justify-between">
            <span className="text-xs text-emerald-700 dark:text-emerald-400 font-medium">
              Đã xác nhận
            </span>
            <CheckCircle2 className="h-4 w-4 text-emerald-600 dark:text-emerald-400" />
          </div>
          <p className="text-xl font-bold mt-1 text-emerald-700 dark:text-emerald-400">
            {metrics.confirmed}
          </p>
        </Card>

        <Card className="p-3 bg-blue-500/10 border-blue-500/20">
          <div className="flex items-center justify-between">
            <span className="text-xs text-blue-700 dark:text-blue-400 font-medium">
              Đã đến nhận bàn
            </span>
            <UserCheck className="h-4 w-4 text-blue-600 dark:text-blue-400" />
          </div>
          <p className="text-xl font-bold mt-1 text-blue-700 dark:text-blue-400">
            {metrics.arrived}
          </p>
        </Card>
      </div>

      {/* Control Filters */}
      <Card className="p-4 border">
        <div className="flex flex-col md:flex-row items-center gap-3">
          {/* Date Picker */}
          <div className="flex items-center gap-2 w-full md:w-auto">
            <CalendarIcon className="h-4 w-4 text-muted-foreground shrink-0" />
            <Input
              type="date"
              value={selectedDate}
              onChange={(e) => setSelectedDate(e.target.value)}
              className="text-xs h-9 w-full md:w-44"
            />
          </div>

          {/* Status Filter */}
          <div className="flex items-center gap-2 w-full md:w-56">
            <Filter className="h-4 w-4 text-muted-foreground shrink-0" />
            <Select value={selectedStatus} onValueChange={setSelectedStatus}>
              <SelectTrigger className="text-xs h-9">
                <SelectValue placeholder="Lọc trạng thái" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">Tất cả trạng thái</SelectItem>
                <SelectItem value="PENDING_HOLD">⚠️ Chờ xác nhận</SelectItem>
                <SelectItem value="CONFIRMED">✅ Đã xác nhận</SelectItem>
                <SelectItem value="ARRIVED">🔵 Đã nhận bàn</SelectItem>
                <SelectItem value="CANCELLED">❌ Đã hủy</SelectItem>
                <SelectItem value="NO_SHOW">⚪ Không đến (No-Show)</SelectItem>
              </SelectContent>
            </Select>
          </div>

          {/* Search Input */}
          <div className="relative w-full md:flex-1">
            <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
            <Input
              type="text"
              placeholder="Tìm theo tên khách, SĐT, mã đặt bàn, số bàn..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="pl-8 text-xs h-9"
            />
          </div>
        </div>
      </Card>

      {/* Bookings Table */}
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
              {isLoading ? (
                Array.from({ length: 4 }).map((_, i) => (
                  <TableRow key={i}>
                    <TableCell colSpan={7} className="p-4">
                      <Skeleton className="h-8 w-full rounded" />
                    </TableCell>
                  </TableRow>
                ))
              ) : filteredBookings.length > 0 ? (
                filteredBookings.map((booking) => (
                  <StaffBookingRow
                    key={booking.id}
                    booking={booking}
                    onOpenConfirm={handleOpenConfirm}
                    onCheckIn={(b) => void handleCheckIn(b)}
                    onCancel={handleOpenCancel}
                    isActionPending={updateStatusMutation.isPending}
                  />
                ))
              ) : (
                <TableRow>
                  <TableCell colSpan={7} className="h-32 text-center text-xs text-muted-foreground">
                    <div className="flex flex-col items-center justify-center gap-1">
                      <AlertCircle className="h-6 w-6 text-muted-foreground/60 mb-1" />
                      <span>Không tìm thấy yêu cầu đặt bàn nào trong ngày {selectedDate}.</span>
                    </div>
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </div>
      </Card>

      {/* Confirm & Assign Table Dialog */}
      <ConfirmBookingDialog
        booking={confirmDialogBooking}
        open={isConfirmOpen}
        onOpenChange={setIsConfirmOpen}
      />
      <CancelBookingDialog
        booking={cancelDialogBooking}
        open={isCancelOpen}
        onOpenChange={setIsCancelOpen}
        onConfirmCancel={handleStaffCancel}
      />
    </div>
  );
}
