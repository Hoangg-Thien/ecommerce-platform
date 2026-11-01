import { useEffect, useState } from 'react';
import productApi from '../../../api/productApi';
import { apiError } from '../../../utils/promotion';

export default function ProductPicker({ ids, onChange }) {
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(0);
  const [result, setResult] = useState(null);
  const key = JSON.stringify([query, page]);
  useEffect(() => {
    let current = true;
    productApi.getProducts({ q: query, page, size: 10 }).then(data => {
      if (current) setResult({ key, data });
    }).catch(error => { if (current) setResult({ key, error: apiError(error) }); });
    return () => { current = false; };
  }, [query, page, key]);
  const data = result?.key === key ? result.data : null;
  return <div className="promotion-picker">
    <label>Tìm sản phẩm<input type="search" value={query} onChange={e => { setQuery(e.target.value); setPage(0); }} /></label>
    <p>Đã chọn {ids.length} sản phẩm</p>
    {result?.key === key && result.error && <p role="alert">{result.error}</p>}
    {!data ? !(result?.key === key && result.error) && <p role="status">Đang tải sản phẩm…</p> : <>
      {!data.content.length && <p>Không có sản phẩm phù hợp.</p>}
      {data.content.map(product => <label key={product.id} className="promotion-choice">
        <input type="checkbox" checked={ids.includes(product.id)} onChange={e => onChange(e.target.checked ? [...ids, product.id] : ids.filter(id => id !== product.id))} />
        #{product.id} — {product.name}
      </label>)}
      <div className="promotion-actions">
        <button type="button" disabled={!page} onClick={() => setPage(page - 1)}>Trang trước</button>
        <span>Trang {page + 1}</span>
        <button type="button" disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>Trang sau</button>
      </div>
    </>}
  </div>;
}
