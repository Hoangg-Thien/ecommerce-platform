package com.ecommerce.promotion.service;

import com.ecommerce.common.dto.PageResponse;
import com.ecommerce.common.exception.DuplicateResourceException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.product.repository.ProductRepository;
import com.ecommerce.promotion.dto.request.PromotionRequests;
import com.ecommerce.promotion.dto.response.PromotionResponses.*;
import com.ecommerce.promotion.entity.*;
import com.ecommerce.promotion.enums.PromotionStatus;
import com.ecommerce.promotion.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminPromotionService {
    private final PromotionProgramRepository programs;
    private final VoucherRepository vouchers;
    private final VoucherUsageRepository usages;
    private final OrderDiscountRepository orderDiscounts;
    private final ProductDiscountRepository productDiscounts;
    private final ProductRepository products;
    private final Clock clock;

    @Transactional(readOnly=true)
    public PageResponse<Program> list(Pageable pageable) {
        var now=clock.instant();
        return PageResponse.of(programs.findAll(pageable).map(p -> Program.from(p,now)));
    }
    @Transactional(readOnly=true)
    public Detail detail(Long id) {
        var p=Program.from(programs.findById(id).orElseThrow(() -> missing("Program")),clock.instant());
        return new Detail(p.id(),p.name(),p.description(),p.startDate(),p.endDate(),p.status(),p.phase(),
                vouchers.findByProgramId(id).stream().map(VoucherView::from).toList(),
                orderDiscounts.findByProgramId(id).stream().map(OrderDiscountView::from).toList(),
                productDiscounts.findByProgramId(id).stream().map(ProductDiscountView::from).toList());
    }
    public Program create(PromotionRequests.Program request) {
        var p=new PromotionProgram(); apply(p,request);
        return Program.from(programs.save(p),clock.instant());
    }
    public Program update(Long id,PromotionRequests.Program request) {
        var p=lockProgram(id); apply(p,request);
        return Program.from(p,clock.instant());
    }
    public Program status(Long id,PromotionStatus status) {
        var p=lockProgram(id); p.setStatus(status); return Program.from(p,clock.instant());
    }
    public void delete(Long id) {
        var p=lockProgram(id);
        if(programs.hasHistory(id)) throw history();
        vouchers.deleteAll(vouchers.findByProgramId(id));
        orderDiscounts.deleteAll(orderDiscounts.findByProgramId(id));
        productDiscounts.deleteAll(productDiscounts.findByProgramId(id));
        programs.delete(p);
        programs.flush(); // FK constraints are the final guard against concurrent checkout.
    }
    public VoucherView createVoucher(Long programId,PromotionRequests.Voucher r) {
        var v=new Voucher(); v.setProgram(lockProgram(programId)); apply(v,r);
        return VoucherView.from(vouchers.saveAndFlush(v));
    }
    public VoucherView updateVoucher(Long programId,Long id,PromotionRequests.Voucher r) {
        lockProgram(programId);
        var v=lockedVoucher(programId,id);
        if(r.quantity()<v.getUsedQuantity()) throw new IllegalArgumentException("Quantity cannot be below used quantity");
        apply(v,r); vouchers.flush(); return VoucherView.from(v);
    }
    public VoucherView voucherStatus(Long programId,Long id,PromotionStatus status) {
        lockProgram(programId); var v=lockedVoucher(programId,id); v.setStatus(status); return VoucherView.from(v);
    }
    public void deleteVoucher(Long programId,Long id) {
        lockProgram(programId); var v=lockedVoucher(programId,id);
        if(usages.existsByVoucherId(id)) throw history();
        vouchers.delete(v); vouchers.flush();
    }
    public OrderDiscountView createOrderDiscount(Long programId,PromotionRequests.OrderDiscount r) {
        var d=new OrderDiscount(); d.setProgram(lockProgram(programId)); apply(d,r);
        return OrderDiscountView.from(orderDiscounts.saveAndFlush(d));
    }
    public OrderDiscountView updateOrderDiscount(Long programId,Long id,PromotionRequests.OrderDiscount r) {
        lockProgram(programId); var d=orderDiscount(programId,id); apply(d,r);
        orderDiscounts.flush(); return OrderDiscountView.from(d);
    }
    public void deleteOrderDiscount(Long programId,Long id) {
        lockProgram(programId); var d=orderDiscount(programId,id);
        if(orderDiscounts.hasOrderHistory(id)) throw history();
        orderDiscounts.delete(d); orderDiscounts.flush();
    }
    public List<ProductDiscountView> createProductDiscounts(Long programId,PromotionRequests.ProductDiscounts r) {
        var p=lockProgram(programId);
        var ids=new HashSet<>(r.productIds());
        if(ids.size()!=r.productIds().size()) throw new IllegalArgumentException("Duplicate product IDs");
        var selected=products.findAllById(ids);
        if(selected.size()!=ids.size()) throw missing("Product");
        var rules=selected.stream().map(product -> {
            var d=new ProductDiscount(); d.setProgram(p); d.setProduct(product); d.setDiscountPercent(r.discountPercent()); return d;
        }).toList();
        return productDiscounts.saveAllAndFlush(rules).stream().map(ProductDiscountView::from).toList();
    }
    public ProductDiscountView updateProductDiscount(Long programId,Long id,PromotionRequests.ProductDiscount r) {
        lockProgram(programId); var d=productDiscount(programId,id); d.setDiscountPercent(r.discountPercent());
        return ProductDiscountView.from(d);
    }
    public void deleteProductDiscount(Long programId,Long id) {
        lockProgram(programId); var d=productDiscount(programId,id);
        if(productDiscounts.hasOrderHistory(programId,d.getProduct().getId())) throw history();
        productDiscounts.delete(d); productDiscounts.flush();
    }
    private PromotionProgram lockProgram(Long id) { return programs.findForUpdate(id).orElseThrow(() -> missing("Program")); }
    private Voucher lockedVoucher(Long programId,Long id) {
        // Lock before loading the entity so quota is checked against the committed counter.
        vouchers.lockId(id).orElseThrow(() -> missing("Voucher"));
        var v=vouchers.findById(id).orElseThrow(() -> missing("Voucher"));
        owner(programId,v.getProgram()); return v;
    }
    private OrderDiscount orderDiscount(Long programId,Long id) {
        var d=orderDiscounts.findById(id).orElseThrow(() -> missing("Order discount")); owner(programId,d.getProgram()); return d;
    }
    private ProductDiscount productDiscount(Long programId,Long id) {
        var d=productDiscounts.findById(id).orElseThrow(() -> missing("Product discount")); owner(programId,d.getProgram()); return d;
    }
    private void owner(Long id,PromotionProgram p) { if(!id.equals(p.getId())) throw missing("Promotion child"); }
    private static ResourceNotFoundException missing(String name) { return new ResourceNotFoundException(name+" not found"); }
    private static DuplicateResourceException history() { return new DuplicateResourceException("Promotion has order or usage history; deactivate it instead"); }
    private static void apply(PromotionProgram p,PromotionRequests.Program r) {
        if(!r.endDate().isAfter(r.startDate())) throw new IllegalArgumentException("endDate must be after startDate");
        p.setName(r.name().trim()); p.setDescription(r.description()); p.setStartDate(r.startDate()); p.setEndDate(r.endDate());
    }
    private static void apply(Voucher v,PromotionRequests.Voucher r) {
        v.setCode(r.code()); v.setDiscountPercent(r.discountPercent()); v.setMinimumAmount(r.minimumAmount());
        v.setMaximumDiscount(r.maximumDiscount()); v.setQuantity(r.quantity());
    }
    private static void apply(OrderDiscount d,PromotionRequests.OrderDiscount r) {
        d.setMinimumAmount(r.minimumAmount()); d.setDiscountPercent(r.discountPercent()); d.setMaximumDiscount(r.maximumDiscount());
    }
}
