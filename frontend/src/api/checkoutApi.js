import axiosClient from './axiosClient';

const checkoutApi = {
    
    // send pay request
    checkout: (data, idempotencyKey) => {
        return axiosClient.post('/checkout', data, {
            headers: {
                'Idempotency-Key': idempotencyKey
            }
        });
    }
};

export default checkoutApi;