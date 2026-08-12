import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Check, X, Clock, RotateCcw } from 'lucide-react';
import MainLayout from '../components/layout/MainLayout';
import Button from '../components/ui/Button';
import momoLogo from '../assets/images/MOMO-Logo-App.png';
import './PaymentResult.css';

export default function PaymentResult() {
  // Demo state for the UI preview (Success by default)
  const [status, setStatus] = useState('success'); // success, failure, loading, refunding, refunded

  const renderStatusIcon = () => {
    switch (status) {
      case 'success':
        return (
          <div className="status-icon-wrapper success">
            <div className="status-icon-inner">
              <Check size={20} strokeWidth={3} />
            </div>
          </div>
        );
      case 'failure':
        return (
          <div className="status-icon-wrapper failure">
            <div className="status-icon-inner">
              <X size={20} strokeWidth={3} />
            </div>
          </div>
        );
      case 'loading':
      case 'refunding':
        return (
          <div className="status-icon-wrapper loading">
            <div className="status-icon-inner">
              <Clock size={20} strokeWidth={3} />
            </div>
          </div>
        );
      case 'refunded':
        return (
          <div className="status-icon-wrapper refunded">
            <div className="status-icon-inner">
              <RotateCcw size={20} strokeWidth={3} />
            </div>
          </div>
        );
      default:
        return null;
    }
  };

  const getTitle = () => {
    switch (status) {
      case 'success': return 'Thanh toán thành công';
      case 'failure': return 'Thanh toán thất bại';
      case 'loading': return 'Đang xử lý thanh toán';
      case 'refunding': return 'Đang xử lý hoàn tiền';
      case 'refunded': return 'Đã hoàn tiền';
      default: return '';
    }
  };

  const getAmountColor = () => {
    if (status === 'failure') return '#E53935';
    if (status === 'refunded') return '#0ea84e';
    return '#0A52FF';
  };

  return (
    <MainLayout>
      <div className="payment-result-page">
        <div className="payment-result-card">
          {/* Logo & Status (Stacked & Overlapped) */}
          <div className="payment-hero-icon">
            <img src={momoLogo} alt="MoMo" className="provider-logo" />
            <div className="status-badge">
              {renderStatusIcon()}
            </div>
          </div>

          {/* Title & Amount */}
          <h1 className="payment-title">{getTitle()}</h1>
          <div className="payment-amount" style={{ color: getAmountColor() }}>
            2.150.000đ
          </div>

          {/* Details Box */}
          <div className="payment-details-box">
            <div className="payment-detail-row">
              <span className="detail-label">Mã giao dịch MoMo</span>
              <span className="detail-value">MM192837465</span>
            </div>
            <div className="payment-detail-divider"></div>
            <div className="payment-detail-row">
              <span className="detail-label">Mã đơn hàng</span>
              <span className="detail-value">#128</span>
            </div>
          </div>

          {/* Action Button */}
          <div className="payment-action">
            {status === 'failure' ? (
              <Button variant="primary" className="payment-btn" onClick={() => window.history.back()}>
                Thử lại
              </Button>
            ) : (
              <Link to="/" style={{ width: '100%', textDecoration: 'none' }}>
                <Button variant="primary" className="payment-btn">
                  Xem đơn hàng của tôi
                </Button>
              </Link>
            )}
          </div>
        </div>

        {/* Development Only: State Toggles (like in Figma) */}
        <div className="dev-state-toggles">
          <button 
            className={`dev-toggle default ${status === 'loading' ? 'active' : ''}`}
            onClick={() => setStatus('loading')}
          >
            Loading
          </button>
          <button 
            className={`dev-toggle success ${status === 'success' ? 'active' : ''}`}
            onClick={() => setStatus('success')}
          >
            Success
          </button>
          <button 
            className={`dev-toggle failure ${status === 'failure' ? 'active' : ''}`}
            onClick={() => setStatus('failure')}
          >
            Failure
          </button>
          <button 
            className={`dev-toggle refunding ${status === 'refunding' ? 'active' : ''}`}
            onClick={() => setStatus('refunding')}
          >
            Refunding
          </button>
          <button 
            className={`dev-toggle refunded ${status === 'refunded' ? 'active' : ''}`}
            onClick={() => setStatus('refunded')}
          >
            Refunded
          </button>
        </div>
      </div>
    </MainLayout>
  );
}
