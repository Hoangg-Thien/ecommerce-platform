import axiosClient from './axiosClient';

const productApi = {
    // retrive a paginated list of product filtered by category

    getProducts: (params) => {
        // params can contain: {page, size, categoryId}
        const url = '/products';
        return axiosClient.get(url, {params});
    },

    // retrive product details
    getProductById: (id) => {
        const url = `/products/${id}`;
        return axiosClient.get(url);
    }
};

export default productApi;