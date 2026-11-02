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
let sessionGeneration = 0;
let refreshPromise = null;

export const setAccessToken = (token) => {
    accessToken = token;
    sessionGeneration++;
};

export const getAccessToken = () => accessToken;

// Shared by initial session restoration and unauthorized-request retries.
// Raw Axios prevents a failed refresh from recursively refreshing itself.
export const refreshSession = () => {
    if (!refreshPromise) {
        refreshPromise = axios.post(`${axiosClient.defaults.baseURL}/auth/refresh`, {}, { withCredentials: true })
            .then(({ data }) => {
                setAccessToken(data.accessToken);
                return data;
            })
            .finally(() => { refreshPromise = null; });
    }
    return refreshPromise;
};

// interceptor for request: attach token
axiosClient.interceptors.request.use((config) => {
    config._sessionGeneration = sessionGeneration;
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
        if((error.response?.status === 401 || error.response?.status === 403)
            && originalRequest && !originalRequest._retry && !originalRequest.url?.startsWith('/auth/')){
            originalRequest._retry = true;

            try{
            // A delayed 401 may belong to a request sent before another rotation.
            // Compare generations, since access JWT strings can match within one second.
            const newAccessToken = accessToken && originalRequest._sessionGeneration !== sessionGeneration
                ? accessToken : (await refreshSession()).accessToken;

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
