import { createFileRoute, Link, useNavigate } from "@tanstack/react-router";
import { useState, type FormEvent } from "react";
import { ArrowLeft, UtensilsCrossed } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";


export const Route = createFileRoute("/login")({ component: LoginPage });

function LoginPage() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const navigate = useNavigate();

  function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    // Authentication must be validated by the backend; never grant a role from client input.
    window.alert("Frontend hiện chưa được kết nối API đăng nhập. Hãy tích hợp API xác thực để đăng nhập và điều hướng theo role.");
  }

  return <main className="flex min-h-screen items-center justify-center bg-background px-4 py-10">
    <div className="w-full max-w-md rounded-2xl border bg-card p-8 shadow-sm">
      <Link to="/" className="mb-8 inline-flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground"><ArrowLeft className="h-4 w-4" /> Về trang khách hàng</Link>
      <div className="mb-6 flex items-center gap-3"><div className="flex h-11 w-11 items-center justify-center rounded-full bg-primary text-primary-foreground"><UtensilsCrossed className="h-5 w-5" /></div><div><p className="font-display text-xl font-semibold">HƯƠNG VIỆT</p><p className="text-xs text-muted-foreground">Đăng nhập hệ thống</p></div></div>
      <h1 className="text-2xl font-semibold">Đăng nhập</h1>
      <p className="mt-2 text-sm text-muted-foreground">Sử dụng tài khoản được cấp để truy cập hệ thống.</p>
      <form onSubmit={submit} className="mt-6 space-y-4">
        <div className="space-y-2"><Label htmlFor="email">Email / Tên đăng nhập</Label><input id="email" type="text" autoComplete="username" required value={email} onChange={e=>setEmail(e.target.value)} className="h-10 w-full rounded-md border border-input bg-background px-3 text-sm" /></div>
        <div className="space-y-2"><Label htmlFor="password">Mật khẩu</Label><input id="password" type="password" autoComplete="current-password" required value={password} onChange={e=>setPassword(e.target.value)} className="h-10 w-full rounded-md border border-input bg-background px-3 text-sm" /></div>
        <Button type="submit" className="w-full">Đăng nhập</Button>
      </form>
      <p className="mt-4 text-xs leading-relaxed text-muted-foreground">Lưu ý: API xác thực chưa có trong frontend được cung cấp. Không thể xác minh tài khoản hoặc phân quyền an toàn cho đến khi kết nối backend.</p>
    </div>
  </main>;
}
