package com.ecommerce.payment.controller;

import com.ecommerce.payment.config.VnpayConfig;
import com.ecommerce.payment.service.VnpayIpnService;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.*;

@RestController
@RequestMapping("/api/v1/payments/vnpay")
@RequiredArgsConstructor
public class VnpayPaymentController {
    private final VnpayIpnService ipn;
    private final VnpayConfig config;
    public record Ack(@JsonProperty("RspCode") String code, @JsonProperty("Message") String message) {}

    @GetMapping("/ipn")
    public ResponseEntity<Ack> notify(HttpServletRequest request) {
        String code;
        try {
            // The transactional proxy commits before this call returns; commit failures enter catch.
            code = ipn.handle(fields(request));
        } catch (IllegalArgumentException e) { code = "97";
        } catch (Exception e) { code = "99"; }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Ack(code, switch(code) {
            case "00" -> "Recorded"; case "02" -> "Already recorded"; case "01" -> "Reference not found";
            case "04" -> "Amount or payment mismatch"; case "97" -> "Invalid request"; default -> "Processing error";
        }));
    }
    @GetMapping("/return")
    public ResponseEntity<Void> returnToWebsite(HttpServletRequest request) {
        Long orderId = null;
        try { orderId = ipn.returnOrderId(fields(request)); } catch (Exception ignored) { /* Never trust Return for payment state. */ }
        String origin = config.getFrontendOrigin().replaceAll("/$", "");
        return ResponseEntity.status(HttpStatus.SEE_OTHER).cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer")
                .location(URI.create(origin + "/payment-result?" + (orderId == null ? "invalidReturn=1" : "orderId=" + orderId))).build();
    }
    private Map<String,String> fields(HttpServletRequest request) {
        var source = request.getParameterMap();
        if (source.size() > 32 || (request.getQueryString() != null && request.getQueryString().length() > 8192))
            throw new IllegalArgumentException();
        Map<String,String> result = new HashMap<>();
        source.forEach((key, values) -> {
            if (!key.matches("vnp_[A-Za-z0-9]+") || values.length != 1 || values[0].length() > 2048)
                throw new IllegalArgumentException();
            result.put(key, values[0]);
        });
        return result;
    }
}
