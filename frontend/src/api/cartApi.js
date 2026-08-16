import axiosClient from './axiosClient';

const cartApi = {

    getCart: () => {
        return axiosClient.get('/carts');
    },

    addToCart: (variantId, quantity = 1) => {
        return axiosClient.post('/carts/add', {variantId, quantity});
    },

    removeCartItem: (itemId) => {
        return axiosClient.delete(`/carts/${itemId}`);
    },
    
    updateQuantity: (variantId, quantity) => {
        return axiosClient.put('/carts/update', {variantId, quantity});
    },
};

export default cartApi;