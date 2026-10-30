package com.ecommerce.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.dao.DataIntegrityViolationException;


import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(com.ecommerce.checkout.service.CheckoutPricingException.class)
    public ResponseEntity<Map<String,Object>> handleCheckoutPricing(com.ecommerce.checkout.service.CheckoutPricingException ex) {
        Map<String,Object> body = new HashMap<>(buildError(HttpStatus.valueOf(ex.getStatus()),ex.getMessage()).getBody());
        body.put("code",ex.getCode());
        if (ex.getBreakdown()!=null) body.put("breakdown",com.ecommerce.checkout.dto.response.CheckoutPreviewResponse.from(ex.getBreakdown()));
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    @ExceptionHandler(com.ecommerce.promotion.service.VoucherSoldOutException.class)
    public ResponseEntity<Map<String,Object>> handleSoldOut(RuntimeException ex) {
        Map<String,Object> body = new HashMap<>(buildError(HttpStatus.CONFLICT,"Mã giảm giá đã hết lượt.").getBody());
        body.put("code","VOUCHER_SOLD_OUT");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String,Object>> handleUnreadable(Exception ex) {
        return buildError(HttpStatus.BAD_REQUEST,"Invalid request body");
    }

    @ExceptionHandler(org.springframework.web.bind.MissingRequestHeaderException.class)
    public ResponseEntity<Map<String,Object>> handleMissingHeader(Exception ex) {
        return buildError(HttpStatus.BAD_REQUEST,"Required request header is missing");
    }

    // 404 - resource not found
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex) {
        return buildError(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    // 400 - bad request(out of stock, the shopping cart is empty, the quantity is invalid, ....)
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, Object>> handleBadRequest(RuntimeException ex) {
        return buildError(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // 400 - validation errors (@Valid)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError err : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(err.getField(), err.getDefaultMessage());
        }
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", 400);
        body.put("error", "Validation Failed");
        body.put("messages", fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    // 401 - unauthorized
    @ExceptionHandler(org.springframework.security.authentication.BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials(org.springframework.security.authentication.BadCredentialsException ex) {
        return buildError(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }

    // 401 - Invalid or Expired Token (Access token / Refresh token)
    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidToken(InvalidTokenException ex){
        return buildError(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    // 403 - Forbidden Access (Unauthorized access to another person's resources)
    @ExceptionHandler(UnauthorizedAccessException.class)
    public ResponseEntity<Map<String, Object>> handleUnauthorizedAccess(UnauthorizedAccessException ex) {
        return buildError(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    // 402 Payment Required — error from payment gateway
    @ExceptionHandler(PaymentException.class)
    public ResponseEntity<Map<String, Object>> handlePaymentError(PaymentException ex) {
        return buildError(HttpStatus.PAYMENT_REQUIRED, ex.getMessage());
    }

    // 400 Bad Request — Invalid MoMo signature (fake callback)
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, Object>> handleSecurityError(SecurityException ex) {
        log.warn("Security violation: {}", ex.getMessage());
        return buildError(HttpStatus.BAD_REQUEST, "Invalid request signature");
    }

    // 409 - conflict
    @ExceptionHandler({EmailAlreadyExistsException.class, DuplicateResourceException.class})
    public ResponseEntity<Map<String, Object>> handleDuplicateResource(RuntimeException ex) {
        return buildError(HttpStatus.CONFLICT, ex.getMessage());
    }

    // 409 - Conflict (Database Constraint Violation)
    @ExceptionHandler(DataIntegrityViolationException.class)
        public ResponseEntity<Map<String, Object>> handleDataIntergrityViolation(RuntimeException ex){
            Throwable cause = ex;
            while (cause != null) {
                if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                        && "uq_voucher_usage_active_per_user".equals(violation.getConstraintName())) {
                    Map<String,Object> body = new HashMap<>(buildError(HttpStatus.CONFLICT,"Bạn đã sử dụng mã giảm giá này.").getBody());
                    body.put("code","VOUCHER_ALREADY_USED");
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
                }
                cause = cause.getCause();
            }
            log.warn("Database constraint violation caught globally: {}", ex.getMessage());
            return buildError(HttpStatus.CONFLICT, "The request was rejected due to duplicate data or a violation of system constraints");
        }

    // 409 - Concurrency Conflict (conflicts arising when multiple people place orders simultaneously)
    @ExceptionHandler({org.springframework.orm.ObjectOptimisticLockingFailureException.class,
    jakarta.persistence.OptimisticLockException.class})
    public ResponseEntity<Map<String, Object>> handleOptimisticLocking(Exception ex) {
        return buildError(
            HttpStatus.CONFLICT, 
            "The product is being purchased by another customer at the same time. Please try again!"
        );
    }

    // 500 - generic fallback
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Unhandled exception occurred", ex);
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred. Please try again later.");
    }

    private ResponseEntity<Map<String, Object>> buildError(HttpStatus status, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
