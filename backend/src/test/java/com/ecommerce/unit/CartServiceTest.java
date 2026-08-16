package com.ecommerce.unit;

import com.ecommerce.service.CartService;
import com.ecommerce.dto.request.AddToCartRequest;
import com.ecommerce.dto.response.CartResponse;
import com.ecommerce.entity.Cart;
import com.ecommerce.entity.CartItem;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.ProductVariant;
import com.ecommerce.entity.User;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.mapper.CartMapper;
import com.ecommerce.repository.CartRepository;
import com.ecommerce.repository.ProductVariantRepository;
import com.ecommerce.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @Mock
    private UserRepository userRepository;

    @Spy
    private CartMapper cartMapper;

    @InjectMocks
    private CartService cartService;

    private User user;
    private Product product;
    private ProductVariant variant;
    private Cart cart;
    private AddToCartRequest request;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setEmail("user@gmail.com");

        product = new Product();
        product.setId(1L);
        product.setName("Laptop");
        product.setPrice(BigDecimal.valueOf(1000));
        
        variant = new ProductVariant();
        variant.setId(100L);
        variant.setProduct(product);
        variant.setSize("42");
        variant.setStock(10);

        cart = new Cart();
        cart.setId(1L);
        cart.setUser(user);
        cart.setItems(new ArrayList<>());

        request = new AddToCartRequest();
        request.setVariantId(100L);
        request.setQuantity(2);
    }

    @Test
    void addToCart_WhenProductNotInCart_ShouldAddNewItem() {
        when(userRepository.findByEmail("user@gmail.com")).thenReturn(Optional.of(user));
        when(productVariantRepository.findById(100L)).thenReturn(Optional.of(variant));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CartResponse result = cartService.addToCart("user@gmail.com", request);

        assertNotNull(result);
        assertEquals(1, result.getItems().size());
        assertEquals(2, result.getItems().get(0).getQuantity());
        verify(cartRepository, times(1)).save(any(Cart.class));
    }

    @Test
    void addToCart_WhenProductAlreadyInCart_ShouldIncreaseQuantity() {
        CartItem existingItem = new CartItem();
        existingItem.setId(10L);
        existingItem.setCart(cart);
        existingItem.setProductVariant(variant);
        existingItem.setQuantity(2);
        cart.getItems().add(existingItem);

        when(userRepository.findByEmail("user@gmail.com")).thenReturn(Optional.of(user));
        when(productVariantRepository.findById(100L)).thenReturn(Optional.of(variant));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        request.setQuantity(3);
        CartResponse result = cartService.addToCart("user@gmail.com", request);

        assertNotNull(result);
        assertEquals(1, result.getItems().size());
        assertEquals(5, result.getItems().get(0).getQuantity());
    }

    @Test
    void addToCart_WhenQuantityExceedsStock_ShouldThrowException() {
        request.setQuantity(15);

        when(userRepository.findByEmail("user@gmail.com")).thenReturn(Optional.of(user));
        when(productVariantRepository.findById(100L)).thenReturn(Optional.of(variant));

        assertThrows(IllegalArgumentException.class, () -> cartService.addToCart("user@gmail.com", request));
        verify(cartRepository, never()).save(any());
    }

    @Test
    void addToCart_WhenProductNotFound_ShouldThrowException() {
        when(userRepository.findByEmail("user@gmail.com")).thenReturn(Optional.of(user));
        when(productVariantRepository.findById(100L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> cartService.addToCart("user@gmail.com", request));
    }

    @Test
    void addToCart_WhenCartDoesNotExist_ShouldCreateNewCartAndAddItem() {
        when(userRepository.findByEmail("user@gmail.com")).thenReturn(Optional.of(user));
        when(productVariantRepository.findById(100L)).thenReturn(Optional.of(variant));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        CartResponse result = cartService.addToCart("user@gmail.com", request);
        
        assertNotNull(result);
        assertEquals(1, result.getItems().size());
        assertEquals(2, result.getItems().get(0).getQuantity());
        verify(cartRepository, atLeastOnce()).save(any(Cart.class));
    }

    @Test
    void addToCart_WhenAccumulatedQuantityExceedsStock_ShouldThrowException() {
        CartItem existingItem = new CartItem();
        existingItem.setId(10L);
        existingItem.setCart(cart);
        existingItem.setProductVariant(variant);
        existingItem.setQuantity(8);
        cart.getItems().add(existingItem);
        request.setQuantity(3); 
        when(userRepository.findByEmail("user@gmail.com")).thenReturn(Optional.of(user));
        when(productVariantRepository.findById(100L)).thenReturn(Optional.of(variant));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        
        assertThrows(IllegalArgumentException.class, () -> cartService.addToCart("user@gmail.com", request));
    }
    
    @Test
    void addToCart_WhenUserNotFound_ShouldThrowException() {
        when(userRepository.findByEmail("user@gmail.com")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> cartService.addToCart("user@gmail.com", request));
    }

    @Test
    void getCart_WhenCartExists_ShouldReturnCartResponse() {
        when(userRepository.findByEmail("user@gmail.com")).thenReturn(Optional.of(user));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));

        CartResponse result = cartService.getCart("user@gmail.com");

        assertNotNull(result);
        assertEquals(1L, result.getUserId());
        verify(cartRepository, times(1)).findByUserId(1L);
    }

    @Test
    void getCart_WhenCartDoesNotExist_ShouldCreateAndReturnEmptyCart() {
        when(userRepository.findByEmail("user@gmail.com")).thenReturn(Optional.of(user));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CartResponse result = cartService.getCart("user@gmail.com");

        assertNotNull(result);
        assertTrue(result.getItems().isEmpty());
        assertEquals(BigDecimal.ZERO, result.getTotalPrice());
    }

    @Test
    void removeCartItem_WhenItemExists_ShouldRemoveItemAndReturnUpdatedCart() {
        CartItem item = new CartItem();
        item.setId(10L);
        item.setCart(cart);
        item.setProductVariant(variant);
        item.setQuantity(2);
        cart.getItems().add(item);

        when(userRepository.findByEmail("user@gmail.com")).thenReturn(Optional.of(user));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CartResponse result = cartService.removeCartItem("user@gmail.com", 10L);

        assertNotNull(result);
        assertTrue(result.getItems().isEmpty());
        assertEquals(BigDecimal.ZERO, result.getTotalPrice());
        verify(cartRepository, times(1)).save(cart);
    }

    @Test
    void removeCartItem_WhenItemNotFoundInCart_ShouldThrowException() {
        when(userRepository.findByEmail("user@gmail.com")).thenReturn(Optional.of(user));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));

        assertThrows(ResourceNotFoundException.class, () -> cartService.removeCartItem("user@gmail.com", 999L));
        verify(cartRepository, never()).save(any());
    }
}
