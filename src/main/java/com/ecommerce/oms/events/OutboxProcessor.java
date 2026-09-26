package com.ecommerce.oms.events;

import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class OutboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(OutboxProcessor.class);

    private final OutboxRepository outboxRepository;
    private final List<OrderEventHandler> handlers;
    private final TransactionTemplate tx;
    private final int batchSize;
    private final int maxAttempts;

    public OutboxProcessor(OutboxRepository outboxRepository, List<OrderEventHandler> handlers, TransactionTemplate tx,
            @Value("${oms.outbox.batch-size:50}") int batchSize, @Value("${oms.outbox.max-attempts:5}") int maxAttempts) {
        this.outboxRepository = outboxRepository;
        this.handlers = handlers;
        this.tx = tx;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
    }

    /** Processes up to batchSize pending events, oldest first. Returns how many succeeded. */
    public int processBatch() {
        List<Long> ids = tx.execute(status -> outboxRepository
                .findByStatusOrderByIdAsc(OutboxStatus.PENDING, PageRequest.of(0, batchSize))
                .stream().map(OutboxEvent::getId).toList());
        int processed = 0;
        for (Long id : ids) {
            if (processOne(id)) {
                processed++;
            }
        }
        return processed;
    }

    private boolean processOne(Long id) {
        try {
            tx.executeWithoutResult(status -> {
                OutboxEvent event = outboxRepository.findById(id).orElseThrow();
                if (event.getStatus() != OutboxStatus.PENDING) {
                    return;
                }
                for (OrderEventHandler handler : handlers) {
                    if (handler.supports(event.getEventType())) {
                        handler.handle(event);
                    }
                }
                event.setAttempts(event.getAttempts() + 1);
                event.setStatus(OutboxStatus.PROCESSED);
                event.setProcessedAt(Instant.now());
            });
            return true;
        } catch (RuntimeException ex) {
            log.warn("Outbox event {} failed: {}", id, ex.toString());
            tx.executeWithoutResult(status -> {
                OutboxEvent event = outboxRepository.findById(id).orElseThrow();
                event.setAttempts(event.getAttempts() + 1);
                String error = ex.toString();
                event.setLastError(error.length() > 1000 ? error.substring(0, 1000) : error);
                if (event.getAttempts() >= maxAttempts) {
                    event.setStatus(OutboxStatus.FAILED);
                    log.error("Outbox event {} moved to FAILED after {} attempts", id, event.getAttempts());
                }
            });
            return false;
        }
    }
}
