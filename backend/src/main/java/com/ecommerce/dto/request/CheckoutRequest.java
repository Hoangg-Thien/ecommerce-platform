package com.ecommerce.dto.request;

import java.util.List;

import com.ecommerce.enums.PaymentMethod;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CheckoutRequest {

    private String email;

    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    @NotBlank(message = "Address is required")
    private String address;

    @NotBlank(message = "City is required")
    private String city;

    @NotBlank(message = "Ward is required")
    private String ward;

    @NotBlank(message = "Phone is required")
    private String phone;

    private String shippingMethod;
    private java.math.BigDecimal shippingFee;

    @NotNull(message = "Payment method is required")
    private PaymentMethod paymentMethod = PaymentMethod.COD; // default COD

    private List<Long> cartItemIds;
}
