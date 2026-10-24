import * as React from "react";
import { useEffect, useMemo, useRef, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { Client } from "@stomp/stompjs";
import { Loader2, Pencil, Plus, Trash2, UtensilsCrossed } from "lucide-react";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Textarea } from "@/components/ui/textarea";
import { cn } from "@/lib/utils";
import { formatVND } from "@/lib/restaurant-store";
import { getSession } from "@/lib/auth-session";
import { resolveImageUrl } from "@/lib/image-url";
import { FileUpload } from "@/components/hv/FileUpload";
import { PaginationControls } from "@/components/hv/PaginationControls";
import { API_BASE } from "@/lib/table-api";
import {
  useAdminCategories,
  useAdminMenuItems,
  useCreateCategory,
  useCreateItem,
  useDeleteCategory,
  useDeleteItem,
  useSetAvailability,
  useUpdateCategory,
  useUpdateItem,
} from "../hooks/use-admin-menu";
import type {
  CategoryResponse,
  MenuItemFilterParams,
  MenuItemResponse,
  MenuItemSortKey,
  SortDirection,
} from "../api/menu-api";

const ALL_CATEGORIES = "all";
const ALL_AVAILABILITY = "all";
const SEARCH_DEBOUNCE_MS = 300;

/** Số món trên một trang. */
const PAGE_SIZE_OPTIONS: number[] = [10, 20, 50];
const DEFAULT_PAGE_SIZE = 10;

/** Các cách sắp xếp — thời gian tạo được ưu tiên đặt lên đầu. */
const SORT_OPTIONS: { value: MenuItemSortKey; label: string }[] = [
  { value: "createdAt", label: "Mới nhất (thời gian tạo)" },
  { value: "updatedAt", label: "Cập nhật gần nhất" },
  { value: "name", label: "Tên món (A → Z)" },
  { value: "price", label: "Giá (thấp → cao)" },
];

/**
 * Màn hình quản lý thực đơn cho Manager/Admin.
 * STT 74 (quản lý danh mục), STT 75 (quản lý món ăn), STT 76 (bật/tắt tình trạng món).
 */
export function MenuManagement() {
  useMenuRefreshSubscription();

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="text-xs uppercase tracking-[0.2em] text-muted-foreground">Quản trị</p>
          <h1 className="font-display text-3xl font-semibold">Quản lý Thực đơn</h1>
        </div>
      </div>

      <Tabs defaultValue="categories" className="space-y-6">
        <TabsList>
          <TabsTrigger value="categories">Danh mục</TabsTrigger>
          <TabsTrigger value="items">Món ăn</TabsTrigger>
        </TabsList>
        <TabsContent value="categories">
          <CategoriesTab />
        </TabsContent>
        <TabsContent value="items">
          <ItemsTab />
        </TabsContent>
      </Tabs>
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/*                                  Danh mục                                  */
/* -------------------------------------------------------------------------- */

type CategoryFormState = {
  categoryName: string;
  displayOrder: string;
};

const EMPTY_CATEGORY_FORM: CategoryFormState = { categoryName: "", displayOrder: "0" };

function CategoriesTab() {
  const categoriesQuery = useAdminCategories();
  const createCategory = useCreateCategory();
  const updateCategory = useUpdateCategory();
  const deleteCategory = useDeleteCategory();

  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<CategoryResponse | null>(null);
  const [form, setForm] = useState<CategoryFormState>(EMPTY_CATEGORY_FORM);
  const [formError, setFormError] = useState<string | null>(null);
  const [pendingDelete, setPendingDelete] = useState<CategoryResponse | null>(null);

  const openCreateForm = () => {
    setEditing(null);
    setForm(EMPTY_CATEGORY_FORM);
    setFormError(null);
    setFormOpen(true);
  };

  const openEditForm = (category: CategoryResponse) => {
    setEditing(category);
    setForm({
      categoryName: category.categoryName,
      displayOrder: String(category.displayOrder ?? 0),
    });
    setFormError(null);
    setFormOpen(true);
  };

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    const name = form.categoryName.trim();
    if (!name) {
      setFormError("Vui lòng nhập tên danh mục.");
      return;
    }
    const displayOrder = Number.parseInt(form.displayOrder, 10);
    const payload = {
      categoryName: name,
      displayOrder: Number.isFinite(displayOrder) ? displayOrder : 0,
    };

    try {
      if (editing) {
        await updateCategory.mutateAsync({ id: editing.id, payload });
      } else {
        await createCategory.mutateAsync(payload);
      }
      setFormOpen(false);
      setEditing(null);
    } catch {
      // Thông báo lỗi đã được hiển thị bởi hook mutation
    }
  };

  const handleDelete = async () => {
    if (!pendingDelete) return;
    try {
      await deleteCategory.mutateAsync(pendingDelete.id);
      setPendingDelete(null);
    } catch (error) {
      // Backend trả 409 nếu danh mục còn món — hook đã hiển thị message của server
      setPendingDelete(null);
      if (error instanceof Error) {
        setFormError(error.message);
      }
    }
  };

  const isMutating = createCategory.isPending || updateCategory.isPending;

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-xl font-medium">Danh mục món ăn</h2>
        <Button type="button" onClick={openCreateForm} size="sm">
          <Plus className="mr-1.5 h-4 w-4" />
          Thêm danh mục
        </Button>
      </div>

      {categoriesQuery.isLoading ? (
        <LoadingState label="Đang tải danh mục…" />
      ) : categoriesQuery.isError ? (
        <ErrorState
          message={categoriesQuery.error?.message ?? "Không thể tải danh mục."}
          onRetry={() => void categoriesQuery.refetch()}
        />
      ) : categoriesQuery.data && categoriesQuery.data.length > 0 ? (
        <div className="surface-card overflow-x-auto p-5">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b text-left text-muted-foreground">
                <th className="py-2 font-medium">Tên danh mục</th>
                <th className="font-medium">Thứ tự hiển thị</th>
                <th className="font-medium">Số món</th>
                <th className="text-right font-medium">Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {categoriesQuery.data.map((category) => (
                <tr key={category.id} className="border-b last:border-0">
                  <td className="py-3 font-medium">{category.categoryName}</td>
                  <td className="tabular-nums text-muted-foreground">{category.displayOrder}</td>
                  <td className="tabular-nums text-muted-foreground">{category.menuItemCount}</td>
                  <td className="text-right">
                    <div className="flex items-center justify-end gap-2">
                      <Button
                        type="button"
                        variant="ghost"
                        size="sm"
                        aria-label={`Sửa danh mục ${category.categoryName}`}
                        onClick={() => openEditForm(category)}
                      >
                        <Pencil className="h-4 w-4" />
                      </Button>
                      <Button
                        type="button"
                        variant="ghost"
                        size="sm"
                        aria-label={`Xóa danh mục ${category.categoryName}`}
                        onClick={() => setPendingDelete(category)}
                      >
                        <Trash2 className="h-4 w-4 text-alert" />
                      </Button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <EmptyState label="Chưa có danh mục nào." />
      )}

      {/* Form thêm / sửa danh mục */}
      <Dialog open={formOpen} onOpenChange={setFormOpen}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>{editing ? "Sửa danh mục" : "Thêm danh mục"}</DialogTitle>
            <DialogDescription>
              {editing
                ? "Cập nhật tên hoặc thứ tự hiển thị của danh mục."
                : "Tạo danh mục mới để phân loại các món ăn."}
            </DialogDescription>
          </DialogHeader>

          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="space-y-1.5">
              <Label htmlFor="categoryName">Tên danh mục</Label>
              <Input
                id="categoryName"
                value={form.categoryName}
                onChange={(event) =>
                  setForm((prev) => ({ ...prev, categoryName: event.target.value }))
                }
                placeholder="Ví dụ: Khai vị"
                required
              />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="categoryDisplayOrder">Thứ tự hiển thị</Label>
              <Input
                id="categoryDisplayOrder"
                type="number"
                min={0}
                value={form.displayOrder}
                onChange={(event) =>
                  setForm((prev) => ({ ...prev, displayOrder: event.target.value }))
                }
              />
            </div>

            {formError && <p className="text-sm font-medium text-alert">{formError}</p>}

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setFormOpen(false)}>
                Hủy
              </Button>
              <Button type="submit" disabled={isMutating}>
                {isMutating && <Loader2 className="mr-1.5 h-4 w-4 animate-spin" />}
                {editing ? "Lưu thay đổi" : "Thêm danh mục"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* Xác nhận xóa danh mục */}
      <AlertDialog
        open={pendingDelete !== null}
        onOpenChange={(open) => {
          if (!open) setPendingDelete(null);
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Xóa danh mục?</AlertDialogTitle>
            <AlertDialogDescription>
              {pendingDelete
                ? `Danh mục "${pendingDelete.categoryName}" sẽ bị xóa. Nếu danh mục còn món ăn, thao tác sẽ không được phép thực hiện.`
                : ""}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Giữ lại</AlertDialogCancel>
            <AlertDialogAction onClick={() => void handleDelete()}>Xóa</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/*                                  Món ăn                                    */
/* -------------------------------------------------------------------------- */

type ItemFormState = {
  categoryId: string;
  itemName: string;
  description: string;
  currentPrice: string;
  imageUrl: string;
  isAvailable: boolean;
};

const EMPTY_ITEM_FORM: ItemFormState = {
  categoryId: "",
  itemName: "",
  description: "",
  currentPrice: "",
  imageUrl: "",
  isAvailable: true,
};

function ItemsTab() {
  const categoriesQuery = useAdminCategories();
  const [categoryFilter, setCategoryFilter] = useState<string>(ALL_CATEGORIES);
  const [availabilityFilter, setAvailabilityFilter] = useState<string>(ALL_AVAILABILITY);
  const [searchInput, setSearchInput] = useState<string>("");
  const [search, setSearch] = useState<string>("");
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  // Mặc định sắp xếp theo thời gian tạo — món mới nhất lên đầu.
  const [sortKey, setSortKey] = useState<MenuItemSortKey>("createdAt");
  const [sortDirection, setSortDirection] = useState<SortDirection>("desc");

  useEffect(() => {
    const timer = setTimeout(() => setSearch(searchInput.trim()), SEARCH_DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [searchInput]);

  // Đổi bộ lọc hoặc cách sắp xếp thì quay lại trang đầu tiên.
  useEffect(() => {
    setPage(0);
  }, [categoryFilter, search, availabilityFilter, pageSize, sortKey, sortDirection]);

  const params = useMemo(() => {
    const result: MenuItemFilterParams = {
      page,
      size: pageSize,
      sort: sortKey,
      direction: sortDirection,
    };
    if (categoryFilter !== ALL_CATEGORIES) result.categoryId = categoryFilter;
    if (search) result.q = search;
    if (availabilityFilter === "available") result.available = true;
    if (availabilityFilter === "unavailable") result.available = false;
    return result;
  }, [categoryFilter, search, availabilityFilter, page, pageSize, sortKey, sortDirection]);

  const itemsQuery = useAdminMenuItems(params);
  const createItem = useCreateItem();
  const updateItem = useUpdateItem();
  const deleteItem = useDeleteItem();
  const setAvailability = useSetAvailability();

  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<MenuItemResponse | null>(null);
  const [form, setForm] = useState<ItemFormState>(EMPTY_ITEM_FORM);
  const [formError, setFormError] = useState<string | null>(null);
  const [pendingDelete, setPendingDelete] = useState<MenuItemResponse | null>(null);

  const categories = categoriesQuery.data ?? [];

  const openCreateForm = () => {
    setEditing(null);
    setForm({ ...EMPTY_ITEM_FORM, categoryId: categories[0]?.id ?? "" });
    setFormError(null);
    setFormOpen(true);
  };

  const openEditForm = (item: MenuItemResponse) => {
    setEditing(item);
    setForm({
      categoryId: item.categoryId,
      itemName: item.itemName,
      description: item.description ?? "",
      currentPrice: String(item.currentPrice ?? 0),
      imageUrl: item.imageUrl ?? "",
      isAvailable: item.isAvailable,
    });
    setFormError(null);
    setFormOpen(true);
  };

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    const name = form.itemName.trim();
    if (!name) {
      setFormError("Vui lòng nhập tên món ăn.");
      return;
    }
    if (!form.categoryId) {
      setFormError("Vui lòng chọn danh mục cho món ăn.");
      return;
    }
    const price = Number.parseFloat(form.currentPrice);
    if (!Number.isFinite(price) || price < 0) {
      setFormError("Vui lòng nhập giá hợp lệ.");
      return;
    }

    const payload = {
      categoryId: form.categoryId,
      itemName: name,
      description: form.description.trim() ? form.description.trim() : null,
      currentPrice: price,
      imageUrl: form.imageUrl.trim() ? form.imageUrl.trim() : null,
      isAvailable: form.isAvailable,
    };

    try {
      if (editing) {
        await updateItem.mutateAsync({ id: editing.id, payload });
      } else {
        await createItem.mutateAsync(payload);
      }
      setFormOpen(false);
      setEditing(null);
    } catch {
      // Thông báo lỗi đã được hiển thị bởi hook mutation
    }
  };

  const handleToggleAvailability = async (item: MenuItemResponse, nextValue: boolean) => {
    try {
      await setAvailability.mutateAsync({ id: item.id, isAvailable: nextValue });
    } catch {
      // Thông báo lỗi đã được hiển thị bởi hook mutation
    }
  };

  const handleDelete = async () => {
    if (!pendingDelete) return;
    try {
      await deleteItem.mutateAsync(pendingDelete.id);
      setPendingDelete(null);
    } catch (error) {
      setPendingDelete(null);
      if (error instanceof Error) {
        setFormError(error.message);
      }
    }
  };

  const isMutating = createItem.isPending || updateItem.isPending;

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-xl font-medium">Món ăn</h2>
        <Button type="button" onClick={openCreateForm} size="sm">
          <Plus className="mr-1.5 h-4 w-4" />
          Thêm món
        </Button>
      </div>

      {/* Bộ lọc */}
      <div className="surface-card grid gap-3 p-4 sm:grid-cols-2 lg:grid-cols-5">
        <div className="space-y-1.5">
          <Label htmlFor="filter-category">Danh mục</Label>
          <Select value={categoryFilter} onValueChange={setCategoryFilter}>
            <SelectTrigger id="filter-category">
              <SelectValue placeholder="Tất cả" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL_CATEGORIES}>Tất cả</SelectItem>
              {categories.map((category) => (
                <SelectItem key={category.id} value={category.id}>
                  {category.categoryName}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="filter-search">Tìm theo tên</Label>
          <Input
            id="filter-search"
            value={searchInput}
            onChange={(event) => setSearchInput(event.target.value)}
            placeholder="Nhập tên món ăn..."
          />
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="filter-availability">Tình trạng</Label>
          <Select value={availabilityFilter} onValueChange={setAvailabilityFilter}>
            <SelectTrigger id="filter-availability">
              <SelectValue placeholder="Tất cả" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL_AVAILABILITY}>Tất cả</SelectItem>
              <SelectItem value="available">Còn món</SelectItem>
              <SelectItem value="unavailable">Hết món</SelectItem>
            </SelectContent>
          </Select>
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="filter-sort">Sắp xếp</Label>
          <Select value={sortKey} onValueChange={(value) => setSortKey(value as MenuItemSortKey)}>
            <SelectTrigger id="filter-sort">
              <SelectValue placeholder="Sắp xếp" />
            </SelectTrigger>
            <SelectContent>
              {SORT_OPTIONS.map((option) => (
                <SelectItem key={option.value} value={option.value}>
                  {option.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="filter-direction">Thứ tự</Label>
          <Select
            value={sortDirection}
            onValueChange={(value) => setSortDirection(value as SortDirection)}
          >
            <SelectTrigger id="filter-direction">
              <SelectValue placeholder="Thứ tự" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="desc">Giảm dần</SelectItem>
              <SelectItem value="asc">Tăng dần</SelectItem>
            </SelectContent>
          </Select>
        </div>
      </div>

      {itemsQuery.isLoading ? (
        <LoadingState label="Đang tải món ăn…" />
      ) : itemsQuery.isError ? (
        <ErrorState
          message={itemsQuery.error?.message ?? "Không thể tải danh sách món ăn."}
          onRetry={() => void itemsQuery.refetch()}
        />
      ) : itemsQuery.data && itemsQuery.data.content.length > 0 ? (
        <div className="surface-card overflow-x-auto p-5">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b text-left text-muted-foreground">
                <th className="py-2 font-medium">Món</th>
                <th className="font-medium">Danh mục</th>
                <th className="font-medium">Giá</th>
                <th className="font-medium">Tình trạng</th>
                <th className="text-right font-medium">Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {itemsQuery.data.content.map((item) => (
                <tr key={item.id} className="border-b last:border-0">
                  <td className="py-3">
                    <div className="flex items-center gap-3">
                      <div className="h-11 w-11 shrink-0 overflow-hidden rounded-md bg-muted">
                        {item.imageUrl ? (
                          <img
                            src={resolveImageUrl(item.imageUrl) ?? undefined}
                            alt={item.itemName}
                            loading="lazy"
                            className={cn(
                              "h-full w-full object-cover",
                              !item.isAvailable && "grayscale",
                            )}
                          />
                        ) : (
                          <div className="flex h-full w-full items-center justify-center">
                            <UtensilsCrossed className="h-4 w-4 text-muted-foreground" />
                          </div>
                        )}
                      </div>
                      <div>
                        <p className="font-medium">{item.itemName}</p>
                        {item.description && (
                          <p className="max-w-xs truncate text-xs text-muted-foreground">
                            {item.description}
                          </p>
                        )}
                      </div>
                    </div>
                  </td>
                  <td className="text-muted-foreground">{item.categoryName}</td>
                  <td className="tabular-nums">{formatVND(item.currentPrice)}</td>
                  <td>
                    <div className="flex items-center gap-3">
                      <span
                        className={cn(
                          "rounded-full px-2.5 py-0.5 text-xs",
                          item.isAvailable ? "status-available" : "status-cancelled",
                        )}
                      >
                        {item.isAvailable ? "Còn món" : "Hết món"}
                      </span>
                      <Switch
                        checked={item.isAvailable}
                        disabled={setAvailability.isPending}
                        aria-label={`Bật/tắt tình trạng món ${item.itemName}`}
                        onCheckedChange={(checked) => void handleToggleAvailability(item, checked)}
                      />
                    </div>
                  </td>
                  <td className="text-right">
                    <div className="flex items-center justify-end gap-2">
                      <Button
                        type="button"
                        variant="ghost"
                        size="sm"
                        aria-label={`Sửa món ${item.itemName}`}
                        onClick={() => openEditForm(item)}
                      >
                        <Pencil className="h-4 w-4" />
                      </Button>
                      <Button
                        type="button"
                        variant="ghost"
                        size="sm"
                        aria-label={`Xóa món ${item.itemName}`}
                        onClick={() => setPendingDelete(item)}
                      >
                        <Trash2 className="h-4 w-4 text-alert" />
                      </Button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <EmptyState label="Chưa có món ăn nào." />
      )}

      {/* Phân trang */}
      {itemsQuery.data && itemsQuery.data.totalElements > 0 && (
        <div className="surface-card flex flex-wrap items-center justify-between gap-4 p-4">
          <div className="flex items-center gap-3 text-sm text-muted-foreground">
            <span className="tabular-nums">
              Tổng {itemsQuery.data.totalElements} món · Trang{" "}
              {itemsQuery.data.totalPages === 0 ? 0 : itemsQuery.data.page + 1}/
              {Math.max(itemsQuery.data.totalPages, 1)}
            </span>
            <div className="flex items-center gap-2">
              <Label htmlFor="filter-page-size" className="text-xs">
                Số món/trang
              </Label>
              <Select
                value={String(pageSize)}
                onValueChange={(value) => setPageSize(Number(value))}
              >
                <SelectTrigger id="filter-page-size" className="h-8 w-[4.5rem]">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {PAGE_SIZE_OPTIONS.map((option) => (
                    <SelectItem key={option} value={String(option)}>
                      {option}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>

          <PaginationControls
            page={itemsQuery.data.page}
            totalPages={Math.max(itemsQuery.data.totalPages, 1)}
            onPageChange={setPage}
          />
        </div>
      )}

      {/* Form thêm / sửa món ăn */}
      <Dialog open={formOpen} onOpenChange={setFormOpen}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>{editing ? "Sửa món ăn" : "Thêm món ăn"}</DialogTitle>
            <DialogDescription>
              {editing ? "Cập nhật thông tin món ăn." : "Thêm món ăn mới vào danh mục đã chọn."}
            </DialogDescription>
          </DialogHeader>

          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="space-y-1.5">
              <Label htmlFor="itemCategory">Danh mục</Label>
              <Select
                value={form.categoryId}
                onValueChange={(value) => setForm((prev) => ({ ...prev, categoryId: value }))}
              >
                <SelectTrigger id="itemCategory">
                  <SelectValue placeholder="Chọn danh mục" />
                </SelectTrigger>
                <SelectContent>
                  {categories.map((category) => (
                    <SelectItem key={category.id} value={category.id}>
                      {category.categoryName}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="itemName">Tên món ăn</Label>
              <Input
                id="itemName"
                value={form.itemName}
                onChange={(event) => setForm((prev) => ({ ...prev, itemName: event.target.value }))}
                placeholder="Ví dụ: Gà hấp mật ong"
                required
              />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="itemDescription">Mô tả</Label>
              <Textarea
                id="itemDescription"
                rows={3}
                value={form.description}
                onChange={(event) =>
                  setForm((prev) => ({ ...prev, description: event.target.value }))
                }
                placeholder="Mô tả nguyên liệu, cách chế biến..."
              />
            </div>

            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-1.5">
                <Label htmlFor="itemPrice">Giá bán (VNĐ)</Label>
                <Input
                  id="itemPrice"
                  type="number"
                  min={0}
                  step={1000}
                  value={form.currentPrice}
                  onChange={(event) =>
                    setForm((prev) => ({ ...prev, currentPrice: event.target.value }))
                  }
                  required
                />
              </div>

              <div className="space-y-1.5">
                <FileUpload
                  value={form.imageUrl}
                  onChange={(url) => setForm((prev) => ({ ...prev, imageUrl: url ?? "" }))}
                  label="Hình ảnh món ăn"
                />
              </div>
            </div>

            <div className="flex items-center gap-2">
              <Checkbox
                id="itemAvailable"
                checked={form.isAvailable}
                onCheckedChange={(checked) =>
                  setForm((prev) => ({ ...prev, isAvailable: checked === true }))
                }
              />
              <Label htmlFor="itemAvailable" className="font-normal">
                Món đang còn (có thể đặt trực tiếp)
              </Label>
            </div>

            {formError && <p className="text-sm font-medium text-alert">{formError}</p>}

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setFormOpen(false)}>
                Hủy
              </Button>
              <Button type="submit" disabled={isMutating}>
                {isMutating && <Loader2 className="mr-1.5 h-4 w-4 animate-spin" />}
                {editing ? "Lưu thay đổi" : "Thêm món ăn"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* Xác nhận xóa món ăn */}
      <AlertDialog
        open={pendingDelete !== null}
        onOpenChange={(open) => {
          if (!open) setPendingDelete(null);
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Xóa món ăn?</AlertDialogTitle>
            <AlertDialogDescription>
              {pendingDelete ? `Món "${pendingDelete.itemName}" sẽ được xóa khỏi thực đơn.` : ""}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Giữ lại</AlertDialogCancel>
            <AlertDialogAction onClick={() => void handleDelete()}>Xóa</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/*                                Trạng thái UI                                */
/* -------------------------------------------------------------------------- */

function LoadingState({ label }: { label: string }) {
  return (
    <div className="surface-card flex items-center justify-center gap-2 p-10 text-sm text-muted-foreground">
      <Loader2 className="h-4 w-4 animate-spin" />
      {label}
    </div>
  );
}

function ErrorState({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div className="surface-card flex flex-col items-center gap-3 p-10 text-center">
      <p className="text-sm font-medium text-alert">{message}</p>
      <Button type="button" variant="outline" onClick={onRetry}>
        Thử lại
      </Button>
    </div>
  );
}

function EmptyState({ label }: { label: string }) {
  return (
    <div className="surface-card flex flex-col items-center gap-2 p-10 text-center">
      <UtensilsCrossed className="h-6 w-6 text-muted-foreground" />
      <p className="text-sm text-muted-foreground">{label}</p>
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/*                          Đồng bộ WebSocket (BR-55)                          */
/* -------------------------------------------------------------------------- */

/**
 * Lắng nghe `/topic/menu/refresh` để màn hình quản trị tự làm mới khi
 * có thay đổi danh mục / món ăn từ người quản lý khác.
 */
function useMenuRefreshSubscription() {
  const queryClient = useQueryClient();
  const clientRef = useRef<Client | null>(null);

  useEffect(() => {
    if (typeof window === "undefined") {
      return;
    }

    let isDisposed = false;

    const setupConnection = async () => {
      try {
        const token = getSession()?.token;
        const connectHeaders: Record<string, string> = token
          ? { Authorization: `Bearer ${token}` }
          : {};

        const wsProto = window.location.protocol === "https:" ? "wss:" : "ws:";
        const wsHost = API_BASE.replace(/^http(s)?:\/\//, "");
        const brokerURL = `${wsProto}//${wsHost}/ws`;

        let sockJsFactory: (() => WebSocket) | undefined = undefined;
        try {
          const SockJSModule = await import("sockjs-client");
          const SockJSClass = SockJSModule.default;
          sockJsFactory = () => new SockJSClass(`${API_BASE}/ws`) as unknown as WebSocket;
        } catch {
          // Nếu SockJS không tải được, client vẫn hoạt động qua native brokerURL
        }

        if (isDisposed) return;

        const client = new Client({
          brokerURL,
          ...(sockJsFactory ? { webSocketFactory: sockJsFactory } : {}),
          connectHeaders,
          reconnectDelay: 5000,
          heartbeatIncoming: 10000,
          heartbeatOutgoing: 10000,
          onConnect: () => {
            if (isDisposed) return;
            client.subscribe("/topic/menu/refresh", () => {
              void queryClient.invalidateQueries({ queryKey: ["menu"] });
              void queryClient.invalidateQueries({ queryKey: ["admin", "categories"] });
              void queryClient.invalidateQueries({ queryKey: ["admin", "menu-items"] });
            });
          },
        });

        clientRef.current = client;
        client.activate();
      } catch {
        // Không kết nối được thì màn hình vẫn dùng được bằng nút làm mới thủ công
      }
    };

    void setupConnection();

    return () => {
      isDisposed = true;
      const client = clientRef.current;
      clientRef.current = null;
      if (client) {
        try {
          void client.deactivate();
        } catch {
          // Bỏ qua lỗi ngắt kết nối khi unmount
        }
      }
    };
  }, [queryClient]);
}
