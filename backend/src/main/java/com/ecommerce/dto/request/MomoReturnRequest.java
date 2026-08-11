package com.ecommerce.dto.request;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

// MoMo redirect browser về URL dạng:
// /api/payments/momo/return?orderId=...&resultCode=0&signature=...
@Getter
@Setter
public class MomoReturnRequest {
    private String partnerCode;
    private String orderId;
    private String requestId;
    private BigDecimal amount;
    private String orderInfo;
    private String orderType;
    private String transId;
    private Integer resultCode;
    private String message;
    private String payType;
    private Long responseTime;
    private String extraData;
    private String signature;
}
