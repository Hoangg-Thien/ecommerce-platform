import axiosClient from './axiosClient';

const categoryApi = {
    getAllCategories: () => {
        const url = '/categories';
        return axiosClient.get(url);
    }
};

export default categoryApi;