import { z } from "zod";

export const createReservationSchema = z.object({
  guestName: z
    .string()
    .trim()
    .min(1, "Vui lòng nhập họ và tên")
    .max(100, "Họ và tên tối đa 100 ký tự"),

  guestPhone: z
    .string()
    .trim()
    .min(1, "Vui lòng nhập số điện thoại liên hệ")
    .regex(
      /^(0|\+84)[0-9]{9,10}$/,
      "Số điện thoại không hợp lệ (VD: 0912345678 hoặc +84912345678)",
    ),

  guestEmail: z
    .string()
    .trim()
    .email("Địa chỉ email không hợp lệ")
    .max(100, "Email tối đa 100 ký tự")
    .optional()
    .or(z.literal("")),

  reservationDate: z.string().min(1, "Vui lòng chọn ngày dùng bữa"),

  reservationTime: z.string().min(1, "Vui lòng chọn khung giờ"),

  partySize: z
    .number({ invalid_type_error: "Vui lòng chọn số lượng khách" })
    .min(1, "Số lượng khách ít nhất là 1 người")
    .max(15, "Đoàn trên 15 khách vui lòng liên hệ trực tiếp hotline để được đặt tiệc"),

  zoneId: z.string().min(1, "Vui lòng chọn khu vực bàn ăn"),

  specialRequests: z
    .string()
    .max(500, "Yêu cầu đặc biệt không vượt quá 500 ký tự")
    .optional()
    .or(z.literal("")),
});

export type CreateReservationFormValues = z.infer<typeof createReservationSchema>;

export const guestLookupSchema = z.object({
  phone: z
    .string()
    .trim()
    .min(1, "Vui lòng nhập số điện thoại")
    .regex(/^(0|\+84)[0-9]{9,10}$/, "Số điện thoại không hợp lệ"),
  code: z.string().trim().optional().or(z.literal("")),
});

export type GuestLookupFormValues = z.infer<typeof guestLookupSchema>;
