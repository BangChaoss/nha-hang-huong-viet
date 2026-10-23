import * as React from "react";
import { useForm, Controller } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Utensils, Users, PhoneCall, AlertTriangle, Loader2 } from "lucide-react";
import { useSession } from "@/lib/auth-session";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import {
  createReservationSchema,
  type CreateReservationFormValues,
} from "../utils/reservation-validators";
import { useCheckAvailability } from "../hooks/use-check-availability";
import { useCreateReservation } from "../hooks/use-create-reservation";
import { DatePickerField } from "./date-picker-field";
import { ZoneTimeSelector } from "./zone-time-selector";
import { BookingConfirmation } from "./booking-confirmation";
import type { ReservationResponse } from "../types/reservation.types";

interface BookFormProps {
  onDone?: () => void;
  initialValues?: Partial<CreateReservationFormValues>;
}

export function BookForm({ onDone, initialValues }: BookFormProps) {
  const { session } = useSession();
  const [createdReservation, setCreatedReservation] = React.useState<ReservationResponse | null>(
    null,
  );

  const form = useForm<CreateReservationFormValues>({
    resolver: zodResolver(createReservationSchema),
    defaultValues: {
      guestName: initialValues?.guestName ?? session?.username ?? "",
      guestPhone: initialValues?.guestPhone ?? "",
      guestEmail: initialValues?.guestEmail ?? session?.email ?? "",
      reservationDate: initialValues?.reservationDate ?? "",
      reservationTime: initialValues?.reservationTime ?? "",
      partySize: initialValues?.partySize ?? 2,
      zoneId: initialValues?.zoneId ?? "",
      specialRequests: initialValues?.specialRequests ?? "",
    },
    mode: "onTouched",
  });

  const {
    register,
    handleSubmit,
    control,
    watch,
    setValue,
    reset,
    formState: { errors },
  } = form;

  const watchedDate = watch("reservationDate");
  const watchedPartySize = watch("partySize");
  const watchedZoneId = watch("zoneId");
  const watchedTime = watch("reservationTime");

  // Query real-time availability when date and partySize are selected
  const { data: availabilityData, isLoading: isCheckingAvailability } = useCheckAvailability(
    watchedDate,
    watchedPartySize,
  );

  const createMutation = useCreateReservation();

  const isOverPartyLimit = watchedPartySize > 15;

  const onSubmit = async (values: CreateReservationFormValues) => {
    try {
      const result = await createMutation.mutateAsync({
        guestName: values.guestName.trim(),
        guestPhone: values.guestPhone.trim(),
        guestEmail: values.guestEmail?.trim() || null,
        reservationDate: values.reservationDate,
        reservationTime: values.reservationTime,
        partySize: values.partySize,
        zoneId: values.zoneId,
        specialRequests: values.specialRequests?.trim() || null,
      });

      setCreatedReservation(result);
    } catch {
      // Error is already toasted by useCreateReservation hook
    }
  };

  const handleResetForm = () => {
    setCreatedReservation(null);
    reset({
      guestName: session?.username ?? "",
      guestPhone: "",
      guestEmail: session?.email ?? "",
      reservationDate: "",
      reservationTime: "",
      partySize: 2,
      zoneId: "",
      specialRequests: "",
    });
  };

  // If reservation created successfully, render confirmation screen
  if (createdReservation) {
    return (
      <BookingConfirmation
        reservation={createdReservation}
        onReset={handleResetForm}
        onViewHistory={onDone}
      />
    );
  }

  return (
    <form
      onSubmit={handleSubmit(onSubmit)}
      className="surface-card mx-auto max-w-4xl space-y-8 p-6 sm:p-8"
      noValidate
    >
      <div className="border-b pb-4">
        <h2 className="text-2xl sm:text-3xl font-bold tracking-tight text-foreground flex items-center gap-3">
          <Utensils className="h-7 w-7 text-primary" />
          Đặt Bàn Trực Tuyến
        </h2>
        <p className="mt-1 text-sm text-muted-foreground">
          Quý khách vui lòng chọn ngày, quy mô đoàn và khung giờ để nhà hàng chuẩn bị chu đáo nhất.
        </p>
      </div>

      {/* BƯỚC 1: THỜI GIAN & SỐ LƯỢNG KHÁCH */}
      <section className="space-y-4">
        <div className="flex items-center gap-2 text-base font-semibold text-foreground">
          <span className="flex h-6 w-6 items-center justify-center rounded-full bg-primary/10 text-xs font-bold text-primary">
            1
          </span>
          <h3>Thời Gian & Quy Mô Khách</h3>
        </div>

        <div className="grid grid-cols-1 gap-5 sm:grid-cols-2">
          {/* Ngày đặt bàn */}
          <div className="space-y-1.5">
            <Label htmlFor="reservationDate">
              Ngày dùng bữa <span className="text-destructive">*</span>
            </Label>
            <Controller
              control={control}
              name="reservationDate"
              render={({ field }) => (
                <DatePickerField
                  value={field.value}
                  onChange={(dateStr) => {
                    field.onChange(dateStr);
                    // Reset selected time when date changes
                    setValue("reservationTime", "");
                  }}
                />
              )}
            />
            {errors.reservationDate && (
              <p className="text-xs text-destructive font-medium">
                {errors.reservationDate.message}
              </p>
            )}
          </div>

          {/* Số lượng khách */}
          <div className="space-y-1.5">
            <Label htmlFor="partySize">
              Số lượng khách (1–15 người) <span className="text-destructive">*</span>
            </Label>
            <div className="relative">
              <select
                id="partySize"
                {...register("partySize", { valueAsNumber: true })}
                className="h-9 w-full rounded-md border border-input bg-card px-3 text-sm focus:outline-none focus:ring-2 focus:ring-ring"
              >
                {Array.from({ length: 20 }, (_, i) => i + 1).map((num) => (
                  <option key={num} value={num}>
                    {num} người {num > 15 ? "(Vượt quá giới hạn đặt online)" : ""}
                  </option>
                ))}
              </select>
              <Users className="pointer-events-none absolute right-3 top-2.5 h-4 w-4 text-muted-foreground" />
            </div>
            {errors.partySize && (
              <p className="text-xs text-destructive font-medium">{errors.partySize.message}</p>
            )}
          </div>
        </div>

        {/* BR-17 Event Booking Notice */}
        {isOverPartyLimit && (
          <Alert
            variant="destructive"
            className="border-amber-500/50 bg-amber-500/10 text-amber-900 dark:text-amber-200"
          >
            <PhoneCall className="h-4 w-4 text-amber-600 dark:text-amber-400" />
            <AlertTitle className="font-semibold">Quy định đoàn khách lớn (BR-17)</AlertTitle>
            <AlertDescription className="text-xs mt-1 leading-relaxed">
              Đoàn trên 15 khách vui lòng liên hệ trực tiếp hotline <strong>1900 8888</strong> để
              được đặt tiệc (Event Booking) và nhận tư vấn thực đơn riêng biệt từ Quản lý nhà hàng.
            </AlertDescription>
          </Alert>
        )}
      </section>

      {/* BƯỚC 2: CHỌN KHU VỰC VÀ KHUNG GIỜ */}
      <section className="space-y-4 border-t pt-6">
        <div className="flex items-center gap-2 text-base font-semibold text-foreground">
          <span className="flex h-6 w-6 items-center justify-center rounded-full bg-primary/10 text-xs font-bold text-primary">
            2
          </span>
          <h3>Chọn Khu Vực & Khung Giờ</h3>
        </div>

        <ZoneTimeSelector
          selectedZoneId={watchedZoneId}
          selectedTime={watchedTime}
          onSelectZone={(zoneId) => setValue("zoneId", zoneId, { shouldValidate: true })}
          onSelectTime={(time) => setValue("reservationTime", time, { shouldValidate: true })}
          availabilityData={availabilityData}
          isLoading={isCheckingAvailability}
          partySize={watchedPartySize}
        />

        {(errors.zoneId || errors.reservationTime) && (
          <div className="rounded-md bg-destructive/10 p-3 text-xs text-destructive font-medium space-y-1">
            {errors.zoneId && <p>• {errors.zoneId.message}</p>}
            {errors.reservationTime && <p>• {errors.reservationTime.message}</p>}
          </div>
        )}
      </section>

      {/* BƯỚC 3: THÔNG TIN LIÊN HỆ */}
      <section className="space-y-4 border-t pt-6">
        <div className="flex items-center gap-2 text-base font-semibold text-foreground">
          <span className="flex h-6 w-6 items-center justify-center rounded-full bg-primary/10 text-xs font-bold text-primary">
            3
          </span>
          <h3>Thông Tin Người Đặt</h3>
        </div>

        <div className="grid grid-cols-1 gap-5 sm:grid-cols-2">
          {/* Họ tên */}
          <div className="space-y-1.5">
            <Label htmlFor="guestName">
              Họ và tên quý khách <span className="text-destructive">*</span>
            </Label>
            <Input id="guestName" placeholder="Nguyễn Văn A" {...register("guestName")} />
            {errors.guestName && (
              <p className="text-xs text-destructive font-medium">{errors.guestName.message}</p>
            )}
          </div>

          {/* Số điện thoại */}
          <div className="space-y-1.5">
            <Label htmlFor="guestPhone">
              Số điện thoại liên hệ <span className="text-destructive">*</span>
            </Label>
            <Input
              id="guestPhone"
              placeholder="0912345678 hoặc +84..."
              {...register("guestPhone")}
            />
            {errors.guestPhone && (
              <p className="text-xs text-destructive font-medium">{errors.guestPhone.message}</p>
            )}
          </div>

          {/* Email */}
          <div className="space-y-1.5 sm:col-span-2">
            <Label htmlFor="guestEmail">Địa chỉ Email (Nhận thư xác nhận)</Label>
            <Input
              id="guestEmail"
              type="email"
              placeholder="example@gmail.com"
              {...register("guestEmail")}
            />
            {errors.guestEmail && (
              <p className="text-xs text-destructive font-medium">{errors.guestEmail.message}</p>
            )}
          </div>

          {/* Yêu cầu đặc biệt */}
          <div className="space-y-1.5 sm:col-span-2">
            <Label htmlFor="specialRequests">
              Yêu cầu đặc biệt (Ăn kiêng, dị ứng, ghế trẻ em, sinh nhật...)
            </Label>
            <Textarea
              id="specialRequests"
              rows={3}
              placeholder="Ví dụ: Ăn chay, dị ứng tôm cua, cần chuẩn bị 1 ghế trẻ em..."
              {...register("specialRequests")}
            />
            {errors.specialRequests && (
              <p className="text-xs text-destructive font-medium">
                {errors.specialRequests.message}
              </p>
            )}
          </div>
        </div>
      </section>

      {/* NÚT GỬI ĐƠN */}
      <div className="border-t pt-6 flex flex-col sm:flex-row items-center justify-between gap-4">
        <p className="text-xs text-muted-foreground">
          Bằng việc bấm Gửi yêu cầu, quý khách đồng ý với các quy định giữ bàn của nhà hàng.
        </p>

        <Button
          type="submit"
          size="lg"
          disabled={createMutation.isPending || isOverPartyLimit}
          className="w-full sm:w-auto bg-primary text-primary-foreground hover:bg-primary/90 px-8"
        >
          {createMutation.isPending ? (
            <>
              <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              Đang gửi yêu cầu...
            </>
          ) : (
            "Gửi Yêu Cầu Đặt Bàn"
          )}
        </Button>
      </div>
    </form>
  );
}
