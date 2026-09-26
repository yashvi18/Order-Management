package com.ecommerce.oms.events;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Background worker: drains the outbox off the request thread, so checkout never waits for it. */
@Component
@ConditionalOnProperty(name = "oms.outbox.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxScheduler {

    private final OutboxProcessor outboxProcessor;

    public OutboxScheduler(OutboxProcessor outboxProcessor) {
        this.outboxProcessor = outboxProcessor;
    }

    @Scheduled(fixedDelayString = "${oms.outbox.poll-interval-ms:500}")
    public void poll() {
        outboxProcessor.processBatch();
    }
}
