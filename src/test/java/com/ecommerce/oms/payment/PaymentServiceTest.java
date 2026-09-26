package com.ecommerce.oms.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ecommerce.oms.common.ConflictException;
import com.ecommerce.oms.support.IntegrationTestBase;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

class PaymentServiceTest extends IntegrationTestBase {

    @Autowired private PaymentService paymentService;
    @MockitoSpyBean private PaymentGateway gateway;

    @Test
    void captureStoresAnApprovedPayment() {
        tx.executeWithoutResult(s -> paymentService.capture(1L, new BigDecimal("100.00"), "tok_visa"));
        PaymentSummary summary = paymentService.findSummary(1L).orElseThrow();
        assertThat(summary.status()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(summary.amount()).isEqualByComparingTo("100.00");
        assertThat(summary.transactionId()).startsWith("ch_");
    }

    @Test
    void declinedCardThrowsAndStoresNothing() {
        assertThatThrownBy(() -> tx.executeWithoutResult(
                s -> paymentService.capture(1L, new BigDecimal("100.00"), "tok_declined")))
                .isInstanceOf(PaymentDeclinedException.class)
                .hasMessage("Payment declined: Card declined");
        assertThat(paymentService.findSummary(1L)).isEmpty();
    }

    @Test
    void partialThenFullRefundUpdatesStatus() {
        tx.executeWithoutResult(s -> paymentService.capture(1L, new BigDecimal("100.00"), "tok_visa"));
        tx.executeWithoutResult(s -> paymentService.refund(1L, new BigDecimal("30.00"), "partial"));
        assertThat(paymentService.findSummary(1L).orElseThrow().status()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        tx.executeWithoutResult(s -> paymentService.refund(1L, new BigDecimal("70.00"), "rest"));
        PaymentSummary summary = paymentService.findSummary(1L).orElseThrow();
        assertThat(summary.status()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(summary.refundedAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    void cannotRefundMoreThanCaptured() {
        tx.executeWithoutResult(s -> paymentService.capture(1L, new BigDecimal("100.00"), "tok_visa"));
        tx.executeWithoutResult(s -> paymentService.refund(1L, new BigDecimal("60.00"), "partial"));
        assertThatThrownBy(() -> tx.executeWithoutResult(
                s -> paymentService.refund(1L, new BigDecimal("40.01"), "too much")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void voidsChargeWhenTransactionRollsBackAfterCapture() {
        tx.executeWithoutResult(s -> {
            paymentService.capture(1L, new BigDecimal("100.00"), "tok_visa");
            s.setRollbackOnly();
        });
        assertThat(paymentService.findSummary(1L)).isEmpty();
        verify(gateway).refund(anyString(), eq(new BigDecimal("100.00")));
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void failedVoidIsLoggedAsErrorNotAsVoided(CapturedOutput output) {
        doReturn(GatewayResult.declined("PSP unavailable")).when(gateway).refund(anyString(), any());
        tx.executeWithoutResult(s -> {
            paymentService.capture(1L, new BigDecimal("100.00"), "tok_visa");
            s.setRollbackOnly();
        });
        assertThat(output).contains("ERROR").contains("Failed to void charge").contains("PSP unavailable")
                .doesNotContain("Voided charge");
    }

    @Test
    void committedCaptureNeverRefunds() {
        tx.executeWithoutResult(s -> paymentService.capture(1L, new BigDecimal("100.00"), "tok_visa"));
        verify(gateway, never()).refund(any(), any());
    }
}
