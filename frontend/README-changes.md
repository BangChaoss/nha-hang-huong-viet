# Hương Việt frontend updates

## What changed
- `/` no longer shows the role/category selector. Without a signed-in user it displays the customer portal.
- Home resolves role from JSON stored in `localStorage` under `user`, `authUser`, `currentUser`, or `auth_user`, with a `role` value of `CUSTOMER`, `STAFF`, `CHEF`, or `MANAGER`.
- Manager dashboard now has **Phân quyền user** and **Voucher nhà hàng** tabs.
- Added `database-vouchers-migration.sql` for the voucher schema and usage history.

## Required integration with your existing login/backend
The uploaded frontend has no login page, authentication provider, user API, or backend API client, so role-based redirect cannot be connected to the actual login success handler from this frontend archive alone. After successful login, persist the user in this shape (or adapt `getCurrentRole()` in `src/routes/index.tsx` to your actual auth storage):

```ts
localStorage.setItem("user", JSON.stringify({
  id: response.user.id,
  name: response.user.name,
  role: response.user.role // CUSTOMER | STAFF | CHEF | MANAGER
}));
window.location.assign("/");
```

The manager user-role changes and voucher creation currently update UI state only. They are not saved to the database until API endpoints are added and called. Enforce role permissions on the backend as well; frontend role selection/visibility is not a security boundary.

## Database
Review the SQL migration against your actual `users.id`, `invoices.id`, and `users.role` definitions before applying. The migration assumes `CHAR(36)` IDs and MySQL 8.0.16+ for enforced CHECK constraints. Voucher redemption should lock the voucher row and update `used_count` plus `voucher_usages` in one transaction.
