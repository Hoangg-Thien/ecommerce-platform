package com.ecommerce.service;

import com.ecommerce.controller.ProductController;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecommerce.dto.request.AddToCartRequest;
import com.ecommerce.dto.request.UpdateCartItemRequest;
import com.ecommerce.dto.response.CartResponse;
import com.ecommerce.entity.Cart;
import com.ecommerce.entity.CartItem;
import com.ecommerce.entity.ProductVariant;
import com.ecommerce.entity.User;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.mapper.CartMapper;
import com.ecommerce.repository.CartRepository;
import com.ecommerce.repository.ProductVariantRepository;
import com.ecommerce.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final ProductVariantRepository productVariantRepository;
    private final UserRepository userRepository;
    private final CartMapper cartMapper;

    @Transactional
    public CartResponse addToCart(String userEmail, AddToCartRequest request){

        log.info("Adding to cart: user='{}', variantId={}, quantity={}",
        userEmail, request.getVariantId(), request.getQuantity());

        // tim user qua email dang nhap
        User user = userRepository.findByEmail(userEmail)
        .orElseThrow(() -> new ResourceNotFoundException("User", "email", userEmail));
        
        // tim product variant va kiem tra ton kho
        ProductVariant variant = productVariantRepository.findById(request.getVariantId())
        .orElseThrow(() -> new ResourceNotFoundException("ProductVariant", request.getVariantId()));
        
        if(variant.getStock() < request.getQuantity()){
            throw new IllegalArgumentException("Sản phẩm " + variant.getProduct().getName() + " (Size " + variant.getSize() + ") không đủ số lượng trong kho!");
        }

        // tim cart cua user, neu chua co thi khoi tao
        Cart cart = cartRepository.findByUserId(user.getId())
        .orElseGet(() -> {
            Cart newCart = new Cart();
            newCart.setUser(user);
            return cartRepository.save(newCart);
        });

        // kiem tra variant co trong cart chua
        Optional<CartItem> existingItem = cart.getItems().stream()
        .filter(item -> item.getProductVariant().getId().equals(variant.getId()))
        .findFirst();

        if(existingItem.isPresent()){ // co thi tang so luong len
            CartItem item = existingItem.get();
            int oldQuantity = item.getQuantity();
            int newQuantity = oldQuantity + request.getQuantity();

            if(newQuantity > variant.getStock()){
                log.warn("Cannot add to cart: Total quantity {} exceeds available stock {} for variant '{}'",
                newQuantity, variant.getStock(), variant.getId());
                throw new IllegalArgumentException("Rất tiếc! Tổng số lượng vượt quá hàng có sẵn trong kho");
            }
            item.setQuantity(newQuantity);

            log.info("Updated quantity for variant {} in user '{}' cart: {} -> {}",
            variant.getId(), userEmail, oldQuantity, newQuantity);
            
        } else { // chua co thi tao item moi va them vao items cua cart
            CartItem newItem = new CartItem();
            newItem.setCart(cart);
            newItem.setProductVariant(variant);
            newItem.setQuantity(request.getQuantity());
            cart.getItems().add(newItem);
            log.info("Added new item (variantId={}) to cart of user '{}'", variant.getId(), userEmail);
        }
        Cart savedCart = cartRepository.save(cart);
        return cartMapper.toCartResponse(savedCart);
    }

    @Transactional
    public CartResponse getCart(String userEmail){

        // tim user qua email
        User user = userRepository.findByEmail(userEmail)
        .orElseThrow(() -> new ResourceNotFoundException("User", "email", userEmail));

        // tim cart cua user, chua co thi khoi tao gio rong
        Cart cart = cartRepository.findByUserId(user.getId())
        .orElseGet(() ->{
            Cart newCart = new Cart();
            newCart.setUser(user);
            return cartRepository.save(newCart);
        });
        return cartMapper.toCartResponse(cart);
    }

    @Transactional
    public CartResponse removeCartItem(String userEmail, long itemId){
        log.info("Removing cart item id={} for user '{}'", itemId, userEmail);

        // tim user qua email
        User user = userRepository.findByEmail(userEmail)
        .orElseThrow(() -> new ResourceNotFoundException("User", "email", userEmail));

        // tim cart cua user
        Cart cart = cartRepository.findByUserId(user.getId())
        .orElseThrow(() -> new ResourceNotFoundException("Can not found for user: " + userEmail));

        // tim cartItems theo itemId trong gio hang cua user
        CartItem itemToRemove = cart.getItems().stream()
        .filter(item -> item.getId().equals(itemId))
        .findFirst()
        .orElseThrow(() -> new ResourceNotFoundException("CartItem", itemId));

        cart.getItems().remove(itemToRemove);

        log.info("Successfully removed cart item id={} for user '{}'", itemId, userEmail);
        
        Cart savedCart = cartRepository.save(cart);
        return cartMapper.toCartResponse(savedCart);
    }

    @Transactional
    public CartResponse updateCartItemQuantity(String userEmail, UpdateCartItemRequest request){

        log.info("Updating cart item: user='{}', variantId={}, newQuantity={}", 
        userEmail, request.getVariantId(), request.getQuantity());

        // find user
        User user = userRepository.findByEmail(userEmail)
        .orElseThrow(() -> new ResourceNotFoundException("User", "email", userEmail));

        // find cart
        Cart cart = cartRepository.findByUserId(user.getId())
        .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giỏ hàng cho user: " + userEmail));

        // validate stock
        ProductVariant variant = productVariantRepository.findById(request.getVariantId())
        .orElseThrow(() -> new ResourceNotFoundException("ProductVariant", request.getVariantId()));

        if(variant.getStock() < request.getQuantity()){
            throw new IllegalArgumentException("Không đủ hàng trong kho cho sản phẩm: " + variant.getProduct().getName() + " (Size " + variant.getSize() + ")");
        }

        // find item
        CartItem cartItem = cart.getItems().stream()
        .filter(item -> item.getProductVariant().getId().equals(variant.getId()))
        .findFirst()
        .orElseThrow(() -> new ResourceNotFoundException("Sản phẩm không có trong giỏ hàng!"));

        cartItem.setQuantity(request.getQuantity());
        Cart savedCart = cartRepository.save(cart);
        return cartMapper.toCartResponse(savedCart);
    }
}
