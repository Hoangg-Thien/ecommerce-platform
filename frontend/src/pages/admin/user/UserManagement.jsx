import { useState, useEffect, useCallback } from 'react';
import { Search, X, Shield, User as UserIcon, Users, AlertTriangle } from 'lucide-react';
import AdminTable from '../../../components/admin/AdminTable';
import AdminModal from '../../../components/admin/AdminModal';
import adminUserApi from '../../../api/adminUserApi';
import { useAuth } from '../../../context/AuthContext';
import { useToast } from '../../../context/ToastContext';
import '../../../components/admin/AdminModal.css';
import '../category/CategoryManagement.css';
import './UserManagement.css';

// ---- Helpers ----
const getInitials = (email = '') => email.charAt(0).toUpperCase();

const ROLE_OPTIONS = [
  { value: '', label: 'Tất cả vai trò' },
  { value: 'USER', label: 'Khách hàng' },
  { value: 'ADMIN', label: 'Quản trị viên' },
];

// ---- Table columns ----
const buildColumns = (onRoleClick, currentUserEmail) => [
  {
    key: 'email',
    label: 'Người dùng',
    skeletonWidth: '65%',
    render: (row) => (
      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        <div className="adm-user-avatar">{getInitials(row.email)}</div>
        <div>
          <div className="adm-user-email">{row.email}</div>
          <div className="adm-user-id">ID #{row.id}</div>
        </div>
      </div>
    ),
  },
  {
    key: 'role',
    label: 'Vai trò',
    skeletonWidth: '80px',
    render: (row) => (
      <span className={`adm-user-role-badge adm-user-role-badge--${row.role?.toLowerCase()}`}>
        {row.role === 'ADMIN' ? <Shield size={11} /> : <UserIcon size={11} />}
        {row.role === 'ADMIN' ? 'Admin' : 'Khách hàng'}
      </span>
    ),
  },
  {
    key: 'totalOrders',
    label: 'Đơn hàng',
    skeletonWidth: '60px',
    render: (row) => (
      <div className="adm-user-orders">
        <div className="adm-user-orders__total">{row.totalOrders ?? 0} đơn</div>
        {row.completedOrders > 0 && (
          <div className="adm-user-orders__done">✓ {row.completedOrders} hoàn thành</div>
        )}
      </div>
    ),
  },
  {
    key: 'actions',
    label: '',
    skeletonWidth: '100px',
    render: (row) => {
      const isSelf = row.email === currentUserEmail;
      return (
        <div className="admin-table__action-group">
          <button
            className="admin-btn admin-btn--secondary admin-btn--sm"
            onClick={() => onRoleClick(row)}
            disabled={isSelf}
            title={isSelf ? 'Không thể đổi role của chính mình' : 'Đổi vai trò'}
          >
            <Shield size={13} />
            Đổi role
          </button>
        </div>
      );
    },
  },
];

// ---- Component ----
export default function UserManagement() {
  const { user: currentUser } = useAuth();
  const { showToast } = useToast();

  // Table state
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [pagination, setPagination] = useState({ page: 0, size: 10, totalPages: 1, totalElements: 0 });

  // Stats
  const [stats, setStats] = useState({ total: 0, admins: 0, regular: 0 });

  // Filters
  const [filterRole, setFilterRole] = useState('');
  const [filterEmail, setFilterEmail] = useState('');
  const [emailInput, setEmailInput] = useState('');

  // Role change modal
  const [roleModal, setRoleModal] = useState({ open: false, user: null });
  const [pendingRole, setPendingRole] = useState('');
  const [updating, setUpdating] = useState(false);

  // ---- Fetch ----
  const fetchUsers = useCallback(async (page = 0) => {
    setLoading(true);
    try {
      const res = await adminUserApi.getAllUsers({
        page,
        size: pagination.size,
        role: filterRole || undefined,
        email: filterEmail || undefined,
      });
      const data = res.data ?? res;
      setUsers(data.content ?? []);
      const total = data.totalElements ?? 0;
      setPagination((prev) => ({
        ...prev,
        page: data.pageNo ?? page,
        totalPages: data.totalPages ?? 1,
        totalElements: total,
      }));
      // Ước tính stats từ dữ liệu hiện có (toàn bộ nếu không filter)
      if (!filterRole && !filterEmail) {
        const adminCount = (data.content ?? []).filter((u) => u.role === 'ADMIN').length;
        setStats({
          total,
          admins: adminCount,
          regular: total - adminCount,
        });
      }
    } catch (err) {
      console.error('Error fetching users:', err);
      showToast('Không thể tải danh sách người dùng', 'error');
    } finally {
      setLoading(false);
    }
  }, [filterRole, filterEmail, pagination.size, showToast]);

  useEffect(() => {
    fetchUsers(0);
  }, [filterRole, filterEmail]);

  // ---- Open role modal ----
  const openRoleModal = (user) => {
    setRoleModal({ open: true, user });
    setPendingRole(user.role);
  };

  // ---- Confirm role change ----
  const handleUpdateRole = async () => {
    if (!roleModal.user || pendingRole === roleModal.user.role) return;
    setUpdating(true);
    try {
      const res = await adminUserApi.updateRole(roleModal.user.id, pendingRole);
      const updated = res.data ?? res;
      showToast(`Đã đổi role của ${updated.email} thành ${pendingRole}`, 'success');
      setRoleModal({ open: false, user: null });
      // Cập nhật lại row trong bảng
      setUsers((prev) =>
        prev.map((u) => (u.id === updated.id ? { ...u, role: updated.role } : u))
      );
    } catch (err) {
      const msg = err?.response?.data?.message || 'Có lỗi khi cập nhật role';
      showToast(msg, 'error');
    } finally {
      setUpdating(false);
    }
  };

  const columns = buildColumns(openRoleModal, currentUser?.email);

  return (
    <div className="admin-page">
      {/* Header */}
      <div className="admin-page__header">
        <div>
          <h1 className="admin-page__title">Quản lý người dùng</h1>
          <p className="admin-page__subtitle">
            {pagination.totalElements > 0
              ? `${pagination.totalElements} tài khoản`
              : 'Chưa có tài khoản nào'}
          </p>
        </div>
      </div>

      {/* Stats bar */}
      <div className="adm-user-stats">
        <div className="adm-user-stat-card">
          <div className="adm-user-stat-card__icon adm-user-stat-card__icon--total">
            <Users size={16} />
          </div>
          <div>
            <div className="adm-user-stat-card__label">Tổng tài khoản</div>
            <div className="adm-user-stat-card__value">{pagination.totalElements}</div>
          </div>
        </div>
        <div className="adm-user-stat-card">
          <div className="adm-user-stat-card__icon adm-user-stat-card__icon--admin">
            <Shield size={16} />
          </div>
          <div>
            <div className="adm-user-stat-card__label">Quản trị viên</div>
            <div className="adm-user-stat-card__value">{stats.admins}</div>
          </div>
        </div>
        <div className="adm-user-stat-card">
          <div className="adm-user-stat-card__icon adm-user-stat-card__icon--user">
            <UserIcon size={16} />
          </div>
          <div>
            <div className="adm-user-stat-card__label">Khách hàng</div>
            <div className="adm-user-stat-card__value">{pagination.totalElements - stats.admins}</div>
          </div>
        </div>
      </div>

      {/* Filter Bar */}
      <div className="product-filter-bar">
        <div className="product-filter-bar__search">
          <Search size={15} className="product-filter-bar__search-icon" />
          <input
            className="product-filter-bar__search-input"
            type="text"
            placeholder="Tìm theo email..."
            value={emailInput}
            onChange={(e) => setEmailInput(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') setFilterEmail(emailInput.trim());
            }}
          />
          {emailInput && (
            <button
              className="product-filter-bar__search-clear"
              onClick={() => { setEmailInput(''); setFilterEmail(''); }}
              aria-label="Xoá tìm kiếm"
            >
              <X size={13} />
            </button>
          )}
        </div>

        <div className="product-filter-bar__filters">
          <select
            className="admin-select product-filter-bar__select"
            value={filterRole}
            onChange={(e) => setFilterRole(e.target.value)}
            aria-label="Lọc theo vai trò"
          >
            {ROLE_OPTIONS.map((r) => (
              <option key={r.value} value={r.value}>{r.label}</option>
            ))}
          </select>
        </div>

        <button
          className="admin-btn admin-btn--secondary admin-btn--sm"
          onClick={() => setFilterEmail(emailInput.trim())}
        >
          <Search size={14} />
          Tìm
        </button>
      </div>

      {/* Table */}
      <AdminTable
        columns={columns}
        data={users}
        isLoading={loading}
        emptyMessage="Không có người dùng nào"
        emptyDescription="Thử thay đổi bộ lọc để tìm người dùng."
      />

      {/* Pagination */}
      {pagination.totalPages > 1 && (
        <div className="admin-pagination">
          {Array.from({ length: pagination.totalPages }, (_, i) => (
            <button
              key={i}
              className={`admin-pagination__btn ${i === pagination.page ? 'admin-pagination__btn--active' : ''}`}
              onClick={() => fetchUsers(i)}
            >
              {i + 1}
            </button>
          ))}
        </div>
      )}

      {/* ======== Role Change Modal ======== */}
      <AdminModal
        isOpen={roleModal.open}
        title="Thay đổi vai trò người dùng"
        onClose={() => setRoleModal({ open: false, user: null })}
        size="sm"
        footer={
          <>
            <button
              className="admin-btn admin-btn--secondary"
              onClick={() => setRoleModal({ open: false, user: null })}
            >
              Huỷ
            </button>
            <button
              id="btn-confirm-role-change"
              className="admin-btn admin-btn--primary"
              onClick={handleUpdateRole}
              disabled={updating || pendingRole === roleModal.user?.role}
            >
              {updating ? 'Đang lưu...' : 'Xác nhận'}
            </button>
          </>
        }
      >
        {roleModal.user && (
          <div>
            {/* User info */}
            <div className="user-role-modal__info">
              <div className="user-role-modal__avatar">
                {getInitials(roleModal.user.email)}
              </div>
              <div>
                <div className="user-role-modal__email">{roleModal.user.email}</div>
                <div className="user-role-modal__current-role">
                  Vai trò hiện tại:{' '}
                  <strong>{roleModal.user.role === 'ADMIN' ? 'Quản trị viên' : 'Khách hàng'}</strong>
                </div>
              </div>
            </div>

            {/* Role select */}
            <label className="user-role-modal__select-label">Vai trò mới</label>
            <select
              className="user-role-modal__select"
              value={pendingRole}
              onChange={(e) => setPendingRole(e.target.value)}
              disabled={updating}
            >
              <option value="USER">Khách hàng (USER)</option>
              <option value="ADMIN">Quản trị viên (ADMIN)</option>
            </select>

            {/* Warning khi nâng thành ADMIN */}
            {pendingRole === 'ADMIN' && roleModal.user.role !== 'ADMIN' && (
              <div className="user-role-modal__warning">
                <AlertTriangle size={15} style={{ flexShrink: 0, marginTop: 1 }} />
                <span>
                  Người dùng này sẽ có toàn quyền truy cập vào trang quản trị.
                  Hãy chắc chắn bạn tin tưởng tài khoản này.
                </span>
              </div>
            )}
          </div>
        )}
      </AdminModal>
    </div>
  );
}
