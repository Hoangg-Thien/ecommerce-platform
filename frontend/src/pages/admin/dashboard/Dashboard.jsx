import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import {
  TrendingUp, ShoppingBag, Users, Package,
  AlertTriangle, XCircle, Clock, Truck,
  CheckCircle, CreditCard, ArrowRight,
} from 'lucide-react';
import StatusBadge from '../../../components/admin/StatusBadge';
import dashboardApi from '../../../api/dashboardApi';
import { useToast } from '../../../context/ToastContext';
import './Dashboard.css';

// ---- Helpers ----
const formatCurrency = (n) =>
  new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(n ?? 0);

const formatDate = (dt) => {
  if (!dt) return '—';
  const d = new Date(dt);
  return d.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit' }) +
    ' ' + d.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
};

// ---- Skeleton helpers ----
function SkeletonBox({ w = '100%', h = 20, r = 6 }) {
  return (
    <div className="dash-skeleton" style={{ width: w, height: h, borderRadius: r }} />
  );
}

// ---- Main Component ----
export default function Dashboard() {
  const { showToast } = useToast();
  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const load = async () => {
      setLoading(true);
      try {
        const res = await dashboardApi.getStats();
        setStats(res.data ?? res);
      } catch (err) {
        console.error(err);
        showToast('Không thể tải dữ liệu dashboard', 'error');
      } finally {
        setLoading(false);
      }
    };
    load();
  }, []);

  return (
    <div className="admin-page" style={{ gap: 24 }}>
      {/* ---- Page Header ---- */}
      <div className="admin-page__header">
        <div>
          <h1 className="admin-page__title">Dashboard</h1>
          <p className="admin-page__subtitle">Tổng quan hoạt động cửa hàng</p>
        </div>
      </div>

      {/* ---- Row 1: Big KPI cards ---- */}
      <div className="dash-stats-grid">
        <StatCard
          variant="revenue"
          icon={<TrendingUp size={22} />}
          label="Tổng doanh thu"
          value={loading ? null : formatCurrency(stats?.totalRevenue)}
          sub={loading ? null : `Hôm nay: ${formatCurrency(stats?.todayRevenue)}`}
          subType="up"
        />
        <StatCard
          variant="orders"
          icon={<ShoppingBag size={22} />}
          label="Tổng đơn hàng"
          value={loading ? null : stats?.totalOrders}
          sub={loading ? null : `Hôm nay: ${stats?.todayOrders ?? 0} đơn`}
          subType="up"
        />
        <StatCard
          variant="users"
          icon={<Users size={22} />}
          label="Khách hàng"
          value={loading ? null : stats?.totalUsers}
        />
        <StatCard
          variant="products"
          icon={<Package size={22} />}
          label="Sản phẩm"
          value={loading ? null : stats?.totalProducts}
        />
      </div>

      {/* ---- Row 2: Order status + Inventory ---- */}
      <div className="dash-row">
        {/* Order Status breakdown */}
        <div className="dash-section">
          <div className="dash-section__header">
            <h2 className="dash-section__title">Trạng thái đơn hàng</h2>
            <Link to="/admin/orders" className="dash-section__link">
              Xem tất cả <ArrowRight size={12} style={{ display: 'inline' }} />
            </Link>
          </div>
          {loading ? (
            <div style={{ padding: 20 }}>
              <SkeletonBox h={80} />
            </div>
          ) : (
            <div className="dash-status-grid">
              <StatusItem cls="await" icon={<CreditCard size={14}/>} count={stats?.awaitingPaymentOrders} label="Chờ TT" />
              <StatusItem cls="pending" icon={<Clock size={14}/>} count={stats?.pendingOrders} label="Chờ xử lý" />
              <StatusItem cls="shipping" icon={<Truck size={14}/>} count={stats?.shippingOrders} label="Đang giao" />
              <StatusItem cls="done" icon={<CheckCircle size={14}/>} count={stats?.completedOrders} label="Hoàn thành" />
              <StatusItem cls="cancelled" icon={<XCircle size={14}/>} count={stats?.cancelledOrders} label="Đã huỷ" />
              <StatusItem cls="total" icon={<ShoppingBag size={14}/>} count={stats?.totalOrders} label="Tổng" />
            </div>
          )}
        </div>

        {/* Inventory Alerts */}
        <div className="dash-section">
          <div className="dash-section__header">
            <h2 className="dash-section__title">Cảnh báo tồn kho</h2>
            <Link to="/admin/products" className="dash-section__link">
              Quản lý <ArrowRight size={12} style={{ display: 'inline' }} />
            </Link>
          </div>
          {loading ? (
            <div style={{ padding: 20, display: 'flex', flexDirection: 'column', gap: 12 }}>
              <SkeletonBox h={60} />
              <SkeletonBox h={60} />
            </div>
          ) : (
            <>
              <div className="dash-inventory-alert">
                <div className="dash-inventory-alert__info">
                  <div className="dash-inventory-alert__icon dash-inventory-alert__icon--warn">
                    <AlertTriangle size={18} />
                  </div>
                  <div>
                    <div className="dash-inventory-alert__label">Sắp hết hàng</div>
                    <div className="dash-inventory-alert__desc">Còn ≤5 đôi trong kho</div>
                  </div>
                </div>
                <div className="dash-inventory-alert__count dash-inventory-alert__count--warn">
                  {stats?.lowStockVariants ?? 0}
                </div>
              </div>
              <div className="dash-inventory-alert" style={{ borderTop: '1px solid var(--admin-border)' }}>
                <div className="dash-inventory-alert__info">
                  <div className="dash-inventory-alert__icon dash-inventory-alert__icon--danger">
                    <XCircle size={18} />
                  </div>
                  <div>
                    <div className="dash-inventory-alert__label">Hết hàng</div>
                    <div className="dash-inventory-alert__desc">0 đôi trong kho</div>
                  </div>
                </div>
                <div className="dash-inventory-alert__count dash-inventory-alert__count--danger">
                  {stats?.outOfStockVariants ?? 0}
                </div>
              </div>
              {(stats?.lowStockVariants ?? 0) === 0 && (stats?.outOfStockVariants ?? 0) === 0 && (
                <div className="dash-empty">✅ Kho hàng đang ở trạng thái tốt!</div>
              )}
            </>
          )}
        </div>
      </div>

      {/* ---- Row 3: Top Products + Recent Orders ---- */}
      <div className="dash-row">
        {/* Top Products */}
        <div className="dash-section">
          <div className="dash-section__header">
            <h2 className="dash-section__title">Sản phẩm bán chạy</h2>
            <Link to="/admin/products" className="dash-section__link">
              Xem tất cả <ArrowRight size={12} style={{ display: 'inline' }} />
            </Link>
          </div>
          {loading ? (
            <div style={{ padding: '12px 20px', display: 'flex', flexDirection: 'column', gap: 12 }}>
              {[...Array(5)].map((_, i) => <SkeletonBox key={i} h={44} />)}
            </div>
          ) : (stats?.topProducts ?? []).length === 0 ? (
            <div className="dash-empty">Chưa có đơn hàng hoàn thành nào</div>
          ) : (
            (stats.topProducts).map((p, i) => (
              <div key={p.productId} className="dash-top-product">
                <div className={`dash-top-product__rank dash-top-product__rank--${i + 1}`}>
                  {i + 1}
                </div>
                <div className="dash-top-product__img">
                  <img src={p.imageUrl} alt={p.productName}
                    onError={(e) => { e.target.style.display = 'none'; }} />
                </div>
                <span className="dash-top-product__name">{p.productName}</span>
                <span className="dash-top-product__sold">{p.unitsSold} đôi</span>
              </div>
            ))
          )}
        </div>

        {/* Recent Orders */}
        <div className="dash-section">
          <div className="dash-section__header">
            <h2 className="dash-section__title">Đơn hàng gần đây</h2>
            <Link to="/admin/orders" className="dash-section__link">
              Xem tất cả <ArrowRight size={12} style={{ display: 'inline' }} />
            </Link>
          </div>
          {loading ? (
            <div style={{ padding: '12px 20px', display: 'flex', flexDirection: 'column', gap: 12 }}>
              {[...Array(6)].map((_, i) => <SkeletonBox key={i} h={40} />)}
            </div>
          ) : (stats?.recentOrders ?? []).length === 0 ? (
            <div className="dash-empty">Chưa có đơn hàng nào</div>
          ) : (
            (stats.recentOrders).map((o) => (
              <div key={o.orderId} className="dash-recent-order">
                <span className="dash-recent-order__id">#{o.orderId}</span>
                <span className="dash-recent-order__email">{o.userEmail}</span>
                <StatusBadge status={o.status} />
                <span className="dash-recent-order__total">{formatCurrency(o.totalPrice)}</span>
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
}

// ---- Sub-components ----
function StatCard({ variant, icon, label, value, sub, subType }) {
  return (
    <div className={`dash-stat-card dash-stat-card--${variant}`}>
      <div className="dash-stat-card__icon">{icon}</div>
      <div className="dash-stat-card__body">
        <div className="dash-stat-card__label">{label}</div>
        <div className="dash-stat-card__value">
          {value === null || value === undefined
            ? <div className="dash-skeleton" style={{ height: 28, width: '70%', borderRadius: 6 }} />
            : value}
        </div>
        {sub !== null && sub !== undefined && (
          <div className={`dash-stat-card__sub ${subType ? `dash-stat-card__sub--${subType}` : ''}`}>
            {sub}
          </div>
        )}
      </div>
    </div>
  );
}

function StatusItem({ cls, icon, count, label }) {
  return (
    <div className={`dash-status-item dash-status-item--${cls}`}>
      <div className="dash-status-item__count">{count ?? 0}</div>
      <div className="dash-status-item__label">{label}</div>
    </div>
  );
}
