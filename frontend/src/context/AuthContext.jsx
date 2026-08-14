import { createContext, useState, useEffect, useContext, Children } from 'react';
import authApi from '../api/authApi';
import { setAccessToken } from '../api/axiosClient';

const AuthContext = createContext();

export const AuthProvider = ({children}) => {

    const [user, setUser] = useState(null);
    const [loading, setLoading] = useState(true);

    // restore session from localstorage on browser refresh (f5)
    useEffect(() => {
        const initializeAuth = async () => {
            const storedUser = localStorage.getItem('user');
            
            if(storedUser){
                try {
                    // Gọi refresh token để lấy accessToken mới vào bộ nhớ
                    const res = await authApi.refreshToken();
                    setAccessToken(res.accessToken);
                    setUser(JSON.parse(storedUser));
                } catch (error) {
                    console.error('Lỗi khôi phục phiên đăng nhập', error);
                    localStorage.removeItem('user');
                    setUser(null);
                }
            }
            setLoading(false);
        };
        
        initializeAuth();
    }, []);

    const login = async (credentials) => {
        const res = await authApi.login(credentials);
        const { accessToken, role, email, id } = res;
    
        const userData = { id, email, role };

        // update state & memory
        setUser(userData);
        setAccessToken(accessToken);

        // hhtpOnly cookie is handled by browser
        localStorage.setItem('user', JSON.stringify(userData));
    };

    const register = async (userDataInput) => {
        const res = await authApi.register(userDataInput);
        const { accessToken, role, email, id } = res;
    
        const userData = { id, email, role };

        // update state & memory
        setUser(userData);
        setAccessToken(accessToken);

        // hhtpOnly cookie is handled by browser
        localStorage.setItem('user', JSON.stringify(userData));
    };

    const logout = () => {
    setUser(null);
    setAccessToken(null);
    localStorage.removeItem('user');
    };

    return(
        <AuthContext.Provider value={{ user, login, register, logout, loading }}>
        {!loading && children}
        </AuthContext.Provider>
    );
};

export const useAuth = () => useContext(AuthContext);