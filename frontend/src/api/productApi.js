import axiosClient from './axiosClient';

const productApi = {
    // GET /api/v1/products?page=0&size=10&categoryId=&sortBy=id&sortDir=asc
    getProducts: (params) => {
        return axiosClient.get('/products', { params });
    },

    // GET /api/v1/products/:id
    getProductById: (id) => {
        return axiosClient.get(`/products/${id}`);
    },

    // POST /api/v1/products  (ADMIN)
    // body: { name, price, imageUrl, description, categoryId, variants: [{size, stock}] }
    createProduct: (data) => {
        return axiosClient.post('/products', data);
    },

    // PUT /api/v1/products/:id  (ADMIN)
    updateProduct: (id, data) => {
        return axiosClient.put(`/products/${id}`, data);
    },

    // DELETE /api/v1/products/:id  (ADMIN)
    deleteProduct: (id) => {
        return axiosClient.delete(`/products/${id}`);
    },
};

export default productApi;