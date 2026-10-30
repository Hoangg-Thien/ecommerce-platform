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
                var flyway = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).load();
                assertThat(flyway.migrate().migrationsExecuted).isEqualTo(2);
                flyway.validate();
                assertThat(flyway.migrate().migrationsExecuted).isZero();
                try (var result = statement.executeQuery("SELECT total_price,shipping_fee,status FROM orders ORDER BY id")) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getBigDecimal(1)).isEqualByComparingTo("200000");
                    assertThat(result.getBigDecimal(2)).isNull();
                    assertThat(result.getString(3)).isEqualTo("DONE");
                    assertThat(result.next()).isTrue();
                    assertThat(result.getBigDecimal(1)).isEqualByComparingTo("230000");
                    assertThat(result.getBigDecimal(2)).isEqualByComparingTo("30000");
                    assertThat(result.getString(3)).isEqualTo("CANCELLED");
                }
                try (var result = statement.executeQuery("SELECT price,quantity FROM order_items")) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getBigDecimal(1)).isEqualByComparingTo("100000");
                    assertThat(result.getInt(2)).isEqualTo(2);
                }
            }
        }
    }
}
