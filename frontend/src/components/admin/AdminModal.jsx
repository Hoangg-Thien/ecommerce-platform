import { createPortal } from 'react-dom';
import { X } from 'lucide-react';
import './AdminModal.css';

/**
 * AdminModal — Layout chuẩn cho admin pop-up
 *
 * Props:
 *   isOpen: bool
 *   onClose: fn
 *   title: string
 *   size: 'sm' | 'md' | 'lg' | 'xl'  (default: 'md')
 *   footer: ReactNode  (optional — dùng khi cần custom footer)
 *   children
 */
export default function AdminModal({
  isOpen,
  onClose,
  title,
  size = 'md',
  footer,
  children,
}) {
  if (!isOpen) return null;

  const handleOverlayClick = (e) => {
    if (e.target === e.currentTarget) onClose();
  };

  return createPortal(
    <div className="admin-modal-overlay" onClick={handleOverlayClick}>
      <div className={`admin-modal admin-modal--${size}`} role="dialog" aria-modal="true" aria-labelledby="admin-modal-title">
        {/* Header */}
        <div className="admin-modal__header">
          <h2 className="admin-modal__title" id="admin-modal-title">{title}</h2>
          <button className="admin-modal__close" onClick={onClose} aria-label="Đóng">
            <X size={16} />
          </button>
        </div>

        {/* Body */}
        <div className="admin-modal__body">
          {children}
        </div>

        {/* Footer */}
        {footer && (
          <div className="admin-modal__footer">
            {footer}
          </div>
        )}
      </div>
    </div>,
    document.body
  );
}
