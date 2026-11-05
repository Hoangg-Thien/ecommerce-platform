package com.ecommerce.payment.service;

import com.ecommerce.payment.config.VnpayConfig;
import com.ecommerce.payment.entity.Payment;
import com.ecommerce.payment.enums.*;
import com.ecommerce.order.enums.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VnpayService {
    public static final BigDecimal MINIMUM = new BigDecimal("5000");
    private final VnpayConfig config;
    private final Clock clock;

    public VnpayResult classify(String responseCode, String transactionStatus) {
        return VnpayResult.classify(responseCode, transactionStatus);
    }

    public static String minorAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(MINIMUM) < 0 || amount.stripTrailingZeros().scale() > 0)
            throw new IllegalArgumentException("VNPay requires integer VND of at least 5000");
        String minor = amount.movePointRight(2).toBigIntegerExact().toString();
        if (minor.length() > 12) throw new IllegalArgumentException("VNPay amount exceeds the supported range");
        return minor;
    }

    public String canonical(Map<String,String> fields) {
        return new TreeMap<>(fields).entrySet().stream()
                .filter(e -> e.getValue() != null && !e.getValue().isEmpty())
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue())).collect(Collectors.joining("&"));
    }
    private static String encode(String value) {
        if (!StandardCharsets.US_ASCII.newEncoder().canEncode(value)) throw new IllegalArgumentException("Non-ASCII VNPay data");
        return URLEncoder.encode(value, StandardCharsets.US_ASCII);
    }
    public String sign(Map<String,String> fields) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(config.getHashSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(canonical(fields).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("VNPay signing is unavailable");
        }
    }
    public boolean verify(Map<String,String> fields) {
        String hash = fields.get("vnp_SecureHash");
        if (hash == null || !hash.matches("[a-fA-F0-9]{128}")) return false;
        Map<String,String> signed = new HashMap<>(fields);
        signed.remove("vnp_SecureHash"); signed.remove("vnp_SecureHashType");
        return MessageDigest.isEqual(HexFormat.of().parseHex(hash), HexFormat.of().parseHex(sign(signed)));
    }
    public void createUrl(Payment payment, String ip) {
        if (!config.isEnabled()) throw new IllegalStateException("VNPay Sandbox is disabled");
        if (payment.getProviderTxnRef() != null) throw new IllegalStateException("Payment session already exists");
        if (ip == null || !ip.matches("[a-fA-F0-9:.]{3,45}")) throw new IllegalArgumentException("Invalid payment client IP");
        Instant created = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));
        String reference = UUID.randomUUID().toString().replace("-", "");
        var params = new TreeMap<String,String>();
        params.put("vnp_Version","2.1.0"); params.put("vnp_Command","pay");
        params.put("vnp_TmnCode",config.getTmnCode()); params.put("vnp_Amount",minorAmount(payment.getAmount()));
        params.put("vnp_CreateDate",format.format(created)); params.put("vnp_ExpireDate",format.format(created.plusSeconds(900)));
        params.put("vnp_CurrCode","VND"); params.put("vnp_Locale","vn"); params.put("vnp_IpAddr",ip);
        params.put("vnp_OrderInfo","Thanh toan don hang " + payment.getOrder().getId());
        params.put("vnp_OrderType","200000"); // Official category: fashion.
        params.put("vnp_ReturnUrl",config.getReturnUrl()); params.put("vnp_TxnRef",reference);
        payment.setProviderTxnRef(reference); payment.setExpiresAt(created.plusSeconds(900));
        payment.setProviderEnvironment("SANDBOX"); payment.setRefundStatus(RefundStatus.NONE);
        payment.setPaymentUrl(config.getPayUrl() + "?" + canonical(params) + "&vnp_SecureHash=" + sign(params));
    }
    public String retryUrl(Payment p) {
        if (p.getPaymentMethod() != PaymentMethod.VNPAY || !"SANDBOX".equals(p.getProviderEnvironment())
                || p.getOrder().getStatus() != OrderStatus.AWAITING_PAYMENT || p.getPaymentStatus() != PaymentStatus.PENDING
                || p.getPaidAt() != null || p.getExpiresAt() == null || !clock.instant().isBefore(p.getExpiresAt())
                || p.getPaymentUrl() == null || !p.getPaymentUrl().startsWith(VnpayConfig.SANDBOX_PAY_URL + "?"))
            throw new IllegalStateException("VNPay session is unavailable or expired");
        return p.getPaymentUrl();
    }
}
