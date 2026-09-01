import axiosClient from './axiosClient';

const categoryApi = {
    // GET /api/v1/categories
    getAllCategories: () => {
        return axiosClient.get('/categories');
    },

    // GET /api/v1/categories/:id
    getCategoryById: (id) => {
        return axiosClient.get(`/categories/${id}`);
    },

    // POST /api/v1/categories  (ADMIN)
    createCategory: (data) => {
        return axiosClient.post('/categories', data);
    },

    // PUT /api/v1/categories/:id  (ADMIN)
    updateCategory: (id, data) => {
        return axiosClient.put(`/categories/${id}`, data);
    },

    // DELETE /api/v1/categories/:id  (ADMIN)
    deleteCategory: (id) => {
        return axiosClient.delete(`/categories/${id}`);
    },
};

export default categoryApi;