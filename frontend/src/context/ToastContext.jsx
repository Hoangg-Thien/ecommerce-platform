import { createContext, useContext, useState, useCallback } from 'react';
import './Toast.css';

const ToastContext = createContext();

export const ToastProvider = ({ children }) => {
  const [toast, setToast] = useState(null);

  const showToast = useCallback((message, type = 'success', duration = 1400) => {
    setToast({ message, type });
    // Auto hide after specified duration
    setTimeout(() => {
      setToast(null);
    }, duration);
  }, []);

  return (
    <ToastContext.Provider value={{ showToast }}>
      {children}
      {toast && (
        <div className={`toast-container toast-${toast.type} toast-enter`}>
          <div className="toast-icon">
            {toast.type === 'success' ? '✓' : '⚠️'}
          </div>
          <div className="toast-message">{toast.message}</div>
        </div>
      )}
    </ToastContext.Provider>
  );
};

export const useToast = () => useContext(ToastContext);
