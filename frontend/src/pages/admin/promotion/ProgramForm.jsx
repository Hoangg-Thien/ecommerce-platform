import { useState } from 'react';

// datetime-local is explicitly Vietnamese time, independent of the admin browser timezone.
const localVietnam = instant => instant ? new Date(new Date(instant).getTime() + 7 * 3600000).toISOString().slice(0, 16) : '';
export default function ProgramForm({ program, onSave, busy }) {
  const [form, setForm] = useState({ name: program?.name || '', description: program?.description || '',
    startDate: localVietnam(program?.startDate), endDate: localVietnam(program?.endDate) });
  const change = event => setForm({ ...form, [event.target.name]: event.target.value });
  return <form onSubmit={event => {
    event.preventDefault();
    const instant = field => form[field] === localVietnam(program?.[field]) ? program[field] : new Date(form[field] + ':00+07:00').toISOString();
    onSave({ ...form, startDate: instant('startDate'), endDate: instant('endDate') });
  }}>
    <fieldset disabled={busy} className="promotion-form">
      <legend>{program ? 'Sửa chương trình' : 'Tạo chương trình'}</legend>
      <label>Tên<input name="name" required maxLength={200} value={form.name} onChange={change} /></label>
      <label>Mô tả<textarea name="description" value={form.description} onChange={change} /></label>
      <label>Bắt đầu (giờ Việt Nam)<input name="startDate" type="datetime-local" required value={form.startDate} onChange={change} /></label>
      <label>Kết thúc (giờ Việt Nam)<input name="endDate" type="datetime-local" required min={form.startDate} value={form.endDate} onChange={change} /></label>
      <button type="submit">Lưu chương trình</button>
    </fieldset>
  </form>;
}
