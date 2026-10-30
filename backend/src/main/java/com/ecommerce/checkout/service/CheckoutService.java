package com.ecommerce.checkout.service;

import com.ecommerce.cart.entity.Cart;
import com.ecommerce.cart.entity.CartItem;
import com.ecommerce.cart.repository.CartRepository;
import com.ecommerce.checkout.dto.request.CheckoutRequest;
import com.ecommerce.checkout.dto.request.CheckoutPreviewRequest;
import com.ecommerce.checkout.dto.response.CheckoutResponse;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.common.idempotency.IdempotencyKeyRepository;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderItem;
import com.ecommerce.order.dto.response.OrderResponse;
import com.ecommerce.order.mapper.OrderMapper;
import com.ecommerce.payment.enums.PaymentMethod;
import com.ecommerce.payment.strategy.PaymentStrategyFactory;
import com.ecommerce.promotion.enums.OrderDiscountSource;
import com.ecommerce.promotion.pricing.PricingCalculator.*;
import com.ecommerce.promotion.repository.VoucherRepository;
import com.ecommerce.promotion.service.PromotionPricingService;
import com.ecommerce.promotion.service.VoucherUsageService;
import com.ecommerce.user.entity.User;
import com.ecommerce.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CheckoutService {
    private final UserRepository userRepository;
    private final CartRepository cartRepository;
    private final PaymentStrategyFactory strategyFactory;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final ShippingFeeService shippingFeeService;
    private final PromotionPricingService pricing;
    private final VoucherUsageService voucherUsage;
    private final VoucherRepository vouchers;
    private final OrderMapper orderMapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Result preview(String email, CheckoutPreviewRequest request) {
        User user = findUser(email);
        Cart cart = findCart(user);
        return calculate(user, selectItems(cart, request.cartItemIds()), request.voucherCode());
    }

    @Transactional(readOnly = true)
    public OrderResponse findResult(String email, String key) {
        validateKey(key);
        return orderMapper.tOrderResponse(idempotencyKeyRepository.findResultForOwner(key, email)
                .orElseThrow(() -> new ResourceNotFoundException("Checkout result not found")));
    }

    @Transactional
    public CheckoutResponse checkout(String email, CheckoutRequest request, String key) {
        validateKey(key);
        if (idempotencyKeyRepository.insertIfAbsent(key, LocalDateTime.now(clock)) != 1) {
            throw new IllegalStateException("Request is already processing or completed");
        }
        User user = findUser(email);
        Cart cart = findCart(user);
        List<CartItem> selected = selectItems(cart, request.getCartItemIds());
        Result quote = calculate(user, selected, request.getVoucherCode());
        if (request.getExpectedTotal() == null || request.getExpectedTotal().signum() < 0
                || request.getExpectedTotal().stripTrailingZeros().scale() > 0) {
            throw new CheckoutPricingException("INVALID_EXPECTED_TOTAL",400,"Expected total must be integer VND",null);
        }
        if (quote.voucher() != null && quote.voucher().reason() != null
                && quote.voucher().reason() != VoucherReason.NOT_BETTER_THAN_ORDER_DISCOUNT) {
            var reason = quote.voucher().reason();
            throw new CheckoutPricingException("VOUCHER_" + reason.name(),reason == VoucherReason.SOLD_OUT ? 409 : 400,
                    "Voucher is not valid: " + reason,quote);
        }
        if (request.getExpectedTotal().compareTo(quote.total()) != 0) {
            throw new CheckoutPricingException("PRICE_CHANGED",409,"Giá đã thay đổi. Vui lòng xác nhận lại.",quote);
        }
        if (request.getPaymentMethod() == null) throw new IllegalArgumentException("Payment method is required");
        if (request.getPaymentMethod() == PaymentMethod.MOMO && quote.total().signum() == 0) {
            throw new CheckoutPricingException("ZERO_TOTAL_MOMO",400,"Đơn 0 đồng chỉ hỗ trợ COD.",quote);
        }

        Order order = new Order();
        order.setUser(user);
        order.setPaymentMethod(request.getPaymentMethod());
        order.setCustomerName(request.getLastName() + " " + request.getFirstName());
        order.setCustomerPhone(request.getPhone());
        order.setShippingAddress(request.getAddress());
        order.setShippingCity(request.getCity());
        order.setShippingWard(request.getWard());
        order.setShippingMethod("standard");
        order.setShippingFee(quote.shippingFee());
        order.setSubtotalPrice(quote.subtotal());
        order.setDiscountAmount(quote.orderDiscount().amount());
        order.setOrderDiscountSource(quote.orderDiscount().source());
        order.setOrderDiscountPercent(quote.orderDiscount().source() == null ? null : quote.orderDiscount().percent());
        order.setOrderDiscountId(quote.orderDiscount().source() == OrderDiscountSource.ORDER_DISCOUNT ? quote.orderDiscount().id() : null);
        order.setVoucherCode(quote.orderDiscount().source() == OrderDiscountSource.VOUCHER ? quote.orderDiscount().code() : null);
        order.setTotalPrice(quote.total());
        for (int i=0;i<selected.size();i++) {
            CartItem cartItem = selected.get(i);
            PricedItem priced = quote.items().get(i);
            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setProduct(cartItem.getProductVariant().getProduct());
            item.setSize(cartItem.getProductVariant().getSize());
            item.setQuantity(priced.quantity());
            item.setPrice(priced.unitPrice());
            item.setOriginalPrice(priced.originalUnitPrice());
            item.setProductDiscountPercent(priced.productDiscountPercent());
            item.setProductDiscountProgramId(priced.productDiscountProgramId());
            order.getItems().add(item);
        }
        CheckoutResponse response = strategyFactory.getPaymentStrategy(request.getPaymentMethod()).processPayment(order,cart);
        if (response == null || response.getOrderId() == null) throw new IllegalStateException("Payment strategy did not persist order");
        if (quote.orderDiscount().source() == OrderDiscountSource.VOUCHER) {
            voucherUsage.reserve(vouchers.getReferenceById(quote.orderDiscount().id()),user.getId(),
                    response.getOrderId(),quote.orderDiscount().amount());
        }
        if (idempotencyKeyRepository.linkOrder(key,response.getOrderId()) != 1) throw new IllegalStateException("Cannot link checkout result");
        return response;
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("User","email",email));
    }
    private Cart findCart(User user) {
        return cartRepository.findByUserId(user.getId()).orElseThrow(() -> new ResourceNotFoundException("Cart not found"));
    }
    private List<CartItem> selectItems(Cart cart, List<Long> ids) {
        List<CartItem> selected = cart.getItems();
        if (ids != null && !ids.isEmpty()) {
            Set<Long> wanted = new HashSet<>(ids);
            Set<Long> available = new HashSet<>();
            cart.getItems().forEach(item -> available.add(item.getId()));
            if (wanted.contains(null) || !available.containsAll(wanted))
                throw new CheckoutPricingException("INVALID_CART_ITEMS",400,"Cart items do not belong to this cart",null);
            selected = cart.getItems().stream().filter(item -> wanted.contains(item.getId())).toList();
        }
        if (selected.isEmpty()) throw new CheckoutPricingException("EMPTY_CHECKOUT",400,"Cannot checkout from an empty cart",null);
        for (CartItem item:selected) {
            if (item.getQuantity() == null || item.getQuantity() <= 0) throw new IllegalArgumentException("Invalid quantity");
            if (item.getProductVariant().getStock() < item.getQuantity())
                throw new IllegalArgumentException("Not enough stock for product: " + item.getProductVariant().getProduct().getName());
        }
        return selected;
    }
    private Result calculate(User user, List<CartItem> selected, String voucherCode) {
        return pricing.price(selected.stream().map(item -> new Item(item.getId(),
                item.getProductVariant().getProduct().getId(),item.getProductVariant().getProduct().getPrice(),item.getQuantity()))
                .toList(),user.getId(),voucherCode,shippingFeeService.getFlatFee());
    }
    private void validateKey(String key) {
        if (key == null || key.isBlank() || key.length() > 255) throw new IllegalArgumentException("Invalid Idempotency-Key");
    }
}
