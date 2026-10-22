import { useState } from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { paymentApi } from "@/lib/payment-api";

type Props = {
  invoiceId: string;
  disabled?: boolean;
  className?: string;
};

/**
 * Sends the signed VNPAY request with an HTML form POST, as requested by the
 * integration contract. The amount and signature are always produced by Backend.
 */
export function VnpayPaymentButton({ invoiceId, disabled, className }: Props) {
  const [loading, setLoading] = useState(false);

  const startPayment = async () => {
    if (!invoiceId?.trim()) {
      toast.error("Không tìm thấy mã hóa đơn.");
      return;
    }

    setLoading(true);
    try {
      const checkout = await paymentApi.createVnpayPayment(invoiceId);
      const form = document.createElement("form");
      form.method = "POST";
      form.action = checkout.paymentUrl;
      form.style.display = "none";

      for (const [name, value] of Object.entries(checkout.parameters)) {
        const input = document.createElement("input");
        input.type = "hidden";
        input.name = name;
        input.value = value;
        form.appendChild(input);
      }

      document.body.appendChild(form);
      form.submit();
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Không thể khởi tạo thanh toán VNPAY.");
      setLoading(false);
    }
  };

  return (
    <Button type="button" className={className} disabled={disabled || loading} onClick={startPayment}>
      {loading ? "Đang chuyển đến VNPAY..." : "Thanh toán qua VNPAY"}
    </Button>
  );
}
