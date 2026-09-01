import './StatusBadge.css';

const STATUS_MAP = {
  // Order statuses
  AWAITING_PAYMENT: { label: 'Chờ thanh toán', color: 'orange' },
  PENDING:   { label: 'Chờ xử lý',  color: 'gray' },
  CONFIRMED: { label: 'Đã xác nhận', color: 'blue' },
  SHIPPING:  { label: 'Đang giao',   color: 'orange' },
  DONE:      { label: 'Hoàn thành',  color: 'green' },
  CANCELLED: { label: 'Đã huỷ',     color: 'red' },
  // Payment
  PAID:      { label: 'Đã thanh toán', color: 'green' },
  SUCCESS:   { label: 'Thành công',  color: 'green' },
  FAILED:    { label: 'Thất bại',    color: 'red' },
  // Generic
  ACTIVE:    { label: 'Hoạt động',   color: 'green' },
  INACTIVE:  { label: 'Ngừng hoạt động', color: 'gray' },
  IN_STOCK:  { label: 'Còn hàng',    color: 'green' },
  OUT_OF_STOCK: { label: 'Hết hàng', color: 'red' },
};

export default function StatusBadge({ status }) {
  const config = STATUS_MAP[status] || { label: status, color: 'gray' };
  return (
    <span className={`status-badge status-badge--${config.color}`}>
      {config.label}
    </span>
  );
}
