import { createContext, useState, useEffect, useContext } from 'react';
import cartApi from '../api/cartApi';
import { useAuth } from './AuthContext';

const CartContext = createContext();

export const CartProvider = ({ children }) => {
  const { user } = useAuth();
  const [cart, setCart] = useState(null);
  const [isLoading, setIsLoading] = useState(false);

  const fetchCart = async () => {
    setIsLoading(true);
    try {
      const data = await cartApi.getCart();
      setCart(data);
    } catch (error) {
      console.error('Lỗi khi fetch giỏ hàng', error);
    } finally {
      setIsLoading(false);
    }
  };

  // Lấy giỏ hàng mỗi khi user đăng nhập thành công
  useEffect(() => {
    if (user) {
      fetchCart();
    } else {
      setCart(null); // Xóa giỏ hàng local khi logout
    }
  }, [user]);

  const addToCart = async (variantId, quantity = 1) => {
    try {
      const updatedCart = await cartApi.addToCart(variantId, quantity);
      setCart(updatedCart); // Server trả về giỏ hàng mới nhất kèm tổng tiền -> update luôn
      return true;
    } catch (error) {
      console.error('Lỗi thêm vào giỏ', error);
      throw error;
    }
  };

  const removeCartItem = async (itemId) => {
    try {
      const updatedCart = await cartApi.removeCartItem(itemId);
      setCart(updatedCart); // Update lại tổng tiền mới tự động
    } catch (error) {
      console.error('Lỗi xóa item', error);
      throw error;
    }
  };

  const updateQuantity = async (variantId, quantity) => {
    try {
      const updatedCart = await cartApi.updateQuantity(variantId, quantity);
      setCart(updatedCart);
    } catch (error) {
      console.error('Lỗi cập nhật số lượng', error);
      throw error;
    }
  }

  const clearCart = () => {
    setCart(null);
  };

  return (
    <CartContext.Provider value={{ cart, addToCart, removeCartItem, updateQuantity, isLoading, fetchCart, clearCart }}>
      {children}
    </CartContext.Provider>
  );
};

export const useCart = () => useContext(CartContext);
