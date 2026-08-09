package com.ecommerce.config;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.ecommerce.annotation.Idempotent;
import com.ecommerce.entity.IdempotencyKey;
import com.ecommerce.repository.IdempotencyKeyRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyInterceptor implements HandlerInterceptor{

    private final IdempotencyKeyRepository idempotencyKeyRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception{
        
        // ko phai goi vao 1 ham controller thi bo qua
        if(!(handler instanceof HandlerMethod)){
            return true;
        }

        HandlerMethod handlerMethod = (HandlerMethod) handler;

        // kiem tra ham controller co dan tem @Idempotency ko
        Idempotent idempotent = handlerMethod.getMethodAnnotation(Idempotent.class);
        if( idempotent == null){
            return true; // ko co tem di qua binh thuong
        }

        // neu co tem bat buoc phai co header "Idempotency-Key"
        String idempotencyKey = request.getHeader("Idempotency-Key");
        if(idempotencyKey == null || idempotencyKey.trim().isEmpty()){
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing Idempotency-Key header");
            return false; // chan lai
        }

        // kiem tra key nay da duoc gui len chua
        try {
            if(idempotencyKeyRepository.existsById(idempotencyKey)){
                log.warn("Idempotency-Key {} already exists. Blocking duplicate request", idempotencyKey);

                response.sendError(HttpServletResponse.SC_CONFLICT, "Request is already processing or completed");
                return false; // ton tai thi chan tao trung don
            }
            
            // neu chua co thi luu vao database va danh dau 
            IdempotencyKey keyEntity = new IdempotencyKey(idempotencyKey, LocalDateTime.now());
            idempotencyKeyRepository.save(keyEntity);

            return true; // cho phep vao controller tao don
            
        } catch (Exception e) {
            // 2 truong hop gui request cung luc (race condition)
            // database nem loi trung primary key

            log.warn("Concurrent requests detected for key {}. Blocking duplicate.", idempotencyKey);
            response.sendError(HttpServletResponse.SC_CONFLICT, "Concurrent request detected");
            return false;
        }
    }
}
