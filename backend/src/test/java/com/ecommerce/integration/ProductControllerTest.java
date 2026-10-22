package com.ecommerce.integration;

import com.ecommerce.auth.config.JwtAuthenticationFilter;
import com.ecommerce.common.ratelimit.RateLimitFilter;
import com.ecommerce.product.controller.ProductController;
import com.ecommerce.common.dto.PageResponse;
import com.ecommerce.product.dto.response.ProductResponse;
import com.ecommerce.common.idempotency.IdempotencyKeyRepository;
import com.ecommerce.auth.service.JwtService;
import com.ecommerce.product.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ProductController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductService productService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private RateLimitFilter rateLimitFilter;

    @MockBean 
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Test
    void getAllProducts_WithPaginationParams_ShouldReturn200AndPageResponse() throws Exception {
        ProductResponse productResponse = ProductResponse.builder()
                .id(1L)
                .name("MacBook Pro M3")
                .price(BigDecimal.valueOf(2000))
                .stock(5)
                .categoryId(1L)
                .categoryName("Laptops")
                .build();

        PageResponse<ProductResponse> pageResponse = PageResponse.<ProductResponse>builder()
                .content(List.of(productResponse))
                .pageNo(0)
                .pageSize(10)
                .totalElements(1)
                .totalPages(1)
                .last(true)
                .build();

        when(productService.findAll(eq(1L), any(), any(Pageable.class)))
                .thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/products")
                        .param("categoryId", "1")
                        .param("page", "0")
                        .param("size", "10")
                        .param("sortBy", "price")
                        .param("sortDir", "desc")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageNo").value(0))
                .andExpect(jsonPath("$.pageSize").value(10))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.last").value(true))
                .andExpect(jsonPath("$.content[0].name").value("MacBook Pro M3"));
    }

    @Test
    void getAllProducts_WithKeyword_ShouldPassKeywordAndUnsortedPageableToService() throws Exception {
        org.mockito.ArgumentCaptor<String> keywordCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.ArgumentCaptor<Pageable> pageableCaptor = org.mockito.ArgumentCaptor.forClass(Pageable.class);

        when(productService.findAll(any(), keywordCaptor.capture(), pageableCaptor.capture()))
                .thenReturn(PageResponse.<ProductResponse>builder().content(List.of()).build());

        mockMvc.perform(get("/api/v1/products")
                        .param("q", "ao thun")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        org.junit.jupiter.api.Assertions.assertEquals("ao thun", keywordCaptor.getValue());
        org.junit.jupiter.api.Assertions.assertTrue(pageableCaptor.getValue().getSort().isUnsorted());
    }

    @Test
    void getAllProducts_WithoutKeywordAndWithoutSortBy_ShouldSortByIdAsc() throws Exception {
        org.mockito.ArgumentCaptor<Pageable> pageableCaptor = org.mockito.ArgumentCaptor.forClass(Pageable.class);

        when(productService.findAll(any(), any(), pageableCaptor.capture()))
                .thenReturn(PageResponse.<ProductResponse>builder().content(List.of()).build());

        mockMvc.perform(get("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        org.springframework.data.domain.Sort sort = pageableCaptor.getValue().getSort();
        org.junit.jupiter.api.Assertions.assertEquals(
                org.springframework.data.domain.Sort.by("id").ascending(), sort);
    }

    @Test
    void getAllProducts_WithKeywordAndExplicitSort_ShouldUseExplicitSort() throws Exception {
        org.mockito.ArgumentCaptor<Pageable> pageableCaptor = org.mockito.ArgumentCaptor.forClass(Pageable.class);

        when(productService.findAll(any(), any(), pageableCaptor.capture()))
                .thenReturn(PageResponse.<ProductResponse>builder().content(List.of()).build());

        mockMvc.perform(get("/api/v1/products")
                        .param("q", "ao thun")
                        .param("sortBy", "price")
                        .param("sortDir", "desc")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        org.springframework.data.domain.Sort sort = pageableCaptor.getValue().getSort();
        org.junit.jupiter.api.Assertions.assertEquals(
                org.springframework.data.domain.Sort.by("price").descending(), sort);
    }

    @Test
    void getAllProducts_WhenKeywordTooShort_ShouldReturn400() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .param("q", "a")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Từ khóa tối thiểu 2 ký tự"));
    }

    @Test
    void getAllProducts_WhenKeywordTooLong_ShouldReturn400() throws Exception {
        String longKeyword = "a".repeat(101);
        mockMvc.perform(get("/api/v1/products")
                        .param("q", longKeyword)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Từ khóa tối đa 100 ký tự"));
    }

    @Test
    void getAllProducts_WhenInvalidSortBy_ShouldReturn400() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .param("sortBy", "hack")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("sortBy không hợp lệ. Cho phép: id, name, price, relevance"));
    }
}
