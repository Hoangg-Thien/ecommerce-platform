import axiosClient from './axiosClient';

const cartApi = {

    getCart: () => {
        return axiosClient.get('/carts');
    },

    addToCart: (productId, quantity = 1) => {
        return axiosClient.post('/carts/add', {productId, quantity});
    },

    removeCartItem: (itemId) => {
        return axiosClient.delete(`/carts/${itemId}`);
    },
    
    updateQuantity: (productId, quantity) => {
        return axiosClient.put('/carts/update', {productId, quantity});
    },
};

export default cartApi;