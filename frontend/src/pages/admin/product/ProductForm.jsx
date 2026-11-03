import { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, Plus, Trash2, Package, Tag as TagIcon } from 'lucide-react';
import productApi from '../../../api/productApi';
import categoryApi from '../../../api/categoryApi';
import { useToast } from '../../../context/ToastContext';
import './ProductForm.css';

// Valid shoe sizes per backend pattern (39–46)
const VALID_SIZES = ['39', '40', '41', '42', '43', '44', '45', '46'];

const newVariantRow = () => ({ _id: crypto.randomUUID(), size: '', stock: '' });

const EMPTY_FORM = {
  name: '',
  price: '',
  imageUrl: '',
  description: '',
  categoryId: '',
};

export default function ProductForm() {
  const { id } = useParams(); // undefined = create mode
  const isEdit = Boolean(id);
  const navigate = useNavigate();
  const { showToast } = useToast();

  // ---- State ----
  const [activeTab, setActiveTab] = useState('info');
  const [form, setForm] = useState(EMPTY_FORM);
  const [formErrors, setFormErrors] = useState({});
  const [variants, setVariants] = useState([newVariantRow()]);
  const [variantErrors, setVariantErrors] = useState({});
  const [categories, setCategories] = useState([]);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isLoadingData, setIsLoadingData] = useState(isEdit);

  // ---- Load categories ----
  useEffect(() => {
    categoryApi.getAllCategories()
      .then((data) => setCategories(Array.isArray(data) ? data : []))
      .catch(() => { });
  }, []);

  // ---- Load product (edit mode) ----
  useEffect(() => {
    if (!isEdit) return;
    setIsLoadingData(true);
    productApi.getProductById(id)
      .then((product) => {
        setForm({
          name: product.name ?? '',
          price: product.price != null ? String(product.price) : '',
          imageUrl: product.imageUrl ?? '',
          description: product.description ?? '',
          categoryId: product.categoryId != null ? String(product.categoryId) : '',
        });
        if (product.variants && product.variants.length > 0) {
          setVariants(product.variants.map((v) => ({
            _id: crypto.randomUUID(),
            size: v.size,
            stock: String(v.stock),
          })));
        }
      })
      .catch(() => {
        showToast('Không tìm thấy sản phẩm', 'error');
        navigate('/admin/products');
      })
      .finally(() => setIsLoadingData(false));
  }, [id, isEdit, navigate, showToast]);

  // ---- Form field change ----
  const handleFieldChange = (field, value) => {
    setForm((f) => ({ ...f, [field]: value }));
    if (formErrors[field]) setFormErrors((fe) => ({ ...fe, [field]: '' }));
  };

  // ---- Variant management ----
  const addVariant = () => {
    setVariants((v) => [...v, newVariantRow()]);
  };

  const removeVariant = (rowId) => {
    setVariants((v) => v.filter((r) => r._id !== rowId));
    setVariantErrors((e) => { const copy = { ...e }; delete copy[rowId]; return copy; });
  };

  const updateVariant = (rowId, field, value) => {
    setVariants((v) =>
      v.map((r) => (r._id === rowId ? { ...r, [field]: value } : r))
    );
    if (variantErrors[rowId]?.[field]) {
      setVariantErrors((e) => ({
        ...e,
        [rowId]: { ...e[rowId], [field]: '' },
      }));
    }
  };

  // Which sizes are already taken (excluding current row)
  const takenSizes = (excludeRowId) =>
    variants.filter((r) => r._id !== excludeRowId && r.size).map((r) => r.size);

  // ---- Validation ----
  const validateForm = () => {
    const errors = {};
    if (!form.name.trim()) errors.name = 'Tên sản phẩm không được để trống';
    if (!form.price) errors.price = 'Giá không được để trống';
    else if (isNaN(Number(form.price)) || Number(form.price) < 0) errors.price = 'Giá phải là số không âm';
    if (!form.categoryId) errors.categoryId = 'Vui lòng chọn danh mục';
    return errors;
  };

  const validateVariants = () => {
    const errors = {};
    const usedSizes = {};

    variants.forEach((row) => {
      const rowErr = {};
      if (!row.size) rowErr.size = 'Chọn size';
      else if (usedSizes[row.size]) rowErr.size = 'Size bị trùng';
      else usedSizes[row.size] = true;

      if (row.stock === '' || row.stock === undefined) rowErr.stock = 'Nhập tồn kho';
      else if (isNaN(Number(row.stock)) || Number(row.stock) < 0) rowErr.stock = 'Tồn kho ≥ 0';

      if (Object.keys(rowErr).length > 0) errors[row._id] = rowErr;
    });
    return errors;
  };

  // ---- Submit ----
  const handleSubmit = async () => {
    const fErrors = validateForm();
    const vErrors = variants.length > 0 ? validateVariants() : {};

    if (Object.keys(fErrors).length > 0) {
      setFormErrors(fErrors);
      setActiveTab('info');
      return;
    }
    if (Object.keys(vErrors).length > 0) {
      setVariantErrors(vErrors);
      setActiveTab('variants');
      return;
    }

    setIsSubmitting(true);
    const payload = {
      name: form.name.trim(),
      price: Number(form.price),
      imageUrl: form.imageUrl.trim() || null,
      description: form.description.trim() || null,
      categoryId: Number(form.categoryId),
      variants: variants.map((r) => ({
        size: r.size,
        stock: Number(r.stock),
      })),
    };

    try {
      if (isEdit) {
        await productApi.updateProduct(id, payload);
        showToast('Đã cập nhật sản phẩm', 'success');
      } else {
        await productApi.createProduct(payload);
        showToast('Đã thêm sản phẩm mới', 'success');
      }
      navigate('/admin/products');
    } catch (err) {
      const msg = err?.response?.data?.message || 'Có lỗi xảy ra, thử lại sau';
      showToast(msg, 'error');
    } finally {
      setIsSubmitting(false);
    }
  };

  if (isLoadingData) {
    return (
      <div className="admin-page product-form-page">
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: 200, color: 'var(--admin-text-secondary)' }}>
          Đang tải dữ liệu...
        </div>
      </div>
    );
  }

  const variantErrorCount = Object.keys(variantErrors).length;

  return (
    <div className="admin-page product-form-page">
      {/* Page Header */}
      <div className="admin-page-header">
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <button
            className="admin-btn admin-btn--secondary admin-btn--sm"
            onClick={() => navigate('/admin/products')}
            aria-label="Quay lại"
          >
            <ArrowLeft size={14} />
          </button>
          <div>
            <h1 className="admin-page-title">
              {isEdit ? 'Chỉnh sửa sản phẩm' : 'Thêm sản phẩm mới'}
            </h1>
            <p className="admin-page-desc">
              {isEdit ? `ID: #${id}` : 'Điền thông tin và cấu hình các biến thể'}
            </p>
          </div>
        </div>
      </div>

      {/* Form Card */}
      <div className="admin-card product-form-card">
        {/* Tabs */}
        <div className="product-form-tabs" role="tablist">
          <button
            role="tab"
            aria-selected={activeTab === 'info'}
            className={`product-form-tab ${activeTab === 'info' ? 'active' : ''}`}
            onClick={() => setActiveTab('info')}
          >
            <Package size={14} />
            <span>Thông tin chung</span>
            {Object.keys(formErrors).length > 0 && (
              <span className="product-form-tab__badge product-form-tab__badge--error">
                {Object.keys(formErrors).length}
              </span>
            )}
          </button>
          <button
            role="tab"
            aria-selected={activeTab === 'variants'}
            className={`product-form-tab ${activeTab === 'variants' ? 'active' : ''}`}
            onClick={() => setActiveTab('variants')}
          >
            <TagIcon size={14} />
            <span>Biến thể (Size & Tồn kho)</span>
            <span className="product-form-tab__badge">
              {variants.length}
            </span>
            {variantErrorCount > 0 && (
              <span className="product-form-tab__badge product-form-tab__badge--error">
                {variantErrorCount}
              </span>
            )}
          </button>
        </div>

        {/* ======== TAB 1: Thông tin chung ======== */}
        {activeTab === 'info' && (
          <div className="product-form-body">
            <div className="admin-field">
              <label className="admin-label" htmlFor="pf-name">
                Tên sản phẩm <span className="required">*</span>
              </label>
              <input
                id="pf-name"
                className={`admin-input ${formErrors.name ? 'error' : ''}`}
                type="text"
                placeholder="Ví dụ: Nike Air Max 270"
                value={form.name}
                onChange={(e) => handleFieldChange('name', e.target.value)}
                autoFocus
              />
              {formErrors.name && <span className="admin-error-text">{formErrors.name}</span>}
            </div>

            <div className="product-form-grid-2">
              <div className="admin-field">
                <label className="admin-label" htmlFor="pf-price">
                  Giá bán (VND) <span className="required">*</span>
                </label>
                <input
                  id="pf-price"
                  className={`admin-input ${formErrors.price ? 'error' : ''}`}
                  type="number"
                  min="0"
                  step="1000"
                  placeholder="Ví dụ: 1500000"
                  value={form.price}
                  onChange={(e) => handleFieldChange('price', e.target.value)}
                />
                {formErrors.price && <span className="admin-error-text">{formErrors.price}</span>}
              </div>

              <div className="admin-field">
                <label className="admin-label" htmlFor="pf-category">
                  Danh mục <span className="required">*</span>
                </label>
                <select
                  id="pf-category"
                  className={`admin-select ${formErrors.categoryId ? 'error' : ''}`}
                  value={form.categoryId}
                  onChange={(e) => handleFieldChange('categoryId', e.target.value)}
                >
                  <option value="">-- Chọn danh mục --</option>
                  {categories.map((c) => (
                    <option key={c.id} value={c.id}>{c.name}</option>
                  ))}
                </select>
                {formErrors.categoryId && <span className="admin-error-text">{formErrors.categoryId}</span>}
              </div>
            </div>

            <div className="admin-field">
              <label className="admin-label" htmlFor="pf-imageUrl">URL Hình ảnh</label>
              <input
                id="pf-imageUrl"
                className="admin-input"
                type="url"
                placeholder="https://example.com/product-image.jpg"
                value={form.imageUrl}
                onChange={(e) => handleFieldChange('imageUrl', e.target.value)}
              />
              {form.imageUrl && (
                <div style={{ marginTop: 8 }}>
                  <img
                    src={form.imageUrl}
                    alt="Preview"
                    style={{ width: 64, height: 64, objectFit: 'cover', borderRadius: 'var(--admin-r-sm)', border: '1px solid var(--admin-border)' }}
                    onError={(e) => { e.target.style.display = 'none'; }}
                  />
                </div>
              )}
            </div>

            <div className="admin-field" style={{ marginBottom: 0 }}>
              <label className="admin-label" htmlFor="pf-description">Mô tả sản phẩm</label>
              <textarea
                id="pf-description"
                className="admin-textarea"
                placeholder="Mô tả chi tiết về sản phẩm..."
                value={form.description}
                onChange={(e) => handleFieldChange('description', e.target.value)}
                rows={4}
              />
            </div>
          </div>
        )}

        {/* ======== TAB 2: Biến thể ======== */}
        {activeTab === 'variants' && (
          <div className="product-form-body">
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 }}>
              <p style={{ margin: 0, fontSize: 12.5, color: 'var(--admin-text-secondary)' }}>
                Mỗi biến thể là một size cụ thể với số lượng tồn kho riêng ({VALID_SIZES.join(', ')}).
              </p>
              <button
                id="btn-add-variant"
                className="admin-btn admin-btn--secondary admin-btn--sm"
                onClick={addVariant}
                type="button"
              >
                <Plus size={13} />
                <span>Thêm size</span>
              </button>
            </div>

            {variants.length === 0 ? (
              <div className="variant-empty">
                <p>Chưa có biến thể nào.</p>
                <button className="admin-btn admin-btn--primary admin-btn--sm" onClick={addVariant} type="button">
                  <Plus size={13} /> Thêm biến thể đầu tiên
                </button>
              </div>
            ) : (
              <div className="variant-table-container">
                <table className="variant-table">
                  <thead>
                    <tr>
                      <th style={{ width: '35%' }}>Size</th>
                      <th style={{ width: '45%' }}>Tồn kho</th>
                      <th style={{ width: '20%' }}>Thao tác</th>
                    </tr>
                  </thead>
                  <tbody>
                    {variants.map((row) => {
                      const taken = takenSizes(row._id);
                      const rowErr = variantErrors[row._id] || {};
                      return (
                        <tr key={row._id}>
                          {/* Size */}
                          <td>
                            <select
                              className={`admin-select variant-select ${rowErr.size ? 'error' : ''}`}
                              value={row.size}
                              onChange={(e) => updateVariant(row._id, 'size', e.target.value)}
                              aria-label="Chọn size"
                            >
                              <option value="">-- Chọn size --</option>
                              {VALID_SIZES.map((s) => (
                                <option key={s} value={s} disabled={taken.includes(s)}>
                                  Size {s} {taken.includes(s) ? '(đã chọn)' : ''}
                                </option>
                              ))}
                            </select>
                            {rowErr.size && <div className="admin-error-text">{rowErr.size}</div>}
                          </td>
                          {/* Stock */}
                          <td>
                            <input
                              className={`admin-input variant-input ${rowErr.stock ? 'error' : ''}`}
                              type="number"
                              min="0"
                              placeholder="Số lượng"
                              value={row.stock}
                              onChange={(e) => updateVariant(row._id, 'stock', e.target.value)}
                              aria-label="Tồn kho"
                            />
                            {rowErr.stock && <div className="admin-error-text">{rowErr.stock}</div>}
                          </td>
                          {/* Remove */}
                          <td>
                            <div className="admin-table__action-group">
                              <button
                                className="admin-table__action-btn danger"
                                onClick={() => removeVariant(row._id)}
                                type="button"
                                aria-label="Xoá biến thể"
                                title="Xoá"
                              >
                                <Trash2 size={13} />
                              </button>
                            </div>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}

        {/* Form Footer */}
        <div className="product-form-footer">
          <button
            className="admin-btn admin-btn--secondary"
            onClick={() => navigate('/admin/products')}
            disabled={isSubmitting}
            type="button"
          >
            Huỷ
          </button>
          <button
            id="btn-submit-product"
            className="admin-btn admin-btn--primary"
            onClick={handleSubmit}
            disabled={isSubmitting}
            type="button"
          >
            {isSubmitting ? 'Đang lưu...' : isEdit ? 'Lưu thay đổi' : 'Thêm sản phẩm'}
          </button>
        </div>
      </div>
    </div>
  );
}
