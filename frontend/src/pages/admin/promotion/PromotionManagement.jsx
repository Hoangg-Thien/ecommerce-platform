import { useEffect, useRef, useState } from 'react';
import api from '../../../api/promotionApi';
import { apiError, money, vietnamTime } from '../../../utils/promotion';
import ProgramForm from './ProgramForm';
import RuleForm from './RuleForm';
import './PromotionManagement.css';

const groups = [
  ['vouchers', 'vouchers', 'Voucher'],
  ['order-discounts', 'orderDiscounts', 'Giảm đơn tự động'],
  ['product-discounts', 'productDiscounts', 'Giảm sản phẩm'],
];
const phases = { RUNNING: 'Đang diễn ra', SCHEDULED: 'Sắp diễn ra', ENDED: 'Đã kết thúc' };
export default function PromotionManagement() {
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState(null);
  const [revision, setRevision] = useState(0);
  const [list, setList] = useState(null);
  const [detail, setDetail] = useState(null);
  const [editor, setEditor] = useState(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const inFlight = useRef(false);
  const listKey = `${page}:${revision}`;
  const detailKey = `${selected}:${revision}`;
  useEffect(() => {
    const controller = new AbortController();
    api.list(page, controller.signal).then(data => {
      if (!controller.signal.aborted) setList({ key: listKey, data });
    }).catch(error => { if (!controller.signal.aborted) setList({ key: listKey, error: apiError(error) }); });
    return () => controller.abort();
  }, [page, listKey]);
  useEffect(() => {
    if (!selected) return;
    const controller = new AbortController();
    api.detail(selected, controller.signal).then(data => {
      if (!controller.signal.aborted) setDetail({ key: detailKey, data });
    }).catch(error => { if (!controller.signal.aborted) setDetail({ key: detailKey, error: apiError(error) }); });
    return () => controller.abort();
  }, [selected, detailKey]);
  const mutate = async action => {
    if (inFlight.current) return;
    inFlight.current = true;
    setBusy(true); setMessage('');
    try {
      await action(); setEditor(null); setRevision(n => n + 1); setMessage('Đã lưu thay đổi.');
    } catch (error) {
      setMessage(error.response?.status === 409 ? `Không thể thực hiện: ${apiError(error)}. Dữ liệu có lịch sử cần được tắt thay vì xóa; mã và sản phẩm không được trùng.` : apiError(error));
    } finally { inFlight.current = false; setBusy(false); }
  };
  const remove = (label, action) => {
    if (window.confirm(`Xóa ${label}? Chỉ dữ liệu chưa được sử dụng mới có thể xóa.`)) mutate(action);
  };
  const data = list?.key === listKey ? list.data : null;
  const program = detail?.key === detailKey ? detail.data : null;
  return <section className="promotion-admin">
    <header><h1>Khuyến mãi</h1><p>Giá chỉnh sửa áp dụng cho lần đặt hàng mới. Đơn đã tạo giữ nguyên giá.</p></header>
    {message && <p role="status" className="promotion-notice">{message}</p>}
    <fieldset disabled={busy}>
      <button onClick={() => { setSelected(null); setEditor({ type: 'program' }); }}>Tạo chương trình</button>
      <button onClick={() => setRevision(n => n + 1)}>Tải lại</button>
      {list?.key === listKey && list.error && <p role="alert">{list.error}</p>}
      {!data && !list?.error && <p role="status">Đang tải chương trình…</p>}
      {data && <>
        {!data.content.length && <p>Chưa có chương trình ở trang này.</p>}
        <div className="promotion-table"><table>
          <caption>Danh sách chương trình</caption>
          <thead><tr><th>Tên</th><th>Trạng thái</th><th>Thời gian (VN)</th><th>Thao tác</th></tr></thead>
          <tbody>{data.content.map(p => <tr key={p.id}>
            <td>#{p.id} {p.name}</td><td>{p.status === 'ACTIVE' ? 'Bật' : 'Tắt'} · {phases[p.phase]}</td>
            <td>{vietnamTime(p.startDate)} — {vietnamTime(p.endDate)}</td>
            <td><button onClick={() => { setSelected(p.id); setEditor(null); setMessage(''); }}>Chi tiết #{p.id}</button></td>
          </tr>)}</tbody>
        </table></div>
        <div className="promotion-actions"><button disabled={!page} onClick={() => setPage(page - 1)}>Trang trước</button><span>Trang {page + 1} / {Math.max(1, data.totalPages)}</span><button disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>Trang sau</button></div>
      </>}
    </fieldset>
    {editor?.type === 'program' && <ProgramForm key={editor.program?.id ?? 'new'} program={editor.program} busy={busy}
      onSave={form => mutate(async () => { const saved = await api.save(editor.program?.id, form); setSelected(saved.id); })} />}
    {selected && detail?.key === detailKey && detail.error && <p role="alert">{detail.error}</p>}
    {selected && !program && detail?.key !== detailKey && <p role="status">Đang tải chi tiết…</p>}
    {program && <article>
      <h2>{program.name}</h2><p>{program.description}</p>
      <div className="promotion-actions">
        <button disabled={busy} onClick={() => setEditor({ type: 'program', program })}>Sửa chương trình</button>
        <button disabled={busy} onClick={() => mutate(() => api.status(program.id, program.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'))}>{program.status === 'ACTIVE' ? 'Tắt chương trình' : 'Bật chương trình'}</button>
        <button disabled={busy} onClick={() => remove('chương trình', async () => { await api.remove(program.id); setSelected(null); })}>Xóa chương trình</button>
      </div>
      {groups.map(([type, field, title]) => <section key={type} className="promotion-rule-group">
        <h3>{title}</h3><button disabled={busy} onClick={() => setEditor({ type })}>Thêm {title.toLowerCase()}</button>
        {editor?.type === type && <RuleForm key={`${program.id}:${type}:${editor.rule?.id ?? 'new'}`} type={type} rule={editor.rule} busy={busy}
          onCancel={() => setEditor(null)} onSave={form => mutate(() => api.saveRule(program.id, type, editor.rule?.id, form))} />}
        {!program[field].length && <p>Chưa có ưu đãi.</p>}
        <ul>{program[field].map(rule => <li key={rule.id}>
          <span>{rule.code || (rule.productId ? `Sản phẩm #${rule.productId}` : `Tối thiểu ${money(rule.minimumAmount)}`)} — Giảm {rule.discountPercent}%
            {rule.maximumDiscount != null && `, tối đa ${money(rule.maximumDiscount)}`}
            {type === 'vouchers' && ` · Tối thiểu ${money(rule.minimumAmount)} · ${rule.usedQuantity}/${rule.quantity} lượt · ${rule.status === 'ACTIVE' ? 'Bật' : 'Tắt'}`}</span>
          <div className="promotion-actions">
            <button disabled={busy} onClick={() => setEditor({ type, rule })}>Sửa #{rule.id}</button>
            {type === 'vouchers' && <button disabled={busy} onClick={() => mutate(() => api.voucherStatus(program.id, rule.id, rule.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'))}>{rule.status === 'ACTIVE' ? 'Tắt mã' : 'Bật mã'}</button>}
            <button disabled={busy} onClick={() => remove(`ưu đãi #${rule.id}`, () => api.removeRule(program.id, type, rule.id))}>Xóa #{rule.id}</button>
          </div>
        </li>)}</ul>
      </section>)}
    </article>}
  </section>;
}
