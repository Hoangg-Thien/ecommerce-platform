package com.ecommerce.checkout.dto.request;

import java.math.BigDecimal;
import java.util.List;

import com.ecommerce.payment.enums.PaymentMethod;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
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
    /** @deprecated Ignored; shipping is calculated from backend configuration. */
    @Deprecated
    private BigDecimal shippingFee;

    @NotNull(message = "Payment method is required")
    private PaymentMethod paymentMethod = PaymentMethod.COD; // default COD

    private List<Long> cartItemIds;

    private String voucherCode;

    @NotNull
    @DecimalMin("0")
    @Digits(integer = 36, fraction = 0)
    @JsonDeserialize(using = VndAmountDeserializer.class)
    private BigDecimal expectedTotal;
}
