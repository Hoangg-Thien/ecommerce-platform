import axiosClient from './axiosClient';

const paymentApi = {

    // polling for order status
    getPaymentStatus: (orderId, signal) => {
        return axiosClient.get(`/payments/order/${orderId}`, { signal });
    }
};

export default paymentApi;
