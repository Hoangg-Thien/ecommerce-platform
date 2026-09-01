import { useState, useEffect, useCallback } from 'react';
import { Plus, Pencil, Trash2, Tag } from 'lucide-react';
import AdminTable from '../../../components/admin/AdminTable';
import AdminModal from '../../../components/admin/AdminModal';
import ConfirmModal from '../../../components/ui/ConfirmModal';
import '../../../components/admin/AdminModal.css';
import categoryApi from '../../../api/categoryApi';
import { useToast } from '../../../context/ToastContext';
import './CategoryManagement.css';

// ---- Initial form state ----
const EMPTY_FORM = { name: '', description: '' };

// ---- Table columns definition ----
const buildColumns = (onEdit, onDelete) => [
  {
    key: 'id',
    label: '#',
    skeletonWidth: '30px',
    render: (row) => <span style={{ color: 'var(--admin-text-muted)', fontWeight: 500 }}>#{row.id}</span>,
  },
  {
    key: 'name',
    label: 'Tên danh mục',
    skeletonWidth: '60%',
    render: (row) => (
      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
        <div className="category-icon-cell">
          <Tag size={14} />
        </div>
        <span style={{ fontWeight: 600, color: 'var(--admin-text-primary)' }}>{row.name}</span>
      </div>
    ),
  },
  {
    key: 'description',
    label: 'Mô tả',
    skeletonWidth: '80%',
    render: (row) => (
      <span style={{ color: 'var(--admin-text-secondary)' }}>{row.description || '—'}</span>
    ),
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

export default function CategoryManagement() {
  const { showToast } = useToast();

  // ---- State ----
  const [categories, setCategories] = useState([]);
  const [isLoading, setIsLoading] = useState(true);

  // Modal state
  const [modalOpen, setModalOpen] = useState(false);
  const [editTarget, setEditTarget] = useState(null); // null = create mode
  const [form, setForm] = useState(EMPTY_FORM);
  const [formErrors, setFormErrors] = useState({});
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Delete confirm state
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [isDeleting, setIsDeleting] = useState(false);

  // ---- Fetch ----
  const fetchCategories = useCallback(async () => {
    setIsLoading(true);
    try {
      const data = await categoryApi.getAllCategories();
      setCategories(Array.isArray(data) ? data : []);
    } catch (err) {
      showToast('Không thể tải danh mục', 'error');
    } finally {
      setIsLoading(false);
    }
  }, [showToast]);

  useEffect(() => {
    fetchCategories();
  }, [fetchCategories]);

  // ---- Modal helpers ----
  const openCreate = () => {
    setEditTarget(null);
    setForm(EMPTY_FORM);
    setFormErrors({});
    setModalOpen(true);
  };

  const openEdit = (category) => {
    setEditTarget(category);
    setForm({ name: category.name, description: category.description || '' });
    setFormErrors({});
    setModalOpen(true);
  };

  const closeModal = () => {
    if (isSubmitting) return;
    setModalOpen(false);
  };

  // ---- Validation ----
  const validate = () => {
    const errors = {};
    if (!form.name.trim()) errors.name = 'Tên danh mục không được để trống';
    else if (form.name.trim().length < 2) errors.name = 'Tên phải có ít nhất 2 ký tự';
    return errors;
  };

  // ---- Submit ----
  const handleSubmit = async () => {
    const errors = validate();
    if (Object.keys(errors).length > 0) {
      setFormErrors(errors);
      return;
    }

    setIsSubmitting(true);
    const payload = { name: form.name.trim(), description: form.description.trim() };

    try {
      if (editTarget) {
        await categoryApi.updateCategory(editTarget.id, payload);
        showToast('Đã cập nhật danh mục', 'success');
      } else {
        await categoryApi.createCategory(payload);
        showToast('Đã thêm danh mục mới', 'success');
      }
      setModalOpen(false);
      fetchCategories();
    } catch (err) {
      const msg = err?.response?.data?.message || 'Có lỗi xảy ra, thử lại sau';
      showToast(msg, 'error');
    } finally {
      setIsSubmitting(false);
    }
  };

  // ---- Delete ----
  const handleDeleteConfirm = async () => {
    if (!deleteTarget) return;
    setIsDeleting(true);
    try {
      await categoryApi.deleteCategory(deleteTarget.id);
      showToast(`Đã xoá danh mục "${deleteTarget.name}"`, 'success');
      setDeleteTarget(null);
      fetchCategories();
    } catch (err) {
      const msg = err?.response?.data?.message || 'Không thể xoá danh mục này';
      showToast(msg, 'error');
    } finally {
      setIsDeleting(false);
    }
  };

  // ---- Columns ----
  const columns = buildColumns(openEdit, (row) => setDeleteTarget(row));

  return (
    <div className="admin-page">
      {/* Page Header */}
      <div className="admin-page__header">
        <div>
          <h1 className="admin-page__title">Danh mục</h1>
          <p className="admin-page__subtitle">{categories.length} danh mục</p>
        </div>
        <button
          id="btn-add-category"
          className="admin-btn admin-btn--primary"
          onClick={openCreate}
        >
          <Plus size={16} />
          Thêm danh mục
        </button>
      </div>

      {/* Table */}
      <AdminTable
        columns={columns}
        data={categories}
        isLoading={isLoading}
        emptyMessage="Chưa có danh mục nào"
        emptyDescription='Nhấn "Thêm danh mục" để tạo mới.'
        skeletonRows={4}
      />

      {/* Create / Edit Modal */}
      <AdminModal
        isOpen={modalOpen}
        onClose={closeModal}
        title={editTarget ? 'Chỉnh sửa danh mục' : 'Thêm danh mục mới'}
        size="sm"
        footer={
          <>
            <button className="admin-btn admin-btn--secondary" onClick={closeModal} disabled={isSubmitting}>
              Huỷ
            </button>
            <button
              id="btn-submit-category"
              className="admin-btn admin-btn--primary"
              onClick={handleSubmit}
              disabled={isSubmitting}
            >
              {isSubmitting ? 'Đang lưu...' : editTarget ? 'Lưu thay đổi' : 'Thêm mới'}
            </button>
          </>
        }
      >
        {/* Form */}
        <div className="admin-field">
          <label className="admin-label" htmlFor="cat-name">
            Tên danh mục <span className="required">*</span>
          </label>
          <input
            id="cat-name"
            className={`admin-input ${formErrors.name ? 'error' : ''}`}
            type="text"
            placeholder="Ví dụ: Giày thể thao"
            value={form.name}
            onChange={(e) => {
              setForm((f) => ({ ...f, name: e.target.value }));
              if (formErrors.name) setFormErrors((fe) => ({ ...fe, name: '' }));
            }}
            autoFocus
          />
          {formErrors.name && <span className="admin-error-text">{formErrors.name}</span>}
        </div>

        <div className="admin-field" style={{ marginBottom: 0 }}>
          <label className="admin-label" htmlFor="cat-desc">Mô tả</label>
          <textarea
            id="cat-desc"
            className="admin-textarea"
            placeholder="Mô tả ngắn về danh mục (tuỳ chọn)"
            value={form.description}
            onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
            rows={3}
          />
        </div>
      </AdminModal>

      {/* Delete Confirm */}
      <ConfirmModal
        isOpen={!!deleteTarget}
        title="Xoá danh mục"
        message={`Bạn có chắc muốn xoá danh mục "${deleteTarget?.name}"? Hành động này không thể hoàn tác.`}
        onConfirm={handleDeleteConfirm}
        onCancel={() => setDeleteTarget(null)}
        confirmText={isDeleting ? 'Đang xoá...' : 'Xoá'}
        isDanger
      />
    </div>
  );
}
