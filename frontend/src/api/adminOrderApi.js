import axiosClient from './axiosClient';

const adminOrderApi = {
  /**
   * Lấy tất cả đơn hàng (có phân trang + filter)
   * @param {Object} params - { page, size, status, email }
   */
  getAllOrders: (params = {}) => {
    const query = new URLSearchParams();
    if (params.page !== undefined) query.append('page', params.page);
    if (params.size !== undefined) query.append('size', params.size);
    if (params.status) query.append('status', params.status);
    if (params.email) query.append('email', params.email);
    return axiosClient.get(`/admin/orders?${query.toString()}`);
  },

  /** Lấy chi tiết 1 đơn (admin bypass owner check) */
  getOrderById: (id) =>
    axiosClient.get(`/admin/orders/${id}`),

  /** Đổi trạng thái đơn */
  updateOrderStatus: (id, orderStatus) =>
    axiosClient.patch(`/admin/orders/${id}/status`, { orderStatus }),
};

export default adminOrderApi;
