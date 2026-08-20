import { createPortal } from 'react-dom';
import './ConfirmModal.css';
import Button from './Button';

export default function ConfirmModal({ 
  isOpen, 
  title, 
  message, 
  onConfirm, 
  onCancel, 
  confirmText = "Đồng ý", 
  cancelText = "Hủy",
  isDanger = true
}) {
  if (!isOpen) return null;

  return createPortal(
    <div className="confirm-modal-overlay" onClick={onCancel}>
      <div className="confirm-modal-content" onClick={e => e.stopPropagation()}>
        <div className="confirm-modal-header">
          <h3 className="confirm-modal-title">{title}</h3>
          <button className="confirm-modal-close" onClick={onCancel}>✕</button>
        </div>
        <div className="confirm-modal-body">
          <p className="confirm-modal-message">{message}</p>
        </div>
        <div className="confirm-modal-footer">
          <Button variant="outline" onClick={onCancel}>{cancelText}</Button>
          <Button 
            variant="primary" 
            onClick={onConfirm} 
            style={isDanger ? { backgroundColor: '#e74c3c', borderColor: '#e74c3c' } : {}}
          >
            {confirmText}
          </Button>
        </div>
      </div>
    </div>,
    document.body
  );
}
