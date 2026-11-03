import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight } from 'lucide-react';
import StatusBadge from '../../../components/admin/StatusBadge';
import dashboardApi from '../../../api/dashboardApi';
import { useToast } from '../../../context/ToastContext';
import './Dashboard.css';

// ---- Helpers ----
const formatCurrency = (n) =>
  new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(n ?? 0);

// ---- Skeleton helper ----
function SkeletonBox({ w = '100%', h = 20, r = 4 }) {
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
    <div className="dash-container">
      {/* ---- Page Header ---- */}
      <div className="admin-page-header">
        <div>
          <h1 className="admin-page-title">Dashboard</h1>
          <p className="admin-page-desc">Tổng quan số liệu và vận hành hệ thống</p>
        </div>
      </div>

      {/* ---- Row 1: KPI Stat Cards ---- */}
      <div className="dash-stats-grid">
        <StatCard
          label="Tổng doanh thu"
          value={loading ? null : formatCurrency(stats?.totalRevenue)}
          sub={loading ? null : `Hôm nay: ${formatCurrency(stats?.todayRevenue)}`}
        />
        <StatCard
          label="Tổng đơn hàng"
          value={loading ? null : (stats?.totalOrders ?? 0).toLocaleString('vi-VN')}
          sub={loading ? null : `Hôm nay: ${stats?.todayOrders ?? 0} đơn`}
        />
        <StatCard
          label="Khách hàng"
          value={loading ? null : (stats?.totalUsers ?? 0).toLocaleString('vi-VN')}
        />
        <StatCard
          label="Sản phẩm"
          value={loading ? null : (stats?.totalProducts ?? 0).toLocaleString('vi-VN')}
        />
      </div>

      {/* ---- Row 2: Order status + Inventory ---- */}
      <div className="dash-grid-2col">
        {/* Order Status breakdown */}
        <div className="admin-card dash-card">
          <div className="dash-card__header">
            <h2 className="dash-card__title">Trạng thái đơn hàng</h2>
            <Link to="/admin/orders" className="dash-card__link">
              <span>Xem tất cả</span>
              <ArrowRight size={13} />
            </Link>
          </div>
          {loading ? (
            <div style={{ padding: 16 }}>
              <SkeletonBox h={70} />
            </div>
          ) : (
            <div className="dash-status-grid">
              <StatusTile count={stats?.awaitingPaymentOrders} label="Chờ thanh toán" />
              <StatusTile count={stats?.pendingOrders} label="Chờ xử lý" />
              <StatusTile count={stats?.shippingOrders} label="Đang giao" />
              <StatusTile count={stats?.completedOrders} label="Hoàn thành" />
              <StatusTile count={stats?.cancelledOrders} label="Đã huỷ" />
              <StatusTile count={stats?.totalOrders} label="Tổng đơn" />
            </div>
          )}
        </div>

        {/* Inventory Alerts */}
        <div className="admin-card dash-card">
          <div className="dash-card__header">
            <h2 className="dash-card__title">Cảnh báo tồn kho</h2>
            <Link to="/admin/products" className="dash-card__link">
              <span>Quản lý kho</span>
              <ArrowRight size={13} />
            </Link>
          </div>
          {loading ? (
            <div style={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 10 }}>
              <SkeletonBox h={48} />
              <SkeletonBox h={48} />
            </div>
          ) : (
            <div className="dash-inventory-list">
              <div className="dash-inventory-row">
                <div className="dash-inventory-row__info">
                  <div className="dash-inventory-row__label">Sắp hết hàng</div>
                  <div className="dash-inventory-row__desc">Còn từ 1 đến 5 đôi trong kho</div>
                </div>
                <div className="dash-inventory-row__badge dash-inventory-row__badge--warn">
                  {stats?.lowStockVariants ?? 0}
                </div>
              </div>
              <div className="dash-inventory-row">
                <div className="dash-inventory-row__info">
                  <div className="dash-inventory-row__label">Hết hàng</div>
                  <div className="dash-inventory-row__desc">0 đôi trong kho</div>
                </div>
                <div className="dash-inventory-row__badge dash-inventory-row__badge--danger">
                  {stats?.outOfStockVariants ?? 0}
                </div>
              </div>
              {(stats?.lowStockVariants ?? 0) === 0 && (stats?.outOfStockVariants ?? 0) === 0 && (
                <div className="dash-empty">Kho hàng đang ở trạng thái ổn định</div>
              )}
            </div>
          )}
        </div>
      </div>

      {/* ---- Row 3: Top Products + Recent Orders ---- */}
      <div className="dash-grid-2col">
        {/* Top Products */}
        <div className="admin-card dash-card">
          <div className="dash-card__header">
            <h2 className="dash-card__title">Sản phẩm bán chạy</h2>
            <Link to="/admin/products" className="dash-card__link">
              <span>Xem tất cả</span>
              <ArrowRight size={13} />
            </Link>
          </div>
          {loading ? (
            <div style={{ padding: 12, display: 'flex', flexDirection: 'column', gap: 8 }}>
              {[...Array(5)].map((_, i) => <SkeletonBox key={i} h={40} />)}
            </div>
          ) : (stats?.topProducts ?? []).length === 0 ? (
            <div className="dash-empty">Chưa có dữ liệu bán hàng</div>
          ) : (
            <div className="dash-list">
              {(stats.topProducts).map((p, i) => (
                <div key={p.productId} className="dash-top-product">
                  <span className="dash-top-product__rank">{i + 1}</span>
                  <div className="dash-top-product__img">
                    <img
                      src={p.imageUrl}
                      alt={p.productName}
                      onError={(e) => { e.target.style.display = 'none'; }}
                    />
                  </div>
                  <span className="dash-top-product__name">{p.productName}</span>
                  <span className="dash-top-product__sold">{p.unitsSold} đã bán</span>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Recent Orders */}
        <div className="admin-card dash-card">
          <div className="dash-card__header">
            <h2 className="dash-card__title">Đơn hàng gần đây</h2>
            <Link to="/admin/orders" className="dash-card__link">
              <span>Xem tất cả</span>
              <ArrowRight size={13} />
            </Link>
          </div>
          {loading ? (
            <div style={{ padding: 12, display: 'flex', flexDirection: 'column', gap: 8 }}>
              {[...Array(5)].map((_, i) => <SkeletonBox key={i} h={38} />)}
            </div>
          ) : (stats?.recentOrders ?? []).length === 0 ? (
            <div className="dash-empty">Chưa có đơn hàng nào</div>
          ) : (
            <div className="dash-list">
              {(stats.recentOrders).map((o) => (
                <div key={o.orderId} className="dash-recent-order">
                  <span className="dash-recent-order__id">#{o.orderId}</span>
                  <span className="dash-recent-order__email">{o.userEmail}</span>
                  <div className="dash-recent-order__status">
                    <StatusBadge status={o.status} />
                  </div>
                  <span className="dash-recent-order__total">{formatCurrency(o.totalPrice)}</span>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

// ---- Sub-components ----
function StatCard({ label, value, sub }) {
  return (
    <div className="admin-card dash-stat-card">
      <div className="dash-stat-card__label">{label}</div>
      <div className="dash-stat-card__value">
        {value === null || value === undefined ? (
          <div className="dash-skeleton" style={{ height: 24, width: '60%', borderRadius: 4 }} />
        ) : (
          value
        )}
      </div>
      {sub && <div className="dash-stat-card__sub">{sub}</div>}
    </div>
  );
}

function StatusTile({ count, label }) {
  return (
    <div className="dash-status-tile">
      <div className="dash-status-tile__count">{count ?? 0}</div>
      <div className="dash-status-tile__label">{label}</div>
    </div>
  );
}
