package com.ecommerce.oms.payment;

import com.ecommerce.oms.common.ApiException;
import com.ecommerce.oms.common.BadRequestException;
import com.ecommerce.oms.common.ConflictException;
import com.ecommerce.oms.common.Money;
import com.ecommerce.oms.common.NotFoundException;
import java.math.BigDecimal;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final PaymentGateway gateway;

    public PaymentService(PaymentRepository paymentRepository, RefundRepository refundRepository,
            PaymentGateway gateway) {
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.gateway = gateway;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Payment capture(Long orderId, BigDecimal amount, String paymentToken) {
        GatewayResult result = gateway.charge(paymentToken, amount, "order-" + orderId);
        if (!result.success()) {
            throw new PaymentDeclinedException(result.failureReason());
        }
        // The charge already happened outside the DB transaction (it's a call to an external PSP), so if
        // anything later in this transaction fails and it rolls back, the charge must be voided: a charge
        // must not outlive a rolled-back order.
        registerVoidOnRollback(result.transactionId(), amount);
        Payment payment = new Payment();
        payment.setOrderId(orderId);
        payment.setAmount(Money.of(amount));
        payment.setRefundedAmount(Money.ZERO);
        payment.setStatus(PaymentStatus.CAPTURED);
        payment.setGatewayTransactionId(result.transactionId());
        return paymentRepository.save(payment);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Refund refund(Long orderId, BigDecimal amount, String reason) {
        if (amount.signum() <= 0) {
            throw new BadRequestException("Refund amount must be positive");
        }
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new NotFoundException("No payment for order " + orderId));
        BigDecimal refundable = payment.getAmount().subtract(payment.getRefundedAmount());
        if (amount.compareTo(refundable) > 0) {
            throw new ConflictException("Refund of " + amount + " exceeds refundable amount " + refundable);
        }
        GatewayResult result = gateway.refund(payment.getGatewayTransactionId(), amount);
        if (!result.success()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Refund failed: " + result.failureReason());
        }
        payment.setRefundedAmount(payment.getRefundedAmount().add(amount));
        payment.setStatus(payment.getRefundedAmount().compareTo(payment.getAmount()) == 0
                ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED);
        Refund refund = new Refund();
        refund.setPayment(payment);
        refund.setAmount(Money.of(amount));
        refund.setReason(reason);
        refund.setGatewayRefundId(result.transactionId());
        return refundRepository.save(refund);
    }

    /** No readOnly flag: called from inside write transactions (checkout, cancel). */
    public Optional<PaymentSummary> findSummary(Long orderId) {
        return paymentRepository.findByOrderId(orderId).map(PaymentSummary::from);
    }

    /**
     * The gateway charge cannot be undone by a DB rollback, so if this transaction ends up rolling back
     * (e.g. the order fails to persist, or a later step throws) we must void the charge ourselves.
     */
    private void registerVoidOnRollback(String transactionId, BigDecimal amount) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_ROLLED_BACK) {
                    return;
                }
                try {
                    gateway.refund(transactionId, amount);
                    log.warn("Voided charge {} for {} after checkout rollback", transactionId, amount);
                } catch (Exception e) {
                    log.error("Failed to void charge {} for {} after checkout rollback", transactionId, amount, e);
                }
            }
        });
    }
}
