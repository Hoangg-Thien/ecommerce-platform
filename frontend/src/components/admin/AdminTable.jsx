import { Inbox } from 'lucide-react';
import './AdminTable.css';

/**
 * AdminTable — Generic table với loading skeleton và empty state
 *
 * Props:
 *   columns: [{ key, label, render?, className? }]
 *   data: array of row objects
 *   isLoading: bool
 *   emptyMessage: string
 *   emptyDescription: string
 *   skeletonRows: number (default 5)
 *   keyField: string (default 'id')
 */
export default function AdminTable({
  columns = [],
  data = [],
  isLoading = false,
  emptyMessage = 'Không có dữ liệu',
  emptyDescription = '',
  skeletonRows = 5,
  keyField = 'id',
}) {
  return (
    <div className="admin-table-wrapper">
      <table className="admin-table">
        <thead>
          <tr>
            {columns.map((col) => (
              <th key={col.key} className={col.className || ''}>
                {col.label}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {isLoading ? (
            // Skeleton rows
            Array.from({ length: skeletonRows }).map((_, i) => (
              <tr key={`skeleton-${i}`} className="admin-table__skeleton-row">
                {columns.map((col) => (
                  <td key={col.key}>
                    <div
                      className="admin-table__skeleton-cell"
                      style={{ width: col.skeletonWidth || '80%' }}
                    />
                  </td>
                ))}
              </tr>
            ))
          ) : data.length === 0 ? (
            // Empty state
            <tr>
              <td colSpan={columns.length}>
                <div className="admin-table__empty">
                  <div className="admin-table__empty-icon">
                    <Inbox size={26} />
                  </div>
                  <div className="admin-table__empty-title">{emptyMessage}</div>
                  {emptyDescription && (
                    <div className="admin-table__empty-desc">{emptyDescription}</div>
                  )}
                </div>
              </td>
            </tr>
          ) : (
            // Data rows
            data.map((row) => (
              <tr key={row[keyField]}>
                {columns.map((col) => (
                  <td key={col.key} className={col.className || ''}>
                    {col.render ? col.render(row) : row[col.key]}
                  </td>
                ))}
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  );
}
