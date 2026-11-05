import OrderPricing from '../../../components/promotion/OrderPricing';
import SalePrice from '../../../components/promotion/SalePrice';
import { useState, useEffect, useCallback } from 'react';
import { Eye, Search, X, Lock } from 'lucide-react';
import AdminTable from '../../../components/admin/AdminTable';
import AdminModal from '../../../components/admin/AdminModal';
import StatusBadge from '../../../components/admin/StatusBadge';
import Pagination from '../../../components/ui/Pagination';
import adminOrderApi from '../../../api/adminOrderApi';
import { useToast } from '../../../context/ToastContext';
import './OrderManagement.css';
import { paymentMethodLabel, paymentSummary, restrictVnpayTransitions } from '../../../utils/payment';

// ---- Helpers ----
const formatPrice = (n) =>
  new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(n ?? 0);

const formatDate = (dt) => {
  if (!dt) return '—';
  const d = new Date(dt);
  return d.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' }) +
    ' ' + d.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
};

const ORDER_STATUSES = [
  { value: '', label: 'Tất cả trạng thái' },
  { value: 'AWAITING_PAYMENT', label: 'Chờ thanh toán' },
  { value: 'PENDING', label: 'Chờ xử lý' },
  { value: 'CONFIRMED', label: 'Đã xác nhận' },
  { value: 'SHIPPING', label: 'Đang giao' },
  { value: 'DONE', label: 'Hoàn thành' },
  { value: 'CANCELLED', label: 'Đã huỷ' },
];

// Trạng thái nào là terminal (không cho đổi nữa)
const TERMINAL_STATUSES = ['DONE', 'CANCELLED'];

// Đổi tiếp từng trạng thái hợp lệ
const NEXT_STATUS_OPTIONS = {
  AWAITING_PAYMENT: ['CONFIRMED', 'CANCELLED'],
  PENDING:          ['CONFIRMED', 'CANCELLED'],
  CONFIRMED:        ['SHIPPING', 'CANCELLED'],
  SHIPPING:         ['DONE', 'CANCELLED'],
};

// ---- Table columns ----
const COLUMNS = [
  {
    key: 'id',
    label: 'Mã đơn',
    className: 'col-number',
    skeletonWidth: '70px',
    render: (row) => (
      <div>
        <div className="adm-order-id">#{row.id}</div>
        <div className="adm-order-date">{formatDate(row.createdAt)}</div>
      </div>
    ),
  },
  {
    key: 'userEmail',
    label: 'Khách hàng',
    skeletonWidth: '60%',
    render: (row) => (
      <span className="adm-order-customer-email">{row.userEmail}</span>
    ),
  },
  {
    key: 'totalPrice',
    label: 'Tổng tiền',
    className: 'col-currency',
    skeletonWidth: '90px',
    render: (row) => (
      <span className="adm-order-total">{formatPrice(row.totalPrice)}</span>
    ),
  },
  {
    key: 'paymentMethod',
    label: 'Thanh toán',
    skeletonWidth: '70px',
    render: (row) => (
      <div>
      <span className={`adm-order-payment adm-order-payment--${row.paymentMethod?.toLowerCase()}`}>
        {paymentMethodLabel(row.paymentMethod)}
      </span>
      {row.refundStatus && row.refundStatus !== 'NONE' && <div className="adm-order-review">{paymentSummary(row)}</div>}
      </div>
    ),
  },
  {
    key: 'status',
    label: 'Trạng thái',
    skeletonWidth: '90px',
    render: (row) => <StatusBadge status={row.status} />,
  },
];

// ---- Component ----
export default function OrderManagement() {
  const { showToast } = useToast();

  // Table state
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);
  const [pagination, setPagination] = useState({ page: 0, size: 10, totalPages: 1, totalElements: 0 });

  // Filter state
  const [filterStatus, setFilterStatus] = useState('');
  const [filterEmail, setFilterEmail] = useState('');
  const [filterPaymentMethod, setFilterPaymentMethod] = useState('');
  const [filterPaymentStatus, setFilterPaymentStatus] = useState('');
  const [filterRefundStatus, setFilterRefundStatus] = useState('');
  const [emailInput, setEmailInput] = useState('');

  // Detail modal
  const [detailModal, setDetailModal] = useState({ open: false, order: null });
  const [detailLoading, setDetailLoading] = useState(false);

  // Status update
  const [updatingId, setUpdatingId] = useState(null);
  const [pendingStatus, setPendingStatus] = useState('');

  // ---- Fetch orders ----
  const fetchOrders = useCallback(async (page = 0) => {
    setLoading(true);
    try {
      const data = await adminOrderApi.getAllOrders({
        page,
        size: pagination.size,
        status: filterStatus || undefined,
        email: filterEmail || undefined,
        paymentMethod: filterPaymentMethod || undefined,
        paymentStatus: filterPaymentStatus || undefined,
        refundStatus: filterRefundStatus || undefined,
      });
      setOrders(data?.content ?? []);
      setPagination((prev) => ({
        ...prev,
        page: data?.pageNo ?? data?.page ?? page,
        totalPages: data?.totalPages ?? 1,
        totalElements: data?.totalElements ?? 0,
      }));
    } catch (err) {
      console.error('Error fetching admin orders:', err);
      showToast('Không thể tải danh sách đơn hàng', 'error');
    } finally {
      setLoading(false);
    }
  }, [filterStatus, filterEmail, filterPaymentMethod, filterPaymentStatus, filterRefundStatus, pagination.size, showToast]);

  useEffect(() => {
    fetchOrders(0);
  }, [filterStatus, filterEmail, filterPaymentMethod, filterPaymentStatus, filterRefundStatus]);

  // ---- Open detail modal ----
  const openDetail = async (row) => {
    setDetailModal({ open: true, order: null });
    setDetailLoading(true);
    setPendingStatus('');
    try {
      const data = await adminOrderApi.getOrderById(row.id);
      setDetailModal({ open: true, order: data });
      setPendingStatus(data.status);
    } catch {
      showToast('Không thể tải chi tiết đơn hàng', 'error');
      setDetailModal({ open: false, order: null });
    } finally {
      setDetailLoading(false);
    }
  };

  // ---- Update status from modal ----
  const handleUpdateStatus = async () => {
    const order = detailModal.order;
    if (!order || pendingStatus === order.status) return;
    setUpdatingId(order.id);
    try {
      const updated = await adminOrderApi.updateOrderStatus(order.id, pendingStatus);
      showToast('Cập nhật trạng thái thành công', 'success');
      setDetailModal({ open: true, order: updated });
      setPendingStatus(updated.status);
      // Cập nhật lại row trong bảng
      setOrders((prev) =>
        prev.map((o) => (o.id === updated.id ? { ...o, ...updated } : o))
      );
    } catch (err) {
      const msg = err?.response?.data?.message || 'Có lỗi xảy ra khi cập nhật';
      showToast(msg, 'error');
    } finally {
      setUpdatingId(null);
    }
  };

  const actionColumn = {
    key: 'actions',
    label: '',
    className: 'col-actions',
    skeletonWidth: '32px',
    render: (row) => (
      <div className="admin-table__action-group">
        <button
          className="admin-table__action-btn"
          onClick={() => openDetail(row)}
          title="Xem chi tiết"
          aria-label="Xem chi tiết"
        >
          <Eye size={14} />
        </button>
      </div>
    ),
  };

  const isTerminal = (status) => TERMINAL_STATUSES.includes(status);

  return (
    <div className="admin-page">
      {/* Header */}
      <div className="admin-page-header">
        <div>
          <h1 className="admin-page-title">Quản lý đơn hàng</h1>
          <p className="admin-page-desc">
            {pagination.totalElements > 0
              ? `${pagination.totalElements.toLocaleString('vi-VN')} đơn hàng trong hệ thống`
              : 'Chưa có đơn hàng nào'}
          </p>
        </div>
      </div>

      {/* Filter Bar */}
      <div className="adm-order-filter-bar">
        {/* Search by email */}
        <div className="adm-order-filter-bar__search">
          <Search size={14} className="adm-order-filter-bar__search-icon" />
          <input
            className="adm-order-filter-bar__search-input"
            type="text"
            placeholder="Tìm theo email khách hàng..."
            value={emailInput}
            onChange={(e) => setEmailInput(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') setFilterEmail(emailInput.trim());
            }}
          />
          {emailInput && (
            <button
              className="adm-order-filter-bar__search-clear"
              onClick={() => { setEmailInput(''); setFilterEmail(''); }}
              aria-label="Xoá tìm kiếm"
            >
              <X size={13} />
            </button>
          )}
        </div>

        {/* Filter by status */}
        <div className="adm-order-filter-bar__filters">
          <select className="admin-select adm-order-filter-bar__select" aria-label="Lọc phương thức thanh toán"
            value={filterPaymentMethod} onChange={e => setFilterPaymentMethod(e.target.value)}>
            <option value="">Tất cả phương thức</option>
            {['COD', 'MOMO', 'VNPAY'].map(m => <option key={m} value={m}>{paymentMethodLabel(m)}</option>)}
          </select>
          <select className="admin-select adm-order-filter-bar__select" aria-label="Lọc trạng thái thanh toán"
            value={filterPaymentStatus} onChange={e => setFilterPaymentStatus(e.target.value)}>
            <option value="">Tất cả thanh toán</option>
            <option value="PENDING">Chưa có kết quả cuối</option><option value="PAID">Đã thanh toán</option><option value="FAILED">Thất bại đã xác minh</option>
          </select>
          <select className="admin-select adm-order-filter-bar__select" aria-label="Lọc đối soát"
            value={filterRefundStatus} onChange={e => setFilterRefundStatus(e.target.value)}>
            <option value="">Tất cả đối soát</option><option value="RECONCILIATION_REQUIRED">Cần đối soát</option>
            <option value="REQUIRED_MANUAL">Cần xử lý thủ công</option><option value="NONE">Không có yêu cầu</option>
          </select>
          <select
            className="admin-select adm-order-filter-bar__select"
            value={filterStatus}
            onChange={(e) => setFilterStatus(e.target.value)}
            aria-label="Lọc theo trạng thái"
          >
            {ORDER_STATUSES.map((s) => (
              <option key={s.value} value={s.value}>{s.label}</option>
            ))}
          </select>
        </div>

        {/* Apply email search */}
        <button
          className="admin-btn admin-btn--secondary"
          onClick={() => setFilterEmail(emailInput.trim())}
        >
          <Search size={13} />
          <span>Tìm kiếm</span>
        </button>
      </div>

      {/* Table */}
      <AdminTable
        columns={[...COLUMNS, actionColumn]}
        data={orders}
        isLoading={loading}
        emptyMessage="Không có đơn hàng nào"
        emptyDescription="Thử thay đổi bộ lọc hoặc từ khoá tìm kiếm."
      />

      {/* Pagination */}
      {pagination.totalPages > 1 && (
        <Pagination
          currentPage={pagination.page}
          totalPages={pagination.totalPages}
          onPageChange={(p) => fetchOrders(p)}
        />
      )}

      {/* ======== Detail Modal ======== */}
      <AdminModal
        isOpen={detailModal.open}
        title={detailModal.order ? `Chi tiết đơn hàng #${detailModal.order.id}` : 'Đang tải...'}
        onClose={() => setDetailModal({ open: false, order: null })}
        size="lg"
        footer={
          detailModal.order && !isTerminal(detailModal.order.status) ? (
            <>
              <button
                className="admin-btn admin-btn--secondary"
                onClick={() => setDetailModal({ open: false, order: null })}
              >
                Đóng
              </button>
              <button
                id="btn-update-order-status"
                className="admin-btn admin-btn--primary"
                onClick={handleUpdateStatus}
                disabled={
                  updatingId === detailModal.order?.id ||
                  pendingStatus === detailModal.order?.status
                  || !restrictVnpayTransitions(detailModal.order, NEXT_STATUS_OPTIONS[detailModal.order.status] ?? []).includes(pendingStatus)
                }
              >
                {updatingId === detailModal.order?.id ? 'Đang lưu...' : 'Lưu trạng thái'}
              </button>
            </>
          ) : (
            <button
              className="admin-btn admin-btn--secondary"
              onClick={() => setDetailModal({ open: false, order: null })}
            >
              Đóng
            </button>
          )
        }
      >
        {detailLoading || !detailModal.order ? (
          <div style={{ textAlign: 'left', padding: '24px 0', color: 'var(--admin-text-secondary)' }}>
            Đang tải chi tiết đơn hàng...
          </div>
        ) : (
          <OrderDetailContent
            order={detailModal.order}
            pendingStatus={pendingStatus}
            setPendingStatus={setPendingStatus}
            isUpdating={updatingId === detailModal.order.id}
          />
        )}
      </AdminModal>
    </div>
  );
}

// ---- Sub-component: nội dung modal chi tiết ----
function OrderDetailContent({ order, pendingStatus, setPendingStatus, isUpdating }) {
  const isTerminal = TERMINAL_STATUSES.includes(order.status);
  const nextOptions = restrictVnpayTransitions(order, NEXT_STATUS_OPTIONS[order.status] ?? []);

  return (
    <div className="order-detail-modal">
      {/* Thông tin cơ bản */}
      <div>
        <div className="order-detail-modal__section-title">Thông tin đơn hàng</div>
        <div className="order-detail-modal__meta-grid">
          <div className="order-detail-modal__meta-item">
            <span className="order-detail-modal__meta-label">Mã đơn</span>
            <span className="order-detail-modal__meta-value">#{order.id}</span>
          </div>
          <div className="order-detail-modal__meta-item">
            <span className="order-detail-modal__meta-label">Ngày tạo</span>
            <span className="order-detail-modal__meta-value">{formatDate(order.createdAt)}</span>
          </div>
          <div className="order-detail-modal__meta-item">
            <span className="order-detail-modal__meta-label">Khách hàng</span>
            <span className="order-detail-modal__meta-value">{order.userEmail}</span>
          </div>
          <div className="order-detail-modal__meta-item">
            <span className="order-detail-modal__meta-label">Thanh toán</span>
            <span className="order-detail-modal__meta-value">
              <span className={`adm-order-payment adm-order-payment--${order.paymentMethod?.toLowerCase()}`}>
                {paymentMethodLabel(order.paymentMethod)}
              </span>
              {order.paymentStatus && (
                <span style={{ marginLeft: 6 }}>
                  <StatusBadge status={order.paymentStatus} />
                </span>
              )}
            </span>
          </div>
          <div className="order-detail-modal__meta-item">
            <span className="order-detail-modal__meta-label">Trạng thái</span>
            <span className="order-detail-modal__meta-value">
              <StatusBadge status={order.status} />
            </span>
          </div>
        </div>
        {order.paymentMethod === 'VNPAY' && <div className="adm-order-review" role="status">
          <p>{paymentSummary(order)}. Chưa có xác nhận hoàn tiền.</p>
          {order.reason && <p>Lý do: {order.reason}</p>}
          {order.expiresAt && <p>Hạn thanh toán: {formatDate(order.expiresAt)}</p>}
          {order.providerTxnRef && <p>Mã tham chiếu: {order.providerTxnRef}</p>}
          {order.providerTransactionNo && <p>Mã giao dịch VNPay: {order.providerTransactionNo}</p>}
          {(order.responseCode || order.transactionStatus) && <p>Kết quả gateway: {order.responseCode ?? '—'} / {order.transactionStatus ?? '—'}</p>}
        </div>}
      </div>

      {/* Danh sách sản phẩm */}
      <div>
        <div className="order-detail-modal__section-title">
          Sản phẩm ({order.items?.length ?? 0} món)
        </div>
        <div className="order-detail-modal__items">
          {(order.items ?? []).map((item) => (
            <div key={item.id} className="order-detail-modal__item">
              <div className="order-detail-modal__item-img">
                <img
                  src={item.imageUrl}
                  alt={item.productName}
                  onError={(e) => { e.target.style.display = 'none'; }}
                />
              </div>
              <div className="order-detail-modal__item-info">
                <div className="order-detail-modal__item-name">{item.productName}</div>
                <div className="order-detail-modal__item-meta">
                  Size {item.size} · SL: {item.quantity} · <SalePrice price={item.originalPrice ?? item.price} salePrice={item.price} discountPercent={item.productDiscountPercent} />/đôi
                </div>
              </div>
              <div className="order-detail-modal__item-subtotal">
                {formatPrice(item.subTotal)}
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Tổng tiền */}
      <OrderPricing order={order} />
      <div className="order-detail-modal__total-row">
        <span className="order-detail-modal__total-label">Tổng thanh toán</span>
        <span className="order-detail-modal__total-value">{formatPrice(order.totalPrice)}</span>
      </div>

      {/* Cập nhật trạng thái */}
      <div className="order-detail-modal__status-section">
        <div className="order-detail-modal__section-title" style={{ marginBottom: 8 }}>
          Cập nhật trạng thái
        </div>
        {isTerminal ? (
          <div className="order-detail-modal__locked-msg">
            <Lock size={13} />
            <span>Đơn hàng đã ở trạng thái kết thúc, không thể thay đổi thêm.</span>
          </div>
        ) : (
          <div className="order-detail-modal__status-row">
            <select
              className="admin-select order-detail-modal__status-select"
              value={pendingStatus}
              onChange={(e) => setPendingStatus(e.target.value)}
              disabled={isUpdating}
              aria-label="Chọn trạng thái mới"
            >
              <option value={order.status}>
                {ORDER_STATUSES.find((s) => s.value === order.status)?.label ?? order.status} (hiện tại)
              </option>
              {nextOptions.map((s) => (
                <option key={s} value={s}>
                  {ORDER_STATUSES.find((os) => os.value === s)?.label ?? s}
                </option>
              ))}
            </select>
          </div>
        )}
      </div>
    </div>
  );
}
