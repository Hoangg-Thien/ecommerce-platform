import axios from 'axios';

// initialize an axios instance
const axiosClient = axios.create({
    baseURL:
    import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1',
    headers: {
        'Content-Type': 'application/json',
    },
    withCredentials: true,
});

// store the access token in memory to prevent XSS
let accessToken = null;

export const setAccessToken = (token) => {
    accessToken = token;
};

export const getAccessToken = () => accessToken;

// interceptor for request: attach token
axiosClient.interceptors.request.use((config) => {
    if(accessToken){
        config.headers.Authorization = `Bearer ${accessToken}`;
    }
    return config;
});

// interceptor for response: handle 401 error
axiosClient.interceptors.response.use((response) => response.data,
async(error) => {
        const originalRequest = error.config;

        // if 401 or 403 and request has not been retried even once
        if((error.response?.status === 401 || error.response?.status === 403) && !originalRequest._retry){
            originalRequest._retry = true;

            try{
            // call api to get new token
            // use the raw axios instance to avoid an interceptor loop
            const response = await axios.post(`${axiosClient.defaults.baseURL}/auth/refresh`, 
                {}, // no body needed, refreshToken is in cookie
                { withCredentials: true }
            );

            const newAccessToken = response.data.accessToken;

            setAccessToken(newAccessToken);

            // call first request with new token
            originalRequest.headers.Authorization = `Bearer ${newAccessToken}`;
            return axiosClient(originalRequest);

            }catch(refreshError){
            // refresh failed
            setAccessToken(null);
            localStorage.removeItem('user');
            window.location.href = '/login';
            return Promise.reject(refreshError);
            }
        }
        return Promise.reject(error);
    }
);

export default axiosClient;