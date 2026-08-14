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

  // Redirect nếu chưa đăng nhập
  useEffect(() => {
    if (!user) {
      alert("Vui lòng đăng nhập để xem đơn hàng!");
      navigate('/login');
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
              <Button style={{ marginTop: '20px' }} onClick={() => navigate('/')}>Đi mua sắm ngay</Button>
            </div>
          ) : (
            <>
              <div className="orders-grid">
                {orders.map((order) => {
                  // Calculate total items (quantity)
                  const totalItems = order.orderDetails.reduce((sum, item) => sum + item.quantity, 0);
                  const firstItem = order.orderDetails[0];

                  return (
                    <div key={order.id} className="order-card">
                      {/* Header Card */}
                      <div className="order-card-header">
                        <div className="order-meta">
                          <span className="order-id">#{order.id}</span>
                          <span className="order-date">{new Date(order.createAt).toLocaleDateString('vi-VN')}</span>
                        </div>
                        <OrderStatusBadge status={order.orderStatus} />
                      </div>

                      {/* Body Card - Hình ảnh sản phẩm */}
                      <div className="order-card-body">
                        <div className="order-items-preview">
                          <div className="order-images-stack">
                            {order.orderDetails.slice(0, 3).map((item, index) => (
                              <div key={item.id} className="order-item-img-wrapper" style={{ zIndex: 3 - index }}>
                                <img src={item.product.imageUrl} alt={item.product.name} className="order-item-img" />
                              </div>
                            ))}
                            {totalItems > order.orderDetails.length && (
                              <div className="order-item-img-wrapper more-items" style={{ zIndex: 0 }}>
                                +{totalItems - order.orderDetails.length}
                              </div>
                            )}
                          </div>
                          <div className="order-items-text">
                            <span className="order-items-primary">
                              {firstItem?.product?.name}
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
                          <span className="order-total-value">{formatPrice(order.totalAmount)}</span>
                        </div>
                        <div className="order-actions">
                          <Button variant="outline" className="btn-view-details" onClick={() => navigate(`/payment-result?orderId=${order.id}`)}>
                            Xem trạng thái thanh toán
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
    </MainLayout>
  );
}
