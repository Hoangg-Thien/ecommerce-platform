package com.ecommerce.payment.service;

import com.ecommerce.payment.config.VnpayConfig;
import com.ecommerce.payment.entity.Payment;
import com.ecommerce.payment.enums.*;
import com.ecommerce.payment.repository.PaymentRepository;
import com.ecommerce.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.math.BigInteger;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class VnpayIpnService {
    private final VnpayService gateway;
    private final VnpayConfig config;
    private final PaymentRepository payments;
    private final OrderRepository orders;
    private final PaymentFulfillmentService fulfillment;
    private final Clock clock;

    public boolean validEnvelope(Map<String,String> p) {
        if (!gateway.verify(p) || !config.getTmnCode().equals(p.get("vnp_TmnCode"))) return false;
        return matches(p,"vnp_TxnRef","[A-Za-z0-9]{1,100}") && matches(p,"vnp_Amount","[0-9]{1,12}")
                && matches(p,"vnp_ResponseCode","[0-9]{2}") && matches(p,"vnp_TransactionStatus","[0-9]{2}")
                && matches(p,"vnp_TransactionNo","[0-9]{1,15}") && matches(p,"vnp_BankCode","[A-Za-z0-9]{3,20}")
                && matches(p,"vnp_OrderInfo","[\\x20-\\x7E]{1,255}");
    }
    private boolean matches(Map<String,String> p,String key,String regex) {
        return p.get(key) != null && p.get(key).matches(regex);
    }
    private boolean paymentMatches(Payment payment, Map<String,String> p) {
        return payment.getPaymentMethod() == PaymentMethod.VNPAY && payment.getOrder().getPaymentMethod() == PaymentMethod.VNPAY
                && "SANDBOX".equals(payment.getProviderEnvironment()) && payment.getAmount() != null
                && payment.getAmount().compareTo(payment.getOrder().getTotalPrice()) == 0
                && new BigInteger(p.get("vnp_Amount")).equals(payment.getAmount().movePointRight(2).toBigIntegerExact());
    }
    @Transactional
    public String handle(Map<String,String> p) {
        if (!validEnvelope(p)) return "97";
        var orderId = payments.findOrderIdByProviderTxnRef(p.get("vnp_TxnRef"));
        if (orderId.isEmpty()) return "01";
        orders.lockRootById(orderId.get()).orElseThrow();
        Payment payment = payments.lockByOrderId(orderId.get()).orElseThrow();
        if (!p.get("vnp_TxnRef").equals(payment.getProviderTxnRef())) return "01";
        if (!paymentMatches(payment,p)) return "04";
        VnpayResult result = gateway.classify(p.get("vnp_ResponseCode"),p.get("vnp_TransactionStatus"));
        if (payment.getPaidAt() != null || payment.getPaymentStatus() == PaymentStatus.PAID) {
            if (result == VnpayResult.SUCCESS && p.get("vnp_TransactionNo").equals(payment.getProviderTransactionNo())) return "02";
            if (payment.getRefundStatus() != RefundStatus.REQUIRED_MANUAL) payment.setRefundStatus(RefundStatus.RECONCILIATION_REQUIRED);
            payment.setReason("CALLBACK_CONFLICT");
            return "00";
        }
        if (result == VnpayResult.SUCCESS && !matches(p,"vnp_TransactionNo","[1-9][0-9]{0,14}")) return "99";
        boolean repeatedResult = p.get("vnp_ResponseCode").equals(payment.getResponseCode())
                && p.get("vnp_TransactionStatus").equals(payment.getTransactionStatus())
                && p.get("vnp_TransactionNo").equals(payment.getProviderTransactionNo());
        if (repeatedResult && ((result == VnpayResult.FAILURE && payment.getPaymentStatus() == PaymentStatus.FAILED)
                || (result == VnpayResult.UNKNOWN && payment.getRefundStatus() != RefundStatus.NONE))) return "02";
        payment.setResponseCode(p.get("vnp_ResponseCode"));
        payment.setTransactionStatus(p.get("vnp_TransactionStatus"));
        payment.setProviderTransactionNo(p.get("vnp_TransactionNo"));
        if (result == VnpayResult.SUCCESS) {
            payment.setPaidAt(clock.instant()); payment.setPaymentStatus(PaymentStatus.PAID);
            fulfillment.processVnpaySuccess(payment);
        } else if (result == VnpayResult.FAILURE) {
            if (payment.getOrder().getStatus() == com.ecommerce.order.enums.OrderStatus.AWAITING_PAYMENT)
                fulfillment.processFailure(payment, "Verified definitive VNPay failure");
            if (payment.getOrder().getStatus() != com.ecommerce.order.enums.OrderStatus.CANCELLED)
                throw new IllegalStateException("Unproven VNPay order state; reconciliation required");
            payment.setPaymentStatus(PaymentStatus.FAILED);
            payment.setRefundStatus(RefundStatus.NONE);
            payment.setReason(null);
        } else {
            // No FAILURE pairs are approved: keep UNKNOWN distinct from gateway-confirmed failure.
            payment.setRefundStatus(RefundStatus.RECONCILIATION_REQUIRED);
            payment.setReason("VNPAY_RESULT_UNVERIFIED");
        }
        return "00";
    }
    @Transactional(readOnly=true)
    public Long returnOrderId(Map<String,String> p) {
        if (!validEnvelope(p)) return null;
        return payments.findByProviderTxnRef(p.get("vnp_TxnRef"))
                .filter(payment -> paymentMatches(payment,p)).map(payment -> payment.getOrder().getId()).orElse(null);
    }
}
