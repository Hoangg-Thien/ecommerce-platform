package com.ecommerce.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecommerce.dto.request.AddToCartRequest;
import com.ecommerce.dto.response.CartResponse;
import com.ecommerce.entity.Cart;
import com.ecommerce.entity.CartItem;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.User;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.mapper.CartMapper;
import com.ecommerce.repository.CartRepository;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final CartMapper cartMapper;

    @Transactional
    public CartResponse addToCart(String userEmail, AddToCartRequest request){

        log.info("Adding to cart: user='{}', productId={}, quantity={}",
        userEmail, request.getProductId(), request.getQuantity());

        // tim user qua email dang nhap
        User user = userRepository.findByEmail(userEmail)
        .orElseThrow(() -> new ResourceNotFoundException("User", "email", userEmail));
        
        // tim product va kiem tra ton kho
        Product product = productRepository.findById(request.getProductId())
        .orElseThrow(() -> new ResourceNotFoundException("Product", request.getProductId()));
        
        if(product.getStock() < request.getQuantity()){
            throw new IllegalArgumentException("Not enough stock for product: " + product.getName());
        }

        // tim cart cua user, neu chua co thi khoi tao
        Cart cart = cartRepository.findByUserId(user.getId())
        .orElseGet(() -> {
            Cart newCart = new Cart();
            newCart.setUser(user);
            return cartRepository.save(newCart);
        });

        // kiem tra product co trong cart chua
        Optional<CartItem> existingItem = cart.getItems().stream()
        .filter(item -> item.getProduct().getId().equals(product.getId()))
        .findFirst();

        if(existingItem.isPresent()){ // co thi tang so luong len
            CartItem item = existingItem.get();
            int oldQuantity = item.getQuantity();
            int newQuantity = oldQuantity + request.getQuantity();

            if(newQuantity > product.getStock()){
                log.warn("Cannot add to cart: Total quantity {} exceeds available stock {} for product '{}'",
                newQuantity, product.getStock(), product.getName());
                throw new IllegalArgumentException("Cannot add more. Total in cart exceeds available stock");
            }
            item.setQuantity(newQuantity);

            log.info("Updated quantity for product {} in user '{}' cart: {} -> {}",
            product.getId(), userEmail, oldQuantity, newQuantity);
            
        } else { // chua co thi tao item moi va them vao items cua cart
            CartItem newItem = new CartItem();
            newItem.setCart(cart);
            newItem.setProduct(product);
            newItem.setQuantity(request.getQuantity());
            cart.getItems().add(newItem);
            log.info("Added new item (productId={}) to cart of user '{}'", product.getId(), userEmail);
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


}
