package com.ecommerce.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ecommerce.dto.request.MomoIpnRequest;
import com.ecommerce.dto.response.PaymentResponse;
import com.ecommerce.entity.Payment;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.mapper.PaymentMapper;
import com.ecommerce.repository.PaymentRepository;
import com.ecommerce.service.MomoIpnService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final MomoIpnService momoIpnService;
    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;

    // IPN Callback — MoMo server gọi vào đây (server-to-server)
    // Ko cần JWT — MoMo server ko có token
    // Bảo vệ bằng verify HMAC signature bên trong service
    @PostMapping("/momo/ipn")
    public ResponseEntity<Void> handleMomoIpn(
        @RequestBody MomoIpnRequest request){
        momoIpnService.handleIpn(request);
        return ResponseEntity.ok().build();
    }

    // Return URL — Browser redirect sau khi user thanh toán xong trên MoMo
    // Ko cần JWT — đây là browser redirect
    // Chỉ dùng để hiển thị kết quả cho user, logic thực đã xử lý ở IPN
    @GetMapping("/momo/return")
    public ResponseEntity<Map<String, Object>> handleMomoReturn(
        @ModelAttribute MomoIpnRequest request
    ){
        Map<String, Object> result = new HashMap<>();
        result.put("orderId", request.getOrderId());
        result.put("resultCode", request.getResultCode());
        result.put("message", request.getMessage());
        result.put("success", request.getResultCode() != null && request.getResultCode() == 0);
        return ResponseEntity.ok(result);
    }

    // Xem trạng thái payment theo orderId
    // Yêu cầu authentication (user đăng nhập)
    @GetMapping("/api/oders/{orderId}")
    public ResponseEntity<PaymentResponse> getPaymentByOrderId(
        @AuthenticationPrincipal UserDetails userDetails,
        @PathVariable Long orderId
    ){
        Payment payment = paymentRepository.findByOrderId(orderId)
        .orElseThrow(() -> new ResourceNotFoundException("Payment", orderId));
        
        return ResponseEntity.ok(paymentMapper.toPaymentResponse(payment));
    }
}
