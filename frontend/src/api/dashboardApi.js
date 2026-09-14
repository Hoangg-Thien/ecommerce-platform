import axiosClient from './axiosClient';

const dashboardApi = {
  /** GET /api/v1/admin/dashboard — toàn bộ stats */
  getStats: () => axiosClient.get('/admin/dashboard'),
};

export default dashboardApi;
