import axiosClient, { refreshSession } from "./axiosClient";

const authApi = {
    login: (data) => axiosClient.post('/auth/login', data),
    register: (data) => axiosClient.post('/auth/register', data),
    refreshToken: refreshSession,
    logout: () => axiosClient.post('/auth/logout'),
};

export default authApi;
