package com.ecommerce.checkout.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

class ExistingOrderMigrationTest {
    @Test
    void migratesExistingV1OrdersWithoutChangingHistoricalAmounts() throws Exception {
        try (var postgres = new PostgreSQLContainer<>("postgres:17.9")) {
            postgres.start();
            Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .target("1").load().migrate();
            try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 var statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO users(id,email,password,role) VALUES (1,'old@test.local','test','USER')");
                statement.executeUpdate("INSERT INTO orders(id,user_id,total_price,shipping_fee,status) VALUES (1,1,200000,NULL,'DONE'),(2,1,230000,30000,'CANCELLED')");
                statement.executeUpdate("INSERT INTO products(id,name,price) VALUES (1,'Old product',100000)");
                statement.executeUpdate("INSERT INTO order_items(order_id,product_id,quantity,price) VALUES (1,1,2,100000)");
                statement.executeUpdate("INSERT INTO idempotency_keys(id,created_at) VALUES ('legacy',now())");
                var flyway = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).load();
                assertThat(flyway.migrate().migrationsExecuted).isEqualTo(5);
                flyway.validate();
                assertThat(flyway.migrate().migrationsExecuted).isZero();
                try (var result = statement.executeQuery("SELECT order_id FROM idempotency_keys WHERE id='legacy'")) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getObject(1)).isNull();
                }
                try (var result = statement.executeQuery("SELECT total_price,shipping_fee,status,subtotal_price,discount_amount,voucher_code,order_discount_source,order_discount_percent FROM orders ORDER BY id")) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getBigDecimal(1)).isEqualByComparingTo("200000");
                    assertThat(result.getBigDecimal(2)).isNull();
                    assertThat(result.getString(3)).isEqualTo("DONE");
                    assertThat(result.getBigDecimal(4)).isEqualByComparingTo("200000");
                    assertThat(result.getBigDecimal(5)).isEqualByComparingTo("0");
                    assertThat(result.getString(6)).isNull();
                    assertThat(result.getString(7)).isNull();
                    assertThat(result.getBigDecimal(8)).isNull();
                    assertThat(result.next()).isTrue();
                    assertThat(result.getBigDecimal(1)).isEqualByComparingTo("230000");
                    assertThat(result.getBigDecimal(2)).isEqualByComparingTo("30000");
                    assertThat(result.getString(3)).isEqualTo("CANCELLED");
                    assertThat(result.getBigDecimal(4)).isEqualByComparingTo("200000");
                }
                try (var result = statement.executeQuery("SELECT price,quantity,original_price,product_discount_percent,product_discount_program_id FROM order_items")) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getBigDecimal(1)).isEqualByComparingTo("100000");
                    assertThat(result.getInt(2)).isEqualTo(2);
                    assertThat(result.getBigDecimal(3)).isEqualByComparingTo("100000");
                    assertThat(result.getBigDecimal(4)).isNull();
                    assertThat(result.getObject(5)).isNull();
                }
            }
        }
    }
}
