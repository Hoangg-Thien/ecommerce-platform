import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Package, ChevronRight, Clock, CheckCircle2, XCircle } from 'lucide-react';
import MainLayout from '../../components/layout/MainLayout';
import Button from '../../components/ui/Button';
import './OrderList.css';

// Dữ liệu giả lập cho lịch sử đơn hàng
const MOCK_ORDERS = [
  {
    id: 'KHO-10293',
    date: '12 Thg 08, 2026',
    status: 'DELIVERED', 
    total: 2150000,
    itemCount: 3,
    items: [
      { id: 1, name: 'Áo Khoác Nam Thể Thao Đa Năng', image: 'https://images.unsplash.com/photo-1556821840-3a63f95609a7?q=80&w=200&auto=format&fit=crop' },
      { id: 2, name: 'Giày Chạy Bộ Nam Siêu Nhẹ', image: 'https://images.unsplash.com/photo-1542291026-7eec264c27ff?q=80&w=200&auto=format&fit=crop' }
    ]
  },
  {
    id: 'KHO-10285',
    date: '05 Thg 08, 2026',
    status: 'PROCESSING',
    total: 850000,
    itemCount: 1,
    items: [
      { id: 3, name: 'Áo Thun Nam Cổ Tròn Basic', image: 'https://images.unsplash.com/photo-1521572163474-6864f9cf17ab?q=80&w=200&auto=format&fit=crop' }
    ]
  },
  {
    id: 'KHO-10211',
    date: '20 Thg 07, 2026',
    status: 'CANCELLED',
    total: 1250000,
    itemCount: 2,
    items: [
      { id: 4, name: 'Quần Jean Nam Ống Đứng', image: 'https://images.unsplash.com/photo-1542272604-787c3835535d?q=80&w=200&auto=format&fit=crop' }
    ]
  }
];

const formatPrice = (price) => {
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND'
  }).format(price).replace('₫', 'đ');
};

const getStatusConfig = (status) => {
  switch (status) {
    case 'DELIVERED':
      return { label: 'Đã giao hàng', className: 'status-delivered', icon: <CheckCircle2 size={16} /> };
    case 'PROCESSING':
      return { label: 'Đang xử lý', className: 'status-processing', icon: <Clock size={16} /> };
    case 'CANCELLED':
      return { label: 'Đã hủy', className: 'status-cancelled', icon: <XCircle size={16} /> };
    default:
      return { label: 'Không xác định', className: '', icon: <Package size={16} /> };
  }
};

export default function OrderList() {
  const [orders] = useState(MOCK_ORDERS);

  return (
    <MainLayout>
      <div className="order-list-page">
        <div className="order-list-container">
          <div className="order-list-header">
            <h1 className="order-list-title">Đơn hàng của tôi</h1>
            <p className="order-list-subtitle">Quản lý và theo dõi trạng thái các đơn hàng bạn đã đặt.</p>
          </div>

          <div className="orders-grid">
            {orders.map((order) => {
              const statusConfig = getStatusConfig(order.status);
              
              return (
                <div key={order.id} className="order-card">
                  {/* Header Card */}
                  <div className="order-card-header">
                    <div className="order-meta">
                      <span className="order-id">#{order.id}</span>
                      <span className="order-date">{order.date}</span>
                    </div>
                    <div className={`order-status-badge ${statusConfig.className}`}>
                      {statusConfig.icon}
                      <span>{statusConfig.label}</span>
                    </div>
                  </div>

                  {/* Body Card - Hình ảnh sản phẩm */}
                  <div className="order-card-body">
                    <div className="order-items-preview">
                      <div className="order-images-stack">
                        {order.items.slice(0, 3).map((item, index) => (
                          <div key={item.id} className="order-item-img-wrapper" style={{ zIndex: 3 - index }}>
                            <img src={item.image} alt={item.name} className="order-item-img" />
                          </div>
                        ))}
                        {order.itemCount > order.items.length && (
                          <div className="order-item-img-wrapper more-items" style={{ zIndex: 0 }}>
                            +{order.itemCount - order.items.length}
                          </div>
                        )}
                      </div>
                      <div className="order-items-text">
                        <span className="order-items-primary">
                          {order.items[0].name}
                        </span>
                        {order.itemCount > 1 && (
                          <span className="order-items-secondary">
                            và {order.itemCount - 1} sản phẩm khác
                          </span>
                        )}
                      </div>
                    </div>
                  </div>

                  {/* Footer Card */}
                  <div className="order-card-footer">
                    <div className="order-total-section">
                      <span className="order-total-label">Tổng tiền:</span>
                      <span className="order-total-value">{formatPrice(order.total)}</span>
                    </div>
                    <div className="order-actions">
                      <Button variant="outline" className="btn-view-details">
                        Xem chi tiết
                      </Button>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      </div>
    </MainLayout>
  );
}
