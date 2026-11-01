package com.ecommerce.promotion.controller;

import com.ecommerce.common.dto.PageResponse;
import com.ecommerce.promotion.dto.request.PromotionRequests;
import com.ecommerce.promotion.dto.response.PromotionResponses.*;
import com.ecommerce.promotion.service.AdminPromotionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** Protected by the existing ADMIN-only matcher for admin routes. */
@RestController
@RequestMapping("/api/v1/admin/promotions")
@RequiredArgsConstructor
public class AdminPromotionController {
    private final AdminPromotionService service;

    @GetMapping
    public PageResponse<Program> list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size) {
        return service.list(PageRequest.of(Math.max(0,page),size<=0?10:Math.min(size,100),Sort.by("id").descending()));
    }

    @GetMapping("/{id}")
    public Detail detail(@PathVariable Long id) {
        return service.detail(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Program create(@Valid @RequestBody PromotionRequests.Program r) {
        return service.create(r);
    }

    @PutMapping("/{id}")
    public Program update(@PathVariable Long id,@Valid @RequestBody PromotionRequests.Program r) {
        return service.update(id,r);
    }

    @PatchMapping("/{id}/status")
    public Program status(@PathVariable Long id,@Valid @RequestBody PromotionRequests.Status r) {
        return service.status(id,r.status());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @PostMapping("/{id}/vouchers")
    @ResponseStatus(HttpStatus.CREATED)
    public VoucherView createVoucher(@PathVariable Long id,@Valid @RequestBody PromotionRequests.Voucher r) {
        return service.createVoucher(id,r);
    }

    @PutMapping("/{id}/vouchers/{vid}")
    public VoucherView updateVoucher(@PathVariable Long id,@PathVariable Long vid,@Valid @RequestBody PromotionRequests.Voucher r) {
        return service.updateVoucher(id,vid,r);
    }

    @PatchMapping("/{id}/vouchers/{vid}/status")
    public VoucherView voucherStatus(@PathVariable Long id,@PathVariable Long vid,@Valid @RequestBody PromotionRequests.Status r) {
        return service.voucherStatus(id,vid,r.status());
    }

    @DeleteMapping("/{id}/vouchers/{vid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteVoucher(@PathVariable Long id,@PathVariable Long vid) {
        service.deleteVoucher(id,vid);
    }

    @PostMapping("/{id}/order-discounts")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDiscountView createOrderDiscount(@PathVariable Long id,@Valid @RequestBody PromotionRequests.OrderDiscount r) {
        return service.createOrderDiscount(id,r);
    }

    @PutMapping("/{id}/order-discounts/{oid}")
    public OrderDiscountView updateOrderDiscount(@PathVariable Long id,@PathVariable Long oid,@Valid @RequestBody PromotionRequests.OrderDiscount r) {
        return service.updateOrderDiscount(id,oid,r);
    }

    @DeleteMapping("/{id}/order-discounts/{oid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteOrderDiscount(@PathVariable Long id,@PathVariable Long oid) {
        service.deleteOrderDiscount(id,oid);
    }

    @PostMapping("/{id}/product-discounts")
    @ResponseStatus(HttpStatus.CREATED)
    public List<ProductDiscountView> createProductDiscounts(@PathVariable Long id,@Valid @RequestBody PromotionRequests.ProductDiscounts r) {
        return service.createProductDiscounts(id,r);
    }
    // pid identifies the product-discount rule returned by create/detail, not the catalog product.
    @PutMapping("/{id}/product-discounts/{pid}")
    public ProductDiscountView updateProductDiscount(@PathVariable Long id,@PathVariable Long pid,@Valid @RequestBody PromotionRequests.ProductDiscount r) {
        return service.updateProductDiscount(id,pid,r);
    }

    @DeleteMapping("/{id}/product-discounts/{pid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProductDiscount(@PathVariable Long id,@PathVariable Long pid) {
        service.deleteProductDiscount(id,pid);
    }
}
