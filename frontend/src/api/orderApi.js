import axiosClient from './axiosClient';

const orderApi = {

    // retrieve a paginated list of orders
    getUserOrders: (page = 0, size = 10) => {
        return axiosClient.get(`/orders`, {params: {page,size}});
    },

    // retrieve a order detail
    getOrderById: (id) => {
        return axiosClient.get(`/orders/${id}`);
    }
};

export default orderApi;