import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import MainLayout from '../../components/layout/MainLayout';
import Button from '../../components/ui/Button';
import Pagination from '../../components/ui/Pagination';
import OrderStatusBadge from '../../components/order/OrderStatusBadge';
import orderApi from '../../api/orderApi';
import { useAuth } from '../../context/AuthContext';
import './OrderList.css';

const formatPrice = (price) => {
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND'
  }).format(price).replace('₫', 'đ');
};

export default function OrderList() {
  const { user } = useAuth();
  const navigate = useNavigate();
  
  const [orders, setOrders] = useState([]);
  const [currentPage, setCurrentPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [isLoading, setIsLoading] = useState(true);
  const [selectedOrder, setSelectedOrder] = useState(null);

  // Redirect nếu chưa đăng nhập
  useEffect(() => {
    if (!user) {
      navigate('/');
    }
  }, [user, navigate]);

  useEffect(() => {
    if (!user) return; // Không fetch nếu chưa login

    const fetchOrders = async () => {
      setIsLoading(true);
      try {
        const response = await orderApi.getUserOrders(currentPage, 5); // Hiển thị 5 đơn 1 trang
        setOrders(response.content);
        setTotalPages(response.totalPages);
      } catch (error) {
        console.error('Lỗi khi tải đơn hàng', error);
      } finally {
        setIsLoading(false);
      }
    };

    fetchOrders();
  }, [currentPage, user]);

  if (!user) return null;

  return (
    <MainLayout>
      <div className="order-list-page">
        <div className="order-list-container">
          <div className="order-list-header">
            <h1 className="order-list-title">Đơn hàng của tôi</h1>
            <p className="order-list-subtitle">Quản lý và theo dõi trạng thái các đơn hàng bạn đã đặt.</p>
          </div>

          {isLoading ? (
            <div style={{ textAlign: 'center', padding: '50px' }}>Đang tải danh sách đơn hàng...</div>
          ) : orders.length === 0 ? (
            <div style={{ textAlign: 'center', padding: '100px 0', background: '#f9f9f9', borderRadius: '8px' }}>
              <h2>Bạn chưa có đơn hàng nào!</h2>
              <Button style={{ marginTop: '20px',  }} onClick={() => navigate('/')}>Đi mua sắm ngay</Button>
            </div>
          ) : (
            <>
              <div className="orders-grid">
                {orders.map((order) => {
                  // Calculate total items (quantity)
                  const totalItems = order.items ? order.items.reduce((sum, item) => sum + item.quantity, 0) : 0;
                  const firstItem = order.items && order.items.length > 0 ? order.items[0] : null;

                  return (
                    <div key={order.id} className="order-card">
                      {/* Header Card */}
                      <div className="order-card-header">
                        <div className="order-meta">
                          <span className="order-id">#{order.id}</span>
                          <span className="order-date">{new Date(order.createdAt).toLocaleDateString('vi-VN')}</span>
                        </div>
                        <OrderStatusBadge status={order.status} />
                      </div>

                      {/* Body Card - Hình ảnh sản phẩm */}
                      <div className="order-card-body">
                        <div className="order-items-preview">
                          <div className="order-images-stack">
                            {order.items && order.items.slice(0, 3).map((item, index) => (
                              <div key={item.id} className="order-item-img-wrapper" style={{ zIndex: 3 - index }}>
                                <div style={{width: '60px', height: '60px', background: '#f0f0f0', display: 'flex', alignItems: 'center', justifyContent: 'center', borderRadius: '4px', fontSize: '24px'}}>📦</div>
                              </div>
                            ))}
                            {order.items && totalItems > order.items.length && (
                              <div className="order-item-img-wrapper more-items" style={{ zIndex: 0 }}>
                                +{totalItems - order.items.length}
                              </div>
                            )}
                          </div>
                          <div className="order-items-text">
                            <span className="order-items-primary">
                              {firstItem?.productName || "Sản phẩm"}
                            </span>
                            {totalItems > 1 && (
                              <span className="order-items-secondary">
                                và {totalItems - 1} sản phẩm khác
                              </span>
                            )}
                          </div>
                        </div>
                      </div>

                      {/* Footer Card */}
                      <div className="order-card-footer">
                        <div className="order-total-section">
                          <span className="order-total-label">Tổng tiền:</span>
                          <span className="order-total-value">{formatPrice(order.totalPrice)}</span>
                        </div>
                        <div className="order-actions">
                          <Button variant="outline" className="btn-view-details" onClick={() => setSelectedOrder(order)}>
                            Xem chi tiết
                          </Button>
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>

              {/* Hiện nút phân trang */}
              <Pagination 
                currentPage={currentPage}
                totalPages={totalPages}
                onPageChange={setCurrentPage}
              />
            </>
          )}
        </div>
      </div>

      {/* Chi tiết đơn hàng Modal */}
      {selectedOrder && (
        <div className="order-modal-overlay" onClick={() => setSelectedOrder(null)}>
          <div className="order-modal-content" onClick={e => e.stopPropagation()}>
            <div className="order-modal-header">
              <h2>Chi tiết đơn hàng #{selectedOrder.id}</h2>
              <button className="close-modal-btn" onClick={() => setSelectedOrder(null)}>✕</button>
            </div>
            <div className="order-modal-body">
              <div className="order-modal-info">
                <p><strong>Ngày đặt:</strong> {new Date(selectedOrder.createdAt).toLocaleDateString('vi-VN')} {new Date(selectedOrder.createdAt).toLocaleTimeString('vi-VN')}</p>
                <p><strong>Trạng thái:</strong> <OrderStatusBadge status={selectedOrder.status} /></p>
                <p><strong>Thanh toán:</strong> {selectedOrder.paymentMethod === 'COD' ? 'Thanh toán khi nhận hàng (COD)' : selectedOrder.paymentMethod}</p>
              </div>
              
              <h3 style={{marginTop: '20px', marginBottom: '10px', fontSize: '16px'}}>Danh sách sản phẩm</h3>
              <div className="order-modal-items">
                {selectedOrder.items && selectedOrder.items.map(item => (
                  <div key={item.id} className="order-modal-item">
                    <div className="order-modal-item-icon">📦</div>
                    <div className="order-modal-item-details">
                      <div className="order-modal-item-name">{item.productName}</div>
                      <div className="order-modal-item-price-qty">
                        <div>Đơn giá: {formatPrice(item.price)}</div>
                        <div>Size: {item.size}</div>
                        <div>Số lượng: {item.quantity}</div>
                      </div>
                    </div>
                  </div>
                ))}
              </div>

              <div className="order-modal-summary">
                <div className="order-modal-summary-row total">
                  <span>Tổng cộng:</span>
                  <span>{formatPrice(selectedOrder.totalPrice)}</span>
                </div>
              </div>
            </div>
            <div className="order-modal-footer">
              <Button onClick={() => setSelectedOrder(null)}>Đóng</Button>
            </div>
          </div>
        </div>
      )}
    </MainLayout>
  );
}
