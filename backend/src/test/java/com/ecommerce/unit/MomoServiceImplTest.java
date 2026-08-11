package com.ecommerce.unit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import com.ecommerce.config.MomoConfig;
import com.ecommerce.dto.request.MomoIpnRequest;
import com.ecommerce.entity.*;
import com.ecommerce.exception.PaymentException;
import com.ecommerce.service.MomoServiceImpl;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MomoServiceImplTest {

    @Mock private MomoConfig momoConfig;
    @Mock private RestTemplate restTemplate;

    @InjectMocks
    private MomoServiceImpl momoService;

    // Secret key dùng để tính HMAC trong test (phải khớp với mock momoConfig)
    private static final String TEST_SECRET_KEY  = "K951B6PE1waDMi640xX08PD3vg6EkVlz";
    private static final String TEST_ACCESS_KEY  = "F8BBA842ECF85";
    private static final String TEST_PARTNER_CODE = "MOMO_TEST";
    private static final String TEST_IPN_URL     = "http://localhost:8080/api/v1/payments/momo/ipn";
    private static final String TEST_REDIRECT_URL = "http://localhost:8080/api/v1/payments/momo/return";
    private static final String TEST_API_URL     = "https://test-payment.momo.vn/v2/gateway/api/create";

    @BeforeEach
    void setUp() {
        when(momoConfig.getSecretKey()).thenReturn(TEST_SECRET_KEY);
        when(momoConfig.getAccessKey()).thenReturn(TEST_ACCESS_KEY);
        when(momoConfig.getPartnerCode()).thenReturn(TEST_PARTNER_CODE);
        when(momoConfig.getIpnUrl()).thenReturn(TEST_IPN_URL);
        when(momoConfig.getRedirectUrl()).thenReturn(TEST_REDIRECT_URL);
        when(momoConfig.getApiUrl()).thenReturn(TEST_API_URL);
    }

    // ====== verifySignature tests ======

    @Test
    void verifySignature_WithValidSignature_ShouldReturnTrue() {
        MomoIpnRequest request = buildIpnRequest();
        // Tính signature hợp lệ bằng cùng key
        String validSignature = computeExpectedIpnSignature(request);
        request.setSignature(validSignature);

        boolean result = momoService.verifySignature(request);

        assertTrue(result);
    }

    @Test
    void verifySignature_WithInvalidSignature_ShouldReturnFalse() {
        MomoIpnRequest request = buildIpnRequest();
        request.setSignature("totally_wrong_signature_abc123");

        boolean result = momoService.verifySignature(request);

        assertFalse(result);
    }

    @Test
    void verifySignature_WithTamperedAmount_ShouldReturnFalse() {
        MomoIpnRequest request = buildIpnRequest();
        String validSignature = computeExpectedIpnSignature(request);
        request.setSignature(validSignature);
        // Giả lập kẻ tấn công thay đổi amount sau khi signature đã tính
        request.setAmount(BigDecimal.valueOf(1)); // tamper!

        boolean result = momoService.verifySignature(request);

        assertFalse(result);
    }

    // ====== createPaymentUrl tests ======

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void createPaymentUrl_WhenMomoReturnsPayUrl_ShouldReturnUrl() {
        Payment payment = buildPayment();
        Map<String, Object> momoResponse = Map.of("payUrl", "https://payment.momo.vn/abc");
        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(momoResponse));

        String url = momoService.createPaymentUrl(payment);

        assertNotNull(url);
        assertEquals("https://payment.momo.vn/abc", url);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void createPaymentUrl_WhenMomoResponseMissingPayUrl_ShouldThrowPaymentException() {
        Payment payment = buildPayment();
        Map<String, Object> momoResponse = Map.of("resultCode", 99, "message", "Error");
        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(momoResponse));

        assertThrows(PaymentException.class, () -> momoService.createPaymentUrl(payment));
    }

    @Test
    void createPaymentUrl_WhenNetworkError_ShouldThrowPaymentException() {
        Payment payment = buildPayment();
        when(restTemplate.postForEntity(anyString(), any(), any()))
                .thenThrow(new RuntimeException("Connection refused"));

        assertThrows(PaymentException.class, () -> momoService.createPaymentUrl(payment));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void createPaymentUrl_ShouldSetMomoOrderIdAndRequestIdOnPayment() {
        Payment payment = buildPayment();
        Map<String, Object> momoResponse = Map.of("payUrl", "https://payment.momo.vn/abc");
        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(momoResponse));

        momoService.createPaymentUrl(payment);

        // createPaymentUrl() phải set momoOrderId và momoRequestId vào payment
        assertNotNull(payment.getMomoOrderId());
        assertNotNull(payment.getMomoRequestId());
    }

    // ====== Helpers ======

    private Payment buildPayment() {
        User user = new User();
        user.setId(1L);

        Order order = new Order();
        order.setId(42L);
        order.setUser(user);
        order.setTotalPrice(BigDecimal.valueOf(100000));

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setAmount(BigDecimal.valueOf(100000));
        return payment;
    }

    private MomoIpnRequest buildIpnRequest() {
        MomoIpnRequest req = new MomoIpnRequest();
        req.setPartnerCode(TEST_PARTNER_CODE);
        req.setOrderId("ORDER_1_1234567890");
        req.setRequestId("req-uuid-001");
        req.setAmount(BigDecimal.valueOf(100000));
        req.setOrderInfo("Thanh toan don hang #1");
        req.setOrderType("momo_wallet");
        req.setTransId("9876543210");
        req.setResultCode(0);
        req.setMessage("Successful.");
        req.setPayType("qr");
        req.setResponseTime(1722955200000L);
        req.setExtraData("");
        req.setSignature("placeholder"); // sẽ bị override trong từng test
        return req;
    }

    // Tính lại signature đúng theo logic trong MomoServiceImpl.verifySignature()
    private String computeExpectedIpnSignature(MomoIpnRequest req) {
        String rawHash = "accessKey=" + TEST_ACCESS_KEY
                + "&amount=" + req.getAmount().toBigInteger()
                + "&extraData=" + req.getExtraData()
                + "&message=" + req.getMessage()
                + "&orderId=" + req.getOrderId()
                + "&orderInfo=" + req.getOrderInfo()
                + "&orderType=" + req.getOrderType()
                + "&partnerCode=" + req.getPartnerCode()
                + "&payType=" + req.getPayType()
                + "&requestId=" + req.getRequestId()
                + "&responseTime=" + req.getResponseTime()
                + "&resultCode=" + req.getResultCode()
                + "&transId=" + req.getTransId();
        return hmacSHA256(rawHash, TEST_SECRET_KEY);
    }

    private String hmacSHA256(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
