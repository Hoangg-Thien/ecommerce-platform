package com.ecommerce.payment.strategy;

import com.ecommerce.checkout.dto.response.CheckoutResponse;
import com.ecommerce.cart.entity.Cart;
import com.ecommerce.order.entity.Order;

// Interface này là "hợp đồng" mà mọi phương thức thanh toán phải tuân theo
public interface PaymentStrategy {

    // Nhận vào Order (đã build nhưng chưa save) và Cart (chưa xoá)
    // Trả về CheckoutResponse để controller trả về cho frontend
    CheckoutResponse processPayment(Order order, Cart cart);
}
