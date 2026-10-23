# VNPAY Sandbox + Loyalty Points

## Added APIs
- `POST /api/payments/vnpay/create` — creates a pending payment and returns `{ orderId, paymentUrl }`.
- `GET /api/payments/vnpay/ipn` — validates `vnp_SecureHash`, amount and order reference; idempotently changes the payment record and applies voucher usage / points only for a successful transaction.
- `GET /api/payments/vnpay/return` — validates the signed return and redirects to the frontend with `paymentOrderId` / `paymentStatus`.
- `GET /api/payments/vnpay/orders/{id}` — returns payment status for the result notice.
- `GET /api/admin/loyalty/settings`, `PUT /api/admin/loyalty/settings` — Manager/Admin-only rate configuration.

## Setup
1. Apply `database/V20261022__vnpay_loyalty.sql` to the existing application database.
2. Add values from `payment/VNPAY_CONFIG_EXAMPLE.properties` to the active Spring Boot `application.properties`, or configure equivalent environment variables.
3. Set the VNPAY Sandbox merchant code and hash secret from the VNPAY test merchant portal. Never put real secrets in frontend environment variables or commit them.
4. VNPAY must reach the Return URL and IPN URL over a public HTTPS URL. Configure the IPN URL in the VNPAY merchant portal (the gateway does not need the IPN URL in the signed payment query). For local development, use a secure tunnel.
5. Set `VITE_API_BASE_URL` on the frontend to the backend base URL, then restart both applications.
6. Use the new `VNPAY` payment method in Staff POS. Enter the customer's phone number; if a voucher is used, validate it first. On a confirmed successful callback, the backend records voucher usage and adds points using `paid amount * earn rate / 100`.

## Important integration boundary
The uploaded backend source contains the voucher/auth/account modules but no existing order/invoice entity or repository. This addition persists each VNPAY transaction in a new `payment_orders` table and updates that payment order's status. It does not mutate a separate legacy order table that was not present in the supplied source. If the full project has an order/invoice module outside this ZIP, connect `PaymentService.processCallback` to that module's paid-status transition.

The current POS's non-VNPAY payment methods and in-memory restaurant store are left intact. Loyalty points from VNPAY are stored in `loyalty_accounts`; the existing local demo balance/redeem feature is not silently treated as a database-backed points balance.
