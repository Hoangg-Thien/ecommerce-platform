package com.ecommerce.integration;

import com.ecommerce.category.entity.Category;
import com.ecommerce.product.entity.Product;
import com.ecommerce.product.entity.ProductVariant;
import com.ecommerce.category.repository.CategoryRepository;
import com.ecommerce.product.repository.ProductRepository;
import com.ecommerce.product.repository.ProductVariantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:tc:postgresql:17.9:///searchdb",
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate"
})
class ProductSearchIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long categoryAoId;
    private Long categoryQuanId;
    private Long categoryGiayId;

    private final List<Product> seededProducts = new ArrayList<>();

    @BeforeEach
    void setUp() {
        cleanDb();

        Category catAo = new Category();
        catAo.setName("Áo");
        catAo = categoryRepository.save(catAo);
        categoryAoId = catAo.getId();

        Category catQuan = new Category();
        catQuan.setName("Quần");
        catQuan = categoryRepository.save(catQuan);
        categoryQuanId = catQuan.getId();

        Category catGiay = new Category();
        catGiay.setName("Giày");
        catGiay = categoryRepository.save(catGiay);
        categoryGiayId = catGiay.getId();

        // 1: Áo thun polo nam (Áo, price 200) + variant
        Product p1 = createProduct("Áo thun polo nam", new BigDecimal("200.00"), catAo);
        ProductVariant v1 = new ProductVariant();
        v1.setProduct(p1);
        v1.setSize("L");
        v1.setStock(10);
        p1.getVariants().add(v1);
        p1 = productRepository.save(p1);
        seededProducts.add(p1);

        // 2: Áo thun nữ cổ tròn (Áo, price 150)
        Product p2 = productRepository.save(createProduct("Áo thun nữ cổ tròn", new BigDecimal("150.00"), catAo));
        seededProducts.add(p2);

        // 3: Áo sơ mi trắng (Áo, price 300)
        Product p3 = productRepository.save(createProduct("Áo sơ mi trắng", new BigDecimal("300.00"), catAo));
        seededProducts.add(p3);

        // 4: Quần jean nam slim (Quần, price 400)
        Product p4 = productRepository.save(createProduct("Quần jean nam slim", new BigDecimal("400.00"), catQuan));
        seededProducts.add(p4);

        // 5: Giày thể thao Nike Air (Giày, price 1200)
        Product p5 = productRepository.save(createProduct("Giày thể thao Nike Air", new BigDecimal("1200.00"), catGiay));
        seededProducts.add(p5);

        // 6: Giày sneaker trắng (Giày, price 800)
        Product p6 = productRepository.save(createProduct("Giày sneaker trắng", new BigDecimal("800.00"), catGiay));
        seededProducts.add(p6);

        // 7: Đồng hồ thông minh (null, price 1500)
        Product p7 = productRepository.save(createProduct("Đồng hồ thông minh", new BigDecimal("1500.00"), null));
        seededProducts.add(p7);

        // 8: Giảm giá 100% sản_phẩm (null, price 100)
        Product p8 = productRepository.save(createProduct("Giảm giá 100% sản_phẩm", new BigDecimal("100.00"), null));
        seededProducts.add(p8);

        // 9: Bộ vệ sinh giày (null, price 50)
        Product p9 = productRepository.save(createProduct("Bộ vệ sinh giày", new BigDecimal("50.00"), null));
        seededProducts.add(p9);

        // 10: Áo size 1000 (Áo, price 250)
        Product p10 = productRepository.save(createProduct("Áo size 1000", new BigDecimal("250.00"), catAo));
        seededProducts.add(p10);

        // 11: Sản phẩm mẫu (null, price 500)
        Product p11 = productRepository.save(createProduct("Sản phẩm mẫu", new BigDecimal("500.00"), null));
        seededProducts.add(p11);
    }

    @AfterEach
    void tearDown() {
        cleanDb();
    }

    private void cleanDb() {
        productVariantRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        seededProducts.clear();
    }

    private Product createProduct(String name, BigDecimal price, Category category) {
        Product p = new Product();
        p.setName(name);
        p.setPrice(price);
        p.setCategory(category);
        return p;
    }

    @Test
    void search_Unaccent_ShouldReturnExpectedProducts() throws Exception {
        mockMvc.perform(get("/api/v1/products").param("q", "ao thun"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[*].name", containsInAnyOrder("Áo thun polo nam", "Áo thun nữ cổ tròn")));
    }

    @Test
    void search_CaseInsensitive_ShouldReturnExpectedProducts() throws Exception {
        mockMvc.perform(get("/api/v1/products").param("q", "ÁO THUN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[*].name", containsInAnyOrder("Áo thun polo nam", "Áo thun nữ cổ tròn")));
    }

    @Test
    void search_LetterDWithStroke_ShouldMatchBothForms() throws Exception {
        mockMvc.perform(get("/api/v1/products").param("q", "dong ho"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name").value("Đồng hồ thông minh"));

        mockMvc.perform(get("/api/v1/products").param("q", "Đồng Hồ"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name").value("Đồng hồ thông minh"));
    }

    @Test
    void search_ReverseWordOrder_ShouldMatchAllTokens() throws Exception {
        mockMvc.perform(get("/api/v1/products").param("q", "nam ao"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name").value("Áo thun polo nam"));
    }

    @Test
    void search_MultipleWords_ShouldMatch() throws Exception {
        mockMvc.perform(get("/api/v1/products").param("q", "giay trang"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name").value("Giày sneaker trắng"));
    }

    @Test
    void search_RelevanceRanking_ShouldRankPrefixFirstThenContains() throws Exception {
        // "giay":
        // 5: Giày thể thao Nike Air (starts with "giay" -> rank 2, id smaller)
        // 6: Giày sneaker trắng (starts with "giay" -> rank 2, id larger)
        // 9: Bộ vệ sinh giày (contains "giay" -> rank 1)
        mockMvc.perform(get("/api/v1/products").param("q", "giay"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.content[0].name").value("Giày thể thao Nike Air"))
                .andExpect(jsonPath("$.content[1].name").value("Giày sneaker trắng"))
                .andExpect(jsonPath("$.content[2].name").value("Bộ vệ sinh giày"));
    }

    @Test
    void search_EscapePercentSign_ShouldOnlyMatchLiteralPercent() throws Exception {
        // Escaped '%': "100%" should only match #8 ("Giảm giá 100% sản_phẩm"), not #10 ("Áo size 1000")
        mockMvc.perform(get("/api/v1/products").param("q", "100%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name").value("Giảm giá 100% sản_phẩm"));
    }

    @Test
    void search_EscapeUnderscore_ShouldOnlyMatchLiteralUnderscore() throws Exception {
        // Escaped '_': "san_pham" should only match #8 ("Giảm giá 100% sản_phẩm"), not #11 ("Sản phẩm mẫu")
        mockMvc.perform(get("/api/v1/products").param("q", "san_pham"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name").value("Giảm giá 100% sản_phẩm"));
    }

    @Test
    void search_WithoutQueryParam_ShouldReturnAllProductsOrderedByIdAsc() throws Exception {
        mockMvc.perform(get("/api/v1/products").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(11))
                .andExpect(jsonPath("$.content", hasSize(11)))
                .andExpect(jsonPath("$.content[0].name").value("Áo thun polo nam"))
                .andExpect(jsonPath("$.content[10].name").value("Sản phẩm mẫu"));
    }

    @Test
    void search_WithCategoryFilter_ShouldCombineCategoryAndKeyword() throws Exception {
        // "trang" matches #3 ("Áo sơ mi trắng") and #6 ("Giày sneaker trắng")
        // With categoryId = categoryGiayId, only #6 should be returned
        mockMvc.perform(get("/api/v1/products")
                        .param("q", "trang")
                        .param("categoryId", categoryGiayId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name").value("Giày sneaker trắng"));
    }

    @Test
    void search_Pagination_ShouldReturnPaginatedContentAndMetadata() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .param("q", "trang")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    void search_SortOverride_ShouldSortByRequestedField() throws Exception {
        // "trang": #6 (price 800), #3 (price 300)
        // With sortBy=price and sortDir=desc, #6 should be first, then #3
        mockMvc.perform(get("/api/v1/products")
                        .param("q", "trang")
                        .param("sortBy", "price")
                        .param("sortDir", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].name").value("Giày sneaker trắng"))
                .andExpect(jsonPath("$.content[1].name").value("Áo sơ mi trắng"));
    }

    @Test
    void search_Validation_ShouldReturn400() throws Exception {
        mockMvc.perform(get("/api/v1/products").param("q", "a"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Từ khóa tối thiểu 2 ký tự"));

        mockMvc.perform(get("/api/v1/products").param("q", "a".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Từ khóa tối đa 100 ký tự"));

        mockMvc.perform(get("/api/v1/products").param("sortBy", "hack"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("sortBy không hợp lệ. Cho phép: id, name, price, relevance"));
    }

    @Test
    void search_VariantsBatchSize_ShouldLoadVariantsProperly() throws Exception {
        mockMvc.perform(get("/api/v1/products").param("q", "polo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name").value("Áo thun polo nam"))
                .andExpect(jsonPath("$.content[0].variants", hasSize(1)))
                .andExpect(jsonPath("$.content[0].variants[0].size").value("L"))
                .andExpect(jsonPath("$.content[0].variants[0].stock").value(10));
    }

    @Test
    void schema_GeneratedColumnAndIndexes_ShouldExistInDatabase() {
        Long p7Id = seededProducts.get(6).getId();
        String searchName = jdbcTemplate.queryForObject(
                "SELECT search_name FROM products WHERE id = ?",
                String.class,
                p7Id
        );
        assertEquals("dong ho thong minh", searchName);

        List<String> indexes = jdbcTemplate.queryForList(
                "SELECT indexname FROM pg_indexes WHERE tablename = 'products'",
                String.class
        );
        assertTrue(indexes.contains("idx_products_search_name_trgm"), "Missing idx_products_search_name_trgm index");
        assertTrue(indexes.contains("idx_products_category_id"), "Missing idx_products_category_id index");
    }
}
