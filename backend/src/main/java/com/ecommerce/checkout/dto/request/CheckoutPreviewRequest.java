package com.ecommerce.checkout.dto.request;

import java.util.List;

public record CheckoutPreviewRequest(List<Long> cartItemIds, String voucherCode) {}
