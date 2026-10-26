import * as React from "react";
import { Search, History, CalendarX2, AlertCircle, Loader2 } from "lucide-react";
import { toast } from "sonner";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useSession } from "@/lib/auth-session";
import { useBookingHistory, useGuestLookup } from "../hooks/use-booking-history";
import { BookingHistoryCard } from "./booking-history-card";
import { CancelBookingDialog } from "./cancel-booking-dialog";
import type { ReservationResponse } from "../types/reservation.types";

export function BookingHistoryList() {
  const { session } = useSession();
  const isAuthenticated = Boolean(session?.token);

  // Authenticated query
  const { data: memberBookings, isLoading: isMemberLoading, refetch } = useBookingHistory();

  // Guest lookup mutation
  const guestLookupMutation = useGuestLookup();
  const [lookupPhone, setLookupPhone] = React.useState("");
  const [lookupCode, setLookupCode] = React.useState("");
  const [guestResults, setGuestResults] = React.useState<ReservationResponse[] | null>(null);

  // Cancel dialog state
  const [cancelTarget, setCancelTarget] = React.useState<ReservationResponse | null>(null);
  const [isCancelOpen, setIsCancelOpen] = React.useState<boolean>(false);

  const handleOpenCancel = (booking: ReservationResponse) => {
    setCancelTarget(booking);
    setIsCancelOpen(true);
  };

  const handleGuestSearch = async (e: React.FormEvent) => {
    e.preventDefault();
    const phone = lookupPhone.trim();
    const code = lookupCode.trim();

    if (!phone) {
      toast.error("Vui lòng nhập số điện thoại đặt bàn");
      return;
    }
    if (!code) {
      toast.error("Vui lòng nhập mã đặt bàn");
      return;
    }

    try {
      const data = await guestLookupMutation.mutateAsync({ phone, code });
      setGuestResults(data);

      if (data.length === 0) {
        toast.info("Không tìm thấy đơn đặt bàn nào khớp với số điện thoại và mã đặt bàn đã cung cấp");
      }
    } catch (err: unknown) {
      const errorMsg = err instanceof Error ? err.message : "Tra cứu thất bại";
      toast.error(errorMsg);
    }
  };

  return (
    <div className="mx-auto max-w-4xl space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-2xl font-bold tracking-tight text-foreground flex items-center gap-2">
            <History className="h-6 w-6 text-primary" />
            Lịch Sử Đặt Bàn
          </h2>
          <p className="text-sm text-muted-foreground mt-0.5">
            Xem lại danh sách các lần đặt chỗ và theo dõi tình trạng phê duyệt
          </p>
        </div>
      </div>

      <Tabs defaultValue={isAuthenticated ? "member" : "guest"} className="space-y-6">
        <TabsList className="grid w-full grid-cols-2">
          {isAuthenticated && <TabsTrigger value="member">Lịch Sử Của Tôi</TabsTrigger>}
          <TabsTrigger value="guest" className={!isAuthenticated ? "col-span-2" : ""}>
            Tra Cứu Khách Vãng Lai
          </TabsTrigger>
        </TabsList>

        {isAuthenticated && (
          <TabsContent value="member" className="space-y-4">
            {isMemberLoading ? (
              <div className="space-y-3">
                <Skeleton className="h-28 rounded-xl" />
                <Skeleton className="h-28 rounded-xl" />
                <Skeleton className="h-28 rounded-xl" />
              </div>
            ) : memberBookings && memberBookings.length > 0 ? (
              <div className="space-y-3">
                <div className="flex justify-end">
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => void refetch()}
                    className="text-xs"
                  >
                    Làm mới danh sách
                  </Button>
                </div>
                {memberBookings.map((booking) => (
                  <BookingHistoryCard
                    key={booking.id}
                    booking={booking}
                    onCancelClick={handleOpenCancel}
                  />
                ))}
              </div>
            ) : (
              <div className="rounded-xl border border-dashed p-10 text-center">
                <CalendarX2 className="mx-auto mb-3 h-10 w-10 text-muted-foreground/60" />
                <h3 className="text-base font-semibold text-foreground">
                  Bạn chưa có đơn đặt bàn nào
                </h3>
                <p className="mt-1 text-sm text-muted-foreground">
                  Hãy chọn tab &ldquo;Đặt Bàn Trước&rdquo; để đặt chỗ cho bữa ăn sum vầy tiếp theo!
                </p>
              </div>
            )}
          </TabsContent>
        )}

        <TabsContent value="guest" className="space-y-6">
          <form
            onSubmit={(e) => void handleGuestSearch(e)}
            className="rounded-xl border bg-card p-5 shadow-sm space-y-4"
          >
            <div className="flex items-center gap-2 border-b pb-3">
              <Search className="h-4 w-4 text-primary" />
              <div>
                <h3 className="font-semibold text-sm text-foreground">
                  Tra cứu nhanh bằng Số Điện Thoại & Mã Đặt Bàn
                </h3>
                <p className="text-xs text-muted-foreground">
                  Để bảo mật thông tin, quý khách vui lòng nhập chính xác cả 2 thông tin đã đăng ký.
                </p>
              </div>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div className="space-y-1.5">
                <Label htmlFor="lookup-phone">
                  Số điện thoại đặt bàn <span className="text-destructive">*</span>
                </Label>
                <Input
                  id="lookup-phone"
                  placeholder="VD: 0912345678"
                  value={lookupPhone}
                  onChange={(e) => setLookupPhone(e.target.value)}
                  required
                />
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="lookup-code">
                  Mã đặt bàn <span className="text-destructive">*</span>
                </Label>
                <Input
                  id="lookup-code"
                  placeholder="VD: HV-K8M2N9 hoặc K8M2N9"
                  value={lookupCode}
                  onChange={(e) => setLookupCode(e.target.value)}
                  required
                />
              </div>
            </div>

            <div className="flex justify-end pt-2">
              <Button
                type="submit"
                disabled={guestLookupMutation.isPending}
                className="gap-2 bg-primary text-primary-foreground"
              >
                {guestLookupMutation.isPending ? (
                  <>
                    <Loader2 className="h-4 w-4 animate-spin" />
                    Đang tìm kiếm...
                  </>
                ) : (
                  <>
                    <Search className="h-4 w-4" />
                    Tra Cứu Đặt Bàn
                  </>
                )}
              </Button>
            </div>
          </form>

          {/* Guest Results */}
          {guestResults !== null && (
            <div className="space-y-4">
              <h4 className="text-sm font-semibold text-foreground">
                Kết quả tra cứu ({guestResults.length} đơn):
              </h4>

              {guestResults.length > 0 ? (
                <div className="space-y-3">
                  {guestResults.map((booking) => (
                    <BookingHistoryCard
                      key={booking.id}
                      booking={booking}
                      onCancelClick={handleOpenCancel}
                    />
                  ))}
                </div>
              ) : (
                <div className="rounded-xl border border-dashed p-8 text-center text-muted-foreground">
                  <AlertCircle className="mx-auto mb-2 h-8 w-8 text-muted-foreground/60" />
                  <p className="text-sm">
                    Không tìm thấy thông tin đặt bàn nào khớp với số điện thoại{" "}
                    <strong>{lookupPhone}</strong> và mã <strong>{lookupCode}</strong>.
                  </p>
                  <p className="text-xs mt-1 text-muted-foreground">
                    Quý khách vui lòng kiểm tra lại thông tin hoặc liên hệ trực tiếp hotline nhà hàng để được hỗ trợ.
                  </p>
                </div>
              )}
            </div>
          )}
        </TabsContent>
      </Tabs>

      {/* Cancel Confirmation Dialog */}
      <CancelBookingDialog
        booking={cancelTarget}
        open={isCancelOpen}
        onOpenChange={setIsCancelOpen}
      />
    </div>
  );
}
