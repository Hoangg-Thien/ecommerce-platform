package com.ecommerce.promotion.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;

/** Opt-in: mvnw -Dtest=PromotionBrowserE2E test. Real HTTP + Chrome + disposable PostgreSQL. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:tc:postgresql:17.9:///promotion_browser_e2e",
        "cors.allowed-origins=http://127.0.0.1:5179", "rate-limit.enabled=false",
        "application.security.cookie.secure=false", "payment.momo.mock-enabled=true"
})
class PromotionBrowserE2E {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;

    @Test void realBrowserCheckoutAdminAndMockLifecycle() throws Exception {
        // Only the disposable Testcontainers database; never the developer's database.
        jdbc.execute("TRUNCATE idempotency_keys,voucher_usages,vouchers,product_discounts,order_discounts,promotion_programs,order_items,payments,orders,cart_items,carts,product_variants,products,refresh_tokens,users RESTART IDENTITY CASCADE");
        jdbc.update("INSERT INTO users(id,email,password,role) VALUES (1,'browser@test.local',?,'ADMIN')", passwords.encode("BrowserDemo123!"));
        jdbc.update("INSERT INTO products(id,name,price,version) VALUES (1,'E2E shoe',200000,0)");
        jdbc.update("INSERT INTO product_variants(id,product_id,size_value,stock,version) VALUES (1,1,'M',20,0)");
        var log = Path.of("target", "phase9-browser-e2e.log").toAbsolutePath();
        var builder = new ProcessBuilder("node", "--test", "tests/promotion.real.test.mjs")
                .directory(Path.of("..", "frontend").toFile()).redirectErrorStream(true).redirectOutput(log.toFile());
        builder.environment().put("VITE_API_BASE_URL", "http://127.0.0.1:" + port + "/api/v1");
        var process = builder.start();
        try {
            assertThat(process.waitFor(150, TimeUnit.SECONDS)).as("Chrome E2E timeout; see %s", log).isTrue();
            assertThat(process.exitValue()).withFailMessage(Files.readString(log)).isZero();
        } finally {
            if (process.isAlive()) { process.descendants().forEach(ProcessHandle::destroy); process.destroyForcibly(); }
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders", Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders o JOIN payments p ON p.order_id=o.id WHERE p.amount <> o.total_price", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM voucher_usages WHERE status='APPLIED'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT used_quantity FROM vouchers", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM idempotency_keys WHERE order_id IS NOT NULL", Integer.class)).isEqualTo(4);
    }
}
