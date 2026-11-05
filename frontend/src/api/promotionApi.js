import client from './axiosClient';
const root = '/admin/promotions';
export default {
  list: (page, signal) => client.get(root, { params: { page, size: 10 }, signal }),
  detail: (id, signal) => client.get(`${root}/${id}`, { signal }),
  save: (id, data) => id ? client.put(`${root}/${id}`, data) : client.post(root, data),
  status: (id, status) => client.patch(`${root}/${id}/status`, { status }),
  remove: id => client.delete(`${root}/${id}`),
  saveRule: (programId, type, id, data) => id
    ? client.put(`${root}/${programId}/${type}/${id}`, data)
    : client.post(`${root}/${programId}/${type}`, data),
  removeRule: (programId, type, id) => client.delete(`${root}/${programId}/${type}/${id}`),
  voucherStatus: (programId, id, status) => client.patch(`${root}/${programId}/vouchers/${id}/status`, { status }),
};
