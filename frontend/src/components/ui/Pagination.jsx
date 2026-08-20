import React from 'react';

export default function Pagination({ currentPage, totalPages, onPageChange }) {
  if (totalPages <= 1) return null;
  return (
    <div style={{ display: 'flex', justifyContent: 'center', gap: '8px', marginTop: '30px' }}>
      <button 
        disabled={currentPage === 0} 
        onClick={() => onPageChange(currentPage - 1)}
        style={{ padding: '8px 16px', cursor: currentPage === 0 ? 'not-allowed' : 'pointer' }}
      >
        Trang trước
      </button>
      
      <span style={{ padding: '8px 16px', fontWeight: 'bold' }}>
        {currentPage + 1} / {totalPages}
      </span>

      <button 
        disabled={currentPage >= totalPages - 1} 
        onClick={() => onPageChange(currentPage + 1)}
        style={{ padding: '8px 16px', cursor: currentPage >= totalPages - 1 ? 'not-allowed' : 'pointer' }}
      >
        Trang sau
      </button>
    </div>
  );
}
