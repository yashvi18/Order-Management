package com.ecommerce.oms.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Stores an in-app notification and logs it (stand-in for an email/SMS provider). */
@Component
public class NotificationHandler implements OrderEventHandler {

    private static final Logger log = LoggerFactory.getLogger(NotificationHandler.class);

    private final NotificationRepository notificationRepository;

    public NotificationHandler(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Override
    public boolean supports(OrderEventType type) {
        return true;
    }

    @Override
    public void handle(OutboxEvent event) {
        Notification notification = new Notification();
        notification.setUserId(event.getCustomerId());
        notification.setOrderId(event.getOrderId());
        notification.setType(event.getEventType());
        notification.setMessage(message(event));
        notificationRepository.save(notification);
        log.info("Notify customer {}: {}", event.getCustomerId(), notification.getMessage());
    }

    @Override
    public int getOrder() {
        return 100;
    }

    private static String message(OutboxEvent event) {
        String prefix = switch (event.getEventType()) {
            case ORDER_PLACED -> "Thanks! Your order was placed.";
            case ORDER_STATUS_CHANGED -> "Your order status was updated.";
            case ORDER_CANCELLED -> "Your order was cancelled.";
            case RETURN_REQUESTED -> "We received your return request.";
            case RETURN_REJECTED -> "Your return request was rejected.";
            case REFUND_ISSUED -> "Your refund has been issued.";
        };
        return event.getDetails() == null ? prefix : prefix + " " + event.getDetails();
    }
}
