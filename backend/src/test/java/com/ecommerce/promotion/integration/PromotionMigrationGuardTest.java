package com.ecommerce.promotion.integration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.*;

class PromotionMigrationGuardTest {
    @ParameterizedTest
    @CsvSource({"-1,0", "100,-1", "100,200"})
    void refusesAbnormalHistoricalMoneyWithoutPartiallyMigrating(String total, String shipping) throws Exception {
        try (var postgres = new PostgreSQLContainer<>("postgres:17.9")) {
            postgres.start();
            Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .target("3").load().migrate();
            try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 var statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO users(id,email,password) VALUES (1,'old@test.local','test')");
                try (var insert = connection.prepareStatement("INSERT INTO orders(user_id,total_price,shipping_fee,status) VALUES (1,?,?,'DONE')")) {
                    insert.setBigDecimal(1, new java.math.BigDecimal(total));
                    insert.setBigDecimal(2, new java.math.BigDecimal(shipping));
                    insert.executeUpdate();
                }
                var flyway = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).load();
                assertThatThrownBy(flyway::migrate).isInstanceOf(FlywayException.class)
                        .hasMessageContaining("requires review of invalid historical order");
                try (var rows = statement.executeQuery("SELECT total_price, shipping_fee FROM orders")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getBigDecimal(1)).isEqualByComparingTo(total);
                    assertThat(rows.getBigDecimal(2)).isEqualByComparingTo(shipping);
                }
                try (var rows = statement.executeQuery("SELECT to_regclass('public.promotion_programs')")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(1)).isNull();
                }
                assertThat(flyway.info().current().getVersion().toString()).isEqualTo("3");
            }
        }
    }
}
