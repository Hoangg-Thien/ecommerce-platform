import axiosClient from './axiosClient';

const mockPaymentApi = {
    simulatePayment: (orderId, data) => {
        const url = `/mock-payments/${orderId}/simulate`;
        return axiosClient.post(url, data);
    }
};

export default mockPaymentApi;
