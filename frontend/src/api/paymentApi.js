import axiosClient from './axiosClient';

const paymentApi = {

    // polling for order status
    getPaymentStatus: (orderId) => {
        return axiosClient.get(`/payments/order/${orderId}`);
    }
};

export default paymentApi;