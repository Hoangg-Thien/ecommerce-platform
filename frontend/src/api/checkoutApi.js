import axiosClient from './axiosClient';

const checkoutApi = {
    preview: (data, signal) => axiosClient.post('/checkout/preview', data, { signal }),
    result: (key) => axiosClient.get('/checkout/result', { headers: { 'Idempotency-Key': key }, timeout: 15000 }),
    
    // send pay request
    checkout: (data, idempotencyKey) => {
        return axiosClient.post('/checkout', data, {
            timeout: 30000,
            headers: {
                'Idempotency-Key': idempotencyKey
            }
        });
    }
};

export default checkoutApi;
