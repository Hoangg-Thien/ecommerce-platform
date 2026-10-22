import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { Plus, Pencil, Trash2, Search, Package, SlidersHorizontal } from 'lucide-react';
import AdminTable from '../../../components/admin/AdminTable';
import ConfirmModal from '../../../components/ui/ConfirmModal';
import Pagination from '../../../components/ui/Pagination';
import '../../../components/admin/AdminModal.css';
import productApi from '../../../api/productApi';
import categoryApi from '../../../api/categoryApi';
import { useToast } from '../../../context/ToastContext';
import './ProductManagement.css';

const formatPrice = (price) =>
  new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(price);

const buildColumns = (onEdit, onDelete) => [
  {
    key: 'image',
    label: '',
    skeletonWidth: '48px',
    render: (row) => (
      <div className="adm-prod-thumb">
        {row.imageUrl ? (
          <img src={row.imageUrl} alt={row.name} onError={(e) => { e.target.style.display = 'none'; e.target.nextSibling.style.display = 'flex'; }} />
        ) : null}
        <div className="adm-prod-thumb__fallback" style={{ display: row.imageUrl ? 'none' : 'flex' }}>
          <Package size={20} />
        </div>
      </div>
    ),
  },
  {
    key: 'name',
    label: 'Sản phẩm',
    skeletonWidth: '70%',
    render: (row) => (
      <div>
        <div className="adm-prod-name">{row.name}</div>
        <div className="adm-prod-category-label">{row.categoryName}</div>
      </div>
    ),
  },
  {
    key: 'price',
    label: 'Giá',
    skeletonWidth: '50%',
    render: (row) => (
      <span className="adm-prod-price">{formatPrice(row.price)}</span>
    ),
  },
  {
    key: 'variants',
    label: 'Biến thể',
    skeletonWidth: '40px',
    render: (row) => (
      <span className="adm-prod-variant-count">
        {row.variants?.length ?? 0} size
      </span>
    ),
  },
  {
    key: 'stock',
    label: 'Tồn kho',
    skeletonWidth: '40px',
    render: (row) => {
      const total = row.stock ?? 0;
      const low = total < 10;
      return (
        <span className={`adm-prod-stock ${low ? 'adm-prod-stock--low' : ''}`}>
          {total}
        </span>
      );
    },
  },
  {
    key: 'actions',
    label: 'Thao tác',
    className: 'col-actions',
    skeletonWidth: '60px',
    render: (row) => (
      <div className="admin-table__action-group">
        <button
          className="admin-table__action-btn"
          onClick={() => onEdit(row)}
          aria-label={`Sửa ${row.name}`}
          title="Chỉnh sửa"
        >
          <Pencil size={15} />
        </button>
        <button
          className="admin-table__action-btn danger"
          onClick={() => onDelete(row)}
          aria-label={`Xoá ${row.name}`}
          title="Xoá"
        >
          <Trash2 size={15} />
        </button>
      </div>
    ),
  },
];

export default function ProductManagement() {
  const navigate = useNavigate();
  const { showToast } = useToast();

  // ---- State ----
  const [products, setProducts] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [categories, setCategories] = useState([]);

  // Pagination
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const PAGE_SIZE = 10;

  // Filters
  const [search, setSearch] = useState('');
  const [searchInput, setSearchInput] = useState('');
  const [selectedCategory, setSelectedCategory] = useState('');

  // Delete
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [isDeleting, setIsDeleting] = useState(false);

  // Debounce search input (~400ms)
  useEffect(() => {
    const timer = setTimeout(() => {
      setSearch(searchInput);
      setPage(0);
    }, 400);
    return () => clearTimeout(timer);
  }, [searchInput]);

  // ---- Fetch categories (once) ----
  useEffect(() => {
    categoryApi.getAllCategories()
      .then((data) => setCategories(Array.isArray(data) ? data : []))
      .catch(() => { });
  }, []);

  // ---- Fetch products ----
  const fetchProducts = useCallback(async () => {
    setIsLoading(true);
    try {
      const params = {
        page,
        size: PAGE_SIZE,
        sortBy: 'id',
        sortDir: 'desc',
      };
      if (selectedCategory) params.categoryId = selectedCategory;
      if (search.trim().length >= 2) params.q = search.trim();

      const res = await productApi.getProducts(params);

      setProducts(res.content ?? []);
      setTotalPages(res.totalPages ?? 0);
      setTotalElements(res.totalElements ?? 0);
    } catch (err) {
      showToast('Không thể tải danh sách sản phẩm', 'error');
    } finally {
      setIsLoading(false);
    }
  }, [page, selectedCategory, search, showToast]);

  useEffect(() => {
    fetchProducts();
  }, [fetchProducts]);

  // ---- Search submit ----
  const handleSearch = (e) => {
    e.preventDefault();
    setSearch(searchInput);
    setPage(0);
  };

  // ---- Category filter ----
  const handleCategoryChange = (e) => {
    setSelectedCategory(e.target.value);
    setPage(0);
  };

  // ---- Delete ----
  const handleDeleteConfirm = async () => {
    if (!deleteTarget) return;
    setIsDeleting(true);
    try {
      await productApi.deleteProduct(deleteTarget.id);
      showToast(`Đã xoá sản phẩm "${deleteTarget.name}"`, 'success');
      setDeleteTarget(null);
      fetchProducts();
    } catch (err) {
      const msg = err?.response?.data?.message || 'Không thể xoá sản phẩm này';
      showToast(msg, 'error');
    } finally {
      setIsDeleting(false);
    }
  };

  const columns = buildColumns(
    (row) => navigate(`/admin/products/${row.id}`),
    (row) => setDeleteTarget(row)
  );

  return (
    <div className="admin-page">
      {/* Page Header */}
      <div className="admin-page__header">
        <div>
          <h1 className="admin-page__title">Sản phẩm</h1>
          <p className="admin-page__subtitle">{totalElements} sản phẩm</p>
        </div>
        <button
          id="btn-add-product"
          className="admin-btn admin-btn--primary"
          onClick={() => navigate('/admin/products/new')}
        >
          <Plus size={16} />
          Thêm sản phẩm
        </button>
      </div>

      {/* Filter Bar */}
      <div className="product-filter-bar">
        <form className="product-filter-bar__search" onSubmit={handleSearch}>
          <Search size={16} className="product-filter-bar__search-icon" />
          <input
            id="product-search-input"
            className="product-filter-bar__search-input"
            type="text"
            placeholder="Tìm theo tên sản phẩm..."
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
          />
          {searchInput && (
            <button type="button" className="product-filter-bar__search-clear" onClick={() => { setSearchInput(''); setSearch(''); setPage(0); }}>
              ✕
            </button>
          )}
        </form>

        <div className="product-filter-bar__filters">
          <SlidersHorizontal size={15} style={{ color: 'var(--admin-text-secondary)', flexShrink: 0 }} />
          <select
            id="product-category-filter"
            className="admin-select product-filter-bar__select"
            value={selectedCategory}
            onChange={handleCategoryChange}
          >
            <option value="">Tất cả danh mục</option>
            {categories.map((c) => (
              <option key={c.id} value={c.id}>{c.name}</option>
            ))}
          </select>
        </div>
      </div>

      {/* Table */}
      <AdminTable
        columns={columns}
        data={products}
        isLoading={isLoading}
        emptyMessage="Chưa có sản phẩm nào"
        emptyDescription='Nhấn "Thêm sản phẩm" để tạo sản phẩm đầu tiên.'
        skeletonRows={PAGE_SIZE}
      />

      {/* Pagination */}
      <Pagination
        currentPage={page}
        totalPages={totalPages}
        onPageChange={setPage}
      />

      {/* Delete Confirm */}
      <ConfirmModal
        isOpen={!!deleteTarget}
        title="Xoá sản phẩm"
        message={`Bạn có chắc muốn xoá sản phẩm "${deleteTarget?.name}"? Hành động này không thể hoàn tác.`}
        onConfirm={handleDeleteConfirm}
        onCancel={() => setDeleteTarget(null)}
        confirmText={isDeleting ? 'Đang xoá...' : 'Xoá'}
        isDanger
      />
    </div>
  );
}
