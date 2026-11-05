package com.ecommerce.payment.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.assertThat;

class VnpayResultTest {
    @Test void onlyDoubleZeroIsSuccessful() { assertThat(VnpayResult.classify("00","00")).isEqualTo(VnpayResult.SUCCESS); }
    @ParameterizedTest @CsvSource({"24,02","11,02","51,02","07,00","00,07","00,02","99,00","00,01","00,04","00,05","00,06","00,09"})
    void candidatesAndAmbiguousPairsAreUnknown(String response, String status) {
        assertThat(VnpayResult.classify(response,status)).isEqualTo(VnpayResult.UNKNOWN);
    }
    @Test void failureAllowlistRemainsEmptyUntilMerchantVerification() {
        for (int r=0;r<100;r++) for(int s=0;s<100;s++)
            assertThat(VnpayResult.classify("%02d".formatted(r),"%02d".formatted(s))).isNotEqualTo(VnpayResult.FAILURE);
    }
}
