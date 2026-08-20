import { CheckCircle2, Clock, XCircle, Truck, Package, CreditCard } from 'lucide-react';

export default function OrderStatusBadge({ status }) {
  let config = {};

  switch (status) {
    case 'AWAITING_PAYMENT':
      config = { label: 'Chờ thanh toán', color: '#5becf6ff', bgColor: '#fdf3e8', icon: <CreditCard size={16} /> };
      break;
    case 'PENDING':
      config = { label: 'Chờ xử lý', color: '#3498db', bgColor: '#fdf1e8', icon: <Clock size={16} /> };
      break;
    case 'CONFIRMED':
      config = { label: 'Đã xác nhận', color: '#e67e22', bgColor: '#eaf4fc', icon: <Package size={16} /> };
      break;
    case 'SHIPPING':
      config = { label: 'Đang giao hàng', color: '#b0b659ff', bgColor: '#f5eef8', icon: <Truck size={16} /> };
      break;
    case 'DONE':
      config = { label: 'Đã hoàn thành', color: '#2ecc71', bgColor: '#eafaf1', icon: <CheckCircle2 size={16} /> };
      break;
    case 'CANCELLED':
      config = { label: 'Đã hủy', color: '#e74c3c', bgColor: '#fdedec', icon: <XCircle size={16} /> };
      break;
    default:
      config = { label: 'Không xác định', color: '#95a5a6', bgColor: '#f4f6f6', icon: <Package size={16} /> };
  }

  return (
    <span style={{
      display: 'inline-flex',
      alignItems: 'center',
      gap: '6px',
      padding: '6px 12px',
      borderRadius: '20px',
      backgroundColor: config.bgColor,
      color: config.color,
      fontWeight: 'bold',
      fontSize: '14px'
    }}>
      {config.icon}
      <span>{config.label}</span>
    </span>
  );
}
