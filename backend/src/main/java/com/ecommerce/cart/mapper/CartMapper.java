package com.ecommerce.cart.mapper;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Component;

import com.ecommerce.cart.dto.response.CartItemResponse;
import com.ecommerce.cart.dto.response.CartResponse;
import com.ecommerce.cart.entity.Cart;
import com.ecommerce.cart.entity.CartItem;

@Component
public class CartMapper {
    
    public CartItemResponse toCartItemResponse(CartItem cartItem){
        if(cartItem == null){
            return null;
        }
        
        return mapToItemResponse(cartItem);
    }

    private CartItemResponse mapToItemResponse(CartItem cartItem) {
        return CartItemResponse.builder()
                .id(cartItem.getId())
                .productId(cartItem.getProductVariant().getProduct().getId())
                .variantId(cartItem.getProductVariant().getId())
                .productName(cartItem.getProductVariant().getProduct().getName())
                .imageUrl(cartItem.getProductVariant().getProduct().getImageUrl())
                .size(cartItem.getProductVariant().getSize())
                .productPrice(cartItem.getProductVariant().getProduct().getPrice())
                .quantity(cartItem.getQuantity())
                .subTotal(cartItem.getProductVariant().getProduct().getPrice().multiply(new BigDecimal(cartItem.getQuantity())))
                .build();
    }

    public CartResponse toCartResponse(Cart cart){
        if(cart == null){
            return null;
        }

        List<CartItemResponse> itemResponses = cart.getItems()
        .stream()
        .map(this::toCartItemResponse)
        .toList();

        BigDecimal totalPrice = itemResponses
        .stream()
        .map(CartItemResponse::getSubTotal)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

        return CartResponse.builder()
               .id(cart.getId())
               .userId(cart.getUser().getId())
               .items(itemResponses)
               .totalPrice(totalPrice)
               .build();
    }
}
