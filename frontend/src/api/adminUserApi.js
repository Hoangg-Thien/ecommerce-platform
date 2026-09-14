import axiosClient from './axiosClient';

const adminUserApi = {
  /**
   * Lấy danh sách users (phân trang + filter)
   * @param {Object} params - { page, size, role, email }
   */
  getAllUsers: (params = {}) => {
    const query = new URLSearchParams();
    if (params.page !== undefined) query.append('page', params.page);
    if (params.size !== undefined) query.append('size', params.size);
    if (params.role) query.append('role', params.role);
    if (params.email) query.append('email', params.email);
    return axiosClient.get(`/admin/users?${query.toString()}`);
  },

  /** Lấy chi tiết 1 user */
  getUserById: (id) => axiosClient.get(`/admin/users/${id}`),

  /**
   * Đổi role của user
   * @param {number} id
   * @param {'USER'|'ADMIN'} role
   */
  updateRole: (id, role) =>
    axiosClient.patch(`/admin/users/${id}/role`, { role }),
};

export default adminUserApi;
