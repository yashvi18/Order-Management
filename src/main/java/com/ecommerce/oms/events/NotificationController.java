package com.ecommerce.oms.events;

import com.ecommerce.oms.auth.AppUserDetails;
import com.ecommerce.oms.common.PageResponse;
import com.ecommerce.oms.common.Pageables;
import java.time.Instant;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    public record NotificationResponse(Long id, Long orderId, OrderEventType type, String message, Instant createdAt) {
    }

    private static final Set<String> SORTABLE = Set.of("id", "createdAt");

    private final NotificationRepository notificationRepository;

    public NotificationController(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> mine(@AuthenticationPrincipal AppUserDetails me,
            @PageableDefault(size = 20) Pageable pageable) {
        Pageables.requireSortableBy(pageable, SORTABLE);
        return PageResponse.from(notificationRepository.findByUserIdOrderByIdDesc(me.id(), pageable)
                .map(n -> new NotificationResponse(n.getId(), n.getOrderId(), n.getType(), n.getMessage(), n.getCreatedAt())));
    }
}
