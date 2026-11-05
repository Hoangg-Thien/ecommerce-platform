package com.ecommerce.promotion.integration;

import com.ecommerce.product.service.ProductService;
import com.ecommerce.cart.service.CartService;
import com.ecommerce.promotion.repository.ProductDiscountRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:tc:postgresql:17.9:///product_cart_sale"})
class ProductCartSaleIntegrationTest {
    @Autowired ProductService products;
    @Autowired CartService carts;
    @Autowired JdbcTemplate jdbc;
    @Autowired CacheManager caches;
    @Autowired ObjectMapper json;
    @MockitoSpyBean Clock clock;
    @MockitoSpyBean ProductDiscountRepository discounts;
    final Instant start = Instant.parse("2030-01-01T00:00:00Z");

    @BeforeEach void seed() {
        caches.getCache("product").clear();
        doReturn(start).when(clock).instant();
        jdbc.execute("TRUNCATE idempotency_keys,voucher_usages,vouchers,product_discounts,order_discounts,promotion_programs,order_items,payments,orders,cart_items,carts,product_variants,products,refresh_tokens,users RESTART IDENTITY CASCADE");
        jdbc.update("INSERT INTO users(id,email,password,role) VALUES (1,'sale@test.local','test','USER')");
        jdbc.update("INSERT INTO products(id,name,price,version) VALUES (1,'Sale product',99999,0),(2,'Plain product',200000,0)");
        jdbc.update("INSERT INTO product_variants(id,product_id,size_value,stock,version) VALUES (1,1,'M',10,0),(2,1,'L',10,0),(3,2,'M',10,0)");
        jdbc.update("INSERT INTO carts(id,user_id) VALUES (1,1)");
        jdbc.update("INSERT INTO cart_items(id,cart_id,product_variant_id,quantity) VALUES (1,1,1,2),(2,1,2,1),(3,1,3,1)");
        jdbc.update("INSERT INTO promotion_programs(id,name,start_date,end_date) VALUES (1,'Sale','2030-01-01T00:00:00Z','2030-01-02T00:00:00Z')");
        jdbc.update("INSERT INTO product_discounts(id,promotion_program_id,product_id,discount_percent) VALUES (1,1,1,15)");
    }

    @Test void cachedDetailReflectsEditDisableAndClockWithoutMutatingCachedResponse() {
        var first = json.valueToTree(products.findById(1L));
        assertThat(first.path("price").decimalValue()).isEqualByComparingTo("99999");
        assertThat(first.path("salePrice").decimalValue()).isEqualByComparingTo("84999");
        assertThat(first.path("saleEndsAt").asText()).isEqualTo("2030-01-02T00:00:00Z");
        var cached = caches.getCache("product").get(1L).get();
        var cachedBefore = json.valueToTree(cached).deepCopy();
        jdbc.update("UPDATE product_discounts SET discount_percent=20");
        assertThat(json.valueToTree(products.findById(1L)).path("salePrice").decimalValue()).isEqualByComparingTo("79999");
        jdbc.update("UPDATE promotion_programs SET status='INACTIVE'");
        assertThat(json.valueToTree(products.findById(1L)).path("salePrice").decimalValue()).isEqualByComparingTo("99999");
        jdbc.update("UPDATE promotion_programs SET status='ACTIVE'");
        doReturn(start.plusSeconds(86400)).when(clock).instant();
        var ended = json.valueToTree(products.findById(1L));
        assertThat(ended.path("salePrice").decimalValue()).isEqualByComparingTo("99999");
        assertThat(ended.path("saleEndsAt").isNull()).isTrue();
        assertThat(caches.getCache("product").get(1L).get()).isSameAs(cached);
        assertThat(json.<com.fasterxml.jackson.databind.JsonNode>valueToTree(cached)).isEqualTo(cachedBefore);
    }

    @Test void listAndCartBatchPromotionLookupAndAgreeOnUnitRounding() {
        clearInvocations(discounts);
        var list = json.valueToTree(products.findAll(null,null,PageRequest.of(0,10,org.springframework.data.domain.Sort.by("id"))));
        assertThat(list.path("content").get(0).path("salePrice").decimalValue()).isEqualByComparingTo("84999");
        verify(discounts,times(1)).findActiveForProducts(anyCollection(),eq(start));
        clearInvocations(discounts);
        var cart = json.valueToTree(carts.getCart("sale@test.local"));
        assertThat(cart.path("totalPrice").decimalValue()).isEqualByComparingTo("454997");
        assertThat(cart.path("originalTotalPrice").decimalValue()).isEqualByComparingTo("499997");
        var item = java.util.stream.StreamSupport.stream(cart.path("items").spliterator(),false)
                .filter(line -> line.path("id").asLong() == 1L).findFirst().orElseThrow();
        assertThat(item.path("productPrice").decimalValue()).isEqualByComparingTo("99999");
        assertThat(item.path("unitPrice").decimalValue()).isEqualByComparingTo("84999");
        assertThat(item.path("subTotal").decimalValue()).isEqualByComparingTo("169998");
        assertThat(item.path("originalSubTotal").decimalValue()).isEqualByComparingTo("199998");
        assertThat(item.path("saleEndsAt").asText()).isEqualTo("2030-01-02T00:00:00Z");
        var plainItem = java.util.stream.StreamSupport.stream(cart.path("items").spliterator(),false)
                .filter(line -> line.path("productId").asLong() == 2L).findFirst().orElseThrow();
        assertThat(plainItem.path("saleEndsAt").isNull()).isTrue();
        verify(discounts,times(1)).findActiveForProducts(anyCollection(),eq(start));
    }

    @Test void productWithDiscountButNoCartOrOrderCanBeDeleted() {
        jdbc.update("DELETE FROM cart_items WHERE product_variant_id IN (1,2)");
        products.findById(1L);
        products.delete(1L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM product_discounts",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM products WHERE id=1",Integer.class)).isZero();
        assertThat(caches.getCache("product").get(1L)).isNull();
    }

    @Test void productDeleteFailureRollsBackDiscountDeletion() {
        assertThatThrownBy(() -> products.delete(1L)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM product_discounts",Integer.class)).isEqualTo(1);
    }

    @Test void historicalOrderBlocksDeleteAndPreservesDiscountsAndSnapshots() {
        jdbc.update("DELETE FROM cart_items WHERE product_variant_id IN (1,2)");
        jdbc.update("INSERT INTO orders(id,user_id,status,total_price,subtotal_price,version) VALUES (1,1,'DONE',99999,99999,0)");
        jdbc.update("INSERT INTO order_items(order_id,product_id,size,quantity,price,original_price) VALUES (1,1,'M',1,99999,99999)");
        assertThatThrownBy(() -> products.delete(1L)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM product_discounts",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT price FROM order_items",java.math.BigDecimal.class)).isEqualByComparingTo("99999");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM products WHERE id=1",Integer.class)).isEqualTo(1);
    }

    @Test void returnedDetailAndNestedVariantsCannotPoisonCache() {
        var detail = products.findById(1L);
        detail.setPrice(java.math.BigDecimal.ONE);
        detail.getVariants().get(0).setStock(999);
        var next = products.findById(1L);
        assertThat(next.getPrice()).isEqualByComparingTo("99999");
        assertThat(next.getVariants().get(0).getStock()).isEqualTo(10);
    }

    @Test void highestPercentAndTieWinnerDetermineEndDate() {
        jdbc.update("INSERT INTO promotion_programs(id,name,start_date,end_date) VALUES (2,'Other','2030-01-01T00:00:00Z','2030-01-03T00:00:00Z')");
        jdbc.update("INSERT INTO product_discounts(id,promotion_program_id,product_id,discount_percent) VALUES (2,2,1,25)");
        assertThat(products.findById(1L).getSalePrice()).isEqualByComparingTo("74999");
        assertThat(products.findById(1L).getSaleEndsAt()).isEqualTo(start.plusSeconds(172800));
        assertThat(carts.getCart("sale@test.local").getItems().stream().filter(i -> i.getProductId().equals(1L)))
                .allSatisfy(i -> assertThat(i.getSaleEndsAt()).isEqualTo(start.plusSeconds(172800)));
        jdbc.update("UPDATE product_discounts SET discount_percent=25 WHERE id=1");
        assertThat(products.findById(1L).getSaleEndsAt()).isEqualTo(start.plusSeconds(86400));
        assertThat(carts.getCart("sale@test.local").getItems().stream().filter(i -> i.getProductId().equals(1L)))
                .allSatisfy(i -> assertThat(i.getSaleEndsAt()).isEqualTo(start.plusSeconds(86400)));
        doReturn(start.minusSeconds(1)).when(clock).instant();
        assertThat(products.findById(1L).getSalePrice()).isEqualByComparingTo("99999");
        assertThat(carts.getCart("sale@test.local").getItems()).allSatisfy(i -> assertThat(i.getSaleEndsAt()).isNull());
        doReturn(start.plusSeconds(172800)).when(clock).instant();
        assertThat(carts.getCart("sale@test.local").getItems()).allSatisfy(i -> assertThat(i.getSaleEndsAt()).isNull());
    }

    @Test void cartMutationsUseLiveProductPricesOnlyAndEmptyCartSkipsQuery() {
        jdbc.update("INSERT INTO order_discounts(promotion_program_id,minimum_amount,discount_percent) VALUES (1,0,90)");
        var update = new com.ecommerce.cart.dto.request.UpdateCartItemRequest();
        update.setVariantId(1L); update.setQuantity(3);
        assertThat(carts.updateCartItemQuantity("sale@test.local",update).getTotalPrice()).isEqualByComparingTo("539996");
        jdbc.update("UPDATE product_discounts SET discount_percent=100");
        var add = new com.ecommerce.cart.dto.request.AddToCartRequest();
        add.setVariantId(1L); add.setQuantity(1);
        assertThat(carts.addToCart("sale@test.local",add).getTotalPrice()).isEqualByComparingTo("200000");
        assertThat(carts.removeCartItem("sale@test.local",3L).getTotalPrice()).isEqualByComparingTo("0");
        jdbc.update("UPDATE promotion_programs SET status='INACTIVE'");
        var plain = carts.getCart("sale@test.local");
        assertThat(plain.getTotalPrice()).isEqualByComparingTo(plain.getOriginalTotalPrice());
        assertThat(plain.getItems()).allSatisfy(i -> assertThat(i.getSaleEndsAt()).isNull());
        jdbc.update("DELETE FROM cart_items");
        clearInvocations(discounts);
        var empty = carts.getCart("sale@test.local");
        assertThat(empty.getTotalPrice()).isZero();
        assertThat(empty.getOriginalTotalPrice()).isZero();
        verify(discounts,never()).findActiveForProducts(anyCollection(),any());
    }

    @Test void productUpdateEvictsBaseCacheAndSaleUsesNewBasePrice() {
        products.findById(1L);
        var request = new com.ecommerce.product.dto.request.ProductRequest();
        request.setName("Updated"); request.setPrice(new java.math.BigDecimal("200000"));
        products.update(1L,request);
        assertThat(caches.getCache("product").get(1L)).isNull();
        assertThat(products.findById(1L).getPrice()).isEqualByComparingTo("200000");
        assertThat(products.findById(1L).getSalePrice()).isEqualByComparingTo("170000");
    }
}
