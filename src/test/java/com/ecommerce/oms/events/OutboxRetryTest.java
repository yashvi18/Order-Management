package com.ecommerce.oms.events;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecommerce.oms.auth.User;
import com.ecommerce.oms.support.IntegrationTestBase;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@Import(OutboxRetryTest.FailingHandlerConfig.class)
class OutboxRetryTest extends IntegrationTestBase {

    static final AtomicInteger failuresRemaining = new AtomicInteger();

    @TestConfiguration
    static class FailingHandlerConfig {
        @Bean
        OrderEventHandler flakyHandler() {
            return new OrderEventHandler() {
                @Override
                public boolean supports(OrderEventType type) {
                    return true;
                }

                @Override
                public void handle(OutboxEvent event) {
                    if (failuresRemaining.getAndDecrement() > 0) {
                        throw new IllegalStateException("boom");
                    }
                }

                @Override
                public int getOrder() {
                    return 300;
                }
            };
        }
    }

    @Autowired private OrderEventPublisher publisher;
    @Autowired private OutboxProcessor processor;
    @Autowired private OutboxRepository outboxRepository;
    @Autowired private NotificationRepository notificationRepository;

    private User alice;

    @BeforeEach
    void setUp() {
        alice = fixtures.customer("alice@test.local");
        tx.executeWithoutResult(s -> publisher.publish(OrderEventType.ORDER_PLACED, 7L, alice.getId(), "a", "x"));
    }

    @Test
    void failedEventIsRetriedAndHandlerSideEffectsRollBack() {
        failuresRemaining.set(1);
        assertThat(processor.processBatch()).isZero();
        OutboxEvent event = outboxRepository.findAll().getFirst();
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getLastError()).contains("boom");
        assertThat(notificationRepository.count()).isZero();

        assertThat(processor.processBatch()).isEqualTo(1);
        assertThat(outboxRepository.findAll().getFirst().getStatus()).isEqualTo(OutboxStatus.PROCESSED);
        assertThat(notificationRepository.count()).isEqualTo(1);
    }

    @Test
    void eventIsMarkedFailedAfterMaxAttempts() {
        failuresRemaining.set(100);
        for (int i = 0; i < 5; i++) {
            processor.processBatch();
        }
        OutboxEvent event = outboxRepository.findAll().getFirst();
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(event.getAttempts()).isEqualTo(5);
        assertThat(processor.processBatch()).isZero();
    }
}
