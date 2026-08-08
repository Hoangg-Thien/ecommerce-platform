package com.ecommerce.service;

import java.nio.charset.StandardCharsets;
import java.util.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.ecommerce.config.MomoConfig;
import com.ecommerce.dto.request.MomoIpnRequest;
import com.ecommerce.entity.Payment;
import com.ecommerce.exception.PaymentException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class MomoServiceImpl implements MomoService {

    private final MomoConfig momoConfig;
    private final RestTemplate restTemplate;

    @Override
    public String createPaymentUrl(Payment payment){

        // Tạo requestId và orderId duy nhất cho mỗi lần thanh toán
        String requestId = UUID.randomUUID().toString();

        // orderId MoMo: kết hợp orderId hệ thống + timestamp để đảm bảo unique
        String momoOrderId = "ORDER_" + payment.getOrder().getId() + "_" + System.currentTimeMillis();
        String orderInfo = "Thanh toan don hang #" + payment.getOrder().getId();
        String extraData = "";
        String requestType = "payWithMethod";
        String amount = payment.getAmount().toBigInteger().toString(); // MoMo chỉ nhận số nguyên (VND)

        // Lưu lại vào payment để sau này tìm bằng momoOrderId trong IPN callback
        payment.setMomoOrderId(momoOrderId);
        payment.setMomoRequestId(requestId);

        // ===== BƯỚC 1: Tạo chữ ký =====
        String rawHasForCreate = "accessKey=" + momoConfig.getAccessKey()
        + "&amount=" + amount
        + "&extraData=" + extraData
        + "&ipnUrl=" + momoConfig.getIpnUrl()
        + "&orderId=" + momoOrderId
        + "&orderInfo=" + orderInfo
        + "&partnerCode=" + momoConfig.getPartnerCode()
        + "&redirectUrl=" + momoConfig.getRedirectUrl()
        + "&requestId=" + requestId
        + "&requestType=" + requestType;

        String signature = hmacSHA256(rawHasForCreate,momoConfig.getSecretKey());

        // ===== BƯỚC 2: Gửi request lên MoMo =====
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("partnerCode", momoConfig.getPartnerCode());
        requestBody.put("requestId", requestId);
        requestBody.put("amount", Long.parseLong(amount));
        requestBody.put("orderId", momoOrderId);
        requestBody.put("orderInfo", orderInfo);
        requestBody.put("redirectUrl", momoConfig.getRedirectUrl());
        requestBody.put("ipnUrl", momoConfig.getIpnUrl());
        requestBody.put("requestType", requestType);
        requestBody.put("extraData", extraData);
        requestBody.put("lang", "vi");
        requestBody.put("signature", signature);

        try {
            @SuppressWarnings("rawtypes")
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    momoConfig.getApiUrl(), requestBody, Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = response.getBody();
            if (body != null && body.containsKey("payUrl")) {
                log.info("Created MoMo payment URL for order {}", payment.getOrder().getId());
                return (String) body.get("payUrl");
            }
            log.error("MoMo response missing payUrl: {}", body);
            throw new PaymentException("MoMo did not return a payment URL");
        } catch (PaymentException e) {
            throw e; // re-throw không wrap
        } catch (Exception e) {
            log.error("Failed to create MoMo payment for order {}", payment.getOrder().getId(), e);
            throw new PaymentException("Cannot connect to MoMo payment gateway: " + e.getMessage());
        }
    }

    @Override
    public boolean verifySignature(MomoIpnRequest request){
        // verify IPN
        String rawHash = "accessKey=" + momoConfig.getAccessKey()
            + "&amount=" + request.getAmount().toBigInteger()
            + "&extraData=" + request.getExtraData()
            + "&message=" + request.getMessage()
            + "&orderId=" + request.getOrderId()
            + "&orderInfo=" + request.getOrderInfo()
            + "&orderType=" + request.getOrderType()
            + "&partnerCode=" + request.getPartnerCode()
            + "&payType=" + request.getPayType()
            + "&requestId=" + request.getRequestId()
            + "&responseTime=" + request.getResponseTime()
            + "&resultCode=" + request.getResultCode()
            + "&transId=" + request.getTransId();
        String computed = hmacSHA256(rawHash, momoConfig.getSecretKey());
        return computed.equals(request.getSignature());
    }

      // ===== Tính HMAC-SHA256 =====
    private String hmacSHA256(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            // Convert bytes → hex string
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Error computing HMAC-SHA256", e);
        }
    }
}
