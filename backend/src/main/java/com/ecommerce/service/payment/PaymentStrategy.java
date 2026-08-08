package com.ecommerce.service.payment;

import com.ecommerce.dto.response.CheckoutResponse;
import com.ecommerce.entity.Cart;
import com.ecommerce.entity.Order;

// Interface này là "hợp đồng" mà mọi phương thức thanh toán phải tuân theo
public interface PaymentStrategy {

    // Nhận vào Order (đã build nhưng chưa save) và Cart (chưa xoá)
    // Trả về CheckoutResponse để controller trả về cho frontend
    CheckoutResponse processPayment(Order order, Cart cart);
}
