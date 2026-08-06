package com.ecommerce.service;

import java.math.BigDecimal;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecommerce.dto.response.OrderResponse;
import com.ecommerce.entity.Cart;
import com.ecommerce.entity.CartItem;
import com.ecommerce.entity.Order;
import com.ecommerce.entity.OrderItem;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.User;
import com.ecommerce.enums.OrderStatus;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.mapper.OrderMapper;
import com.ecommerce.repository.CartRepository;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final OrderMapper orderMapper;

    @Transactional
    public OrderResponse createOrder(String userEmail){

        // tim user
        User user = userRepository.findByEmail(userEmail)
        .orElseThrow(() -> new ResourceNotFoundException("User", "email", userEmail));

        // tim cart cua user
        Cart cart = cartRepository.findByUserId(user.getId())
        .orElseThrow(() -> new ResourceNotFoundException("Cart not found for user: " + userEmail));

        // gio hang co rong ko
        if(cart.getItems().isEmpty()){
            throw new IllegalArgumentException("Can not create order from an empty cart");
        }

        // khoi tao order
        Order order = new Order();
        order.setUser(user);
        order.setStatus(OrderStatus.PENDING);

        BigDecimal totalPrice = BigDecimal.ZERO;

        // duyet tung mon trong gio hang 
        for(CartItem cartItem : cart.getItems()){
            Product product = cartItem.getProduct();

        // kiem tra ton kho
        if(product.getStock() < cartItem.getQuantity()){
            throw new IllegalArgumentException("Not enough stock for product: " + product.getName());
        }

        // tru so luong ton kho
        product.setStock(product.getStock() - cartItem.getQuantity());
        productRepository.save(product);

        // khoi tao OrderItem moi
        OrderItem orderItem = new OrderItem();
        orderItem.setOrder(order);
        orderItem.setProduct(product);
        orderItem.setQuantity(cartItem.getQuantity());
        orderItem.setPrice(product.getPrice()); // luu gia tai thoi diem mua

        order.getItems().add(orderItem);

        // cong don tien: price * quantity
        BigDecimal subTotal = product.getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity()));
        totalPrice = totalPrice.add(subTotal);
        }

        order.setTotalPrice(totalPrice);

        // luu order
        Order savedOrder = orderRepository.save(order);

        // xoa sach gio hang sau khi dat hang thanh cong
        cart.getItems().clear();
        cartRepository.save(cart);

        return orderMapper.tOrderResponse(savedOrder);
    }

    @Transactional
    public List<OrderResponse> getUserOrders(String userEmail){

        // tim user
        User user = userRepository.findByEmail(userEmail)
        .orElseThrow(() -> new ResourceNotFoundException("User", "email", userEmail));

        // tim tu userRepository, do la list <>
        List<Order> orders = orderRepository.findByUserId(user.getId());

        // map danh sach order sang List<OrderResponse>
        return orders.stream()
        .map(orderMapper::tOrderResponse)
        .toList();
    }

    @Transactional
    public OrderResponse updateOrderStatus(Long orderId, OrderStatus newStatus){
        
        // tim order theo id
        Order order = orderRepository.findById(orderId)
        .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        // ko cho doi trang thai neu DONE va CANCELLED
        if(order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.DONE){
            throw new IllegalArgumentException("Cannot change status of an order that is already " + order.getStatus());
        }

        // neu CANCELLED thi hoan lai so luong ton kho (restock)
        if(newStatus == OrderStatus.CANCELLED){
            for(OrderItem orderItem : order.getItems()){ // duyet qua het tat ca san pham cua don hang
                Product product = orderItem.getProduct();
                product.setStock(product.getStock() + orderItem.getQuantity());
                productRepository.save(product);
            }
        }
        // cap nhat va luu
        order.setStatus(newStatus);
        Order updatedOrder = orderRepository.save(order);
        return orderMapper.tOrderResponse(updatedOrder);
    }
}
