package com.ecommerce.payment.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import java.sql.DriverManager;
import static org.assertj.core.api.Assertions.*;

class VnpayMigrationTest {
    @Test void upgradesV5WithoutChangingCodOrMomoHistory() throws Exception {
        try (var db = new PostgreSQLContainer<>("postgres:17.9")) {
            db.start();
            Flyway.configure().dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword()).target("5").load().migrate();
            try (var c = DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword());
                 var s = c.createStatement()) {
                s.execute("INSERT INTO users(id,email,password,role) VALUES (1,'history@test.local','test','USER')");
                s.execute("INSERT INTO orders(id,user_id,total_price,subtotal_price,status,payment_method) VALUES (1,1,5000,5000,'DONE','COD'),(2,1,6000,6000,'CANCELLED','MOMO')");
                s.execute("INSERT INTO payments(order_id,amount,payment_method,payment_status,momo_order_id,transaction_id) VALUES (1,5000,'COD','PAID',NULL,NULL),(2,6000,'MOMO','REFUNDED','OLDREF','OLDTX')");
                Flyway.configure().dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword()).load().migrate();
                try (var r = s.executeQuery("SELECT payment_method,payment_status,amount,momo_order_id,transaction_id,provider_environment FROM payments ORDER BY order_id")) {
                    assertThat(r.next()).isTrue(); assertThat(r.getString(1)).isEqualTo("COD");
                    assertThat(r.getString(2)).isEqualTo("PAID"); assertThat(r.getBigDecimal(3)).isEqualByComparingTo("5000");
                    assertThat(r.next()).isTrue(); assertThat(r.getString(1)).isEqualTo("MOMO");
                    assertThat(r.getString(2)).isEqualTo("REFUNDED"); assertThat(r.getBigDecimal(3)).isEqualByComparingTo("6000");
                    assertThat(r.getString(4)).isEqualTo("OLDREF"); assertThat(r.getString(5)).isEqualTo("OLDTX");
                    assertThat(r.getString(6)).isNull();
                }
                s.execute("INSERT INTO orders(id,user_id,total_price,subtotal_price,status,payment_method) VALUES (3,1,5000,5000,'AWAITING_PAYMENT','VNPAY'),(4,1,5000,5000,'AWAITING_PAYMENT','VNPAY')");
                s.execute("INSERT INTO payments(order_id,amount,payment_method,payment_status,provider_txn_ref,refund_status) VALUES (3,5000,'VNPAY','PENDING','REF123','NONE')");
                assertThatThrownBy(() -> s.execute("INSERT INTO payments(order_id,payment_method,provider_txn_ref) VALUES (4,'VNPAY','REF123')")).isInstanceOf(java.sql.SQLException.class);
            }
        }
    }
    @Test void migratesFreshDatabase() {
        try (var db = new PostgreSQLContainer<>("postgres:17.9")) {
            db.start();
            var flyway = Flyway.configure().dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword()).load();
            flyway.migrate(); flyway.validate();
            assertThat(flyway.info().current().getVersion().toString()).isEqualTo("6");
        }
    }
}
