package com.ecommerce.dto.request;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MomoIpnRequest {
    private String partnerCode;
    private String orderId;        // momoOrderId ta đã gửi lên MoMo
    private String requestId;      // momoRequestId ta đã gửi lên MoMo
    private BigDecimal amount;
    private String orderInfo;
    private String orderType;
    private String transId;        // mã giao dịch MoMo (dùng làm transactionId trong Payment)
    private Integer resultCode;    // 0 = thành công, khác 0 = thất bại
    private String message;        // mô tả kết quả
    private String payType;
    private Long responseTime;
    private String extraData;
    private String signature;      // HMAC-SHA256 từ MoMo, ta phải verify
}
