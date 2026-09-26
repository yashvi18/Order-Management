package com.ecommerce.oms.discount;

import com.ecommerce.oms.common.BadRequestException;
import com.ecommerce.oms.common.ConflictException;
import com.ecommerce.oms.common.Money;
import com.ecommerce.oms.common.NotFoundException;
import com.ecommerce.oms.discount.DiscountDtos.DiscountRequest;
import com.ecommerce.oms.discount.DiscountDtos.DiscountResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiscountService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final DiscountRepository discountRepository;

    public DiscountService(DiscountRepository discountRepository) {
        this.discountRepository = discountRepository;
    }

    /** Validates the code for this subtotal and returns the money it takes off. Does not consume a use. */
    public AppliedDiscount apply(String code, BigDecimal subtotal, Instant now) {
        Discount d = discountRepository.findByCodeIgnoreCase(code.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new BadRequestException("Invalid discount code"));
        if (!d.isActive()) {
            throw new BadRequestException("Discount code is not active");
        }
        if (d.getValidFrom() != null && now.isBefore(d.getValidFrom())) {
            throw new BadRequestException("Discount code is not yet valid");
        }
        if (d.getValidUntil() != null && !now.isBefore(d.getValidUntil())) {
            throw new BadRequestException("Discount code has expired");
        }
        if (d.getMinOrderAmount() != null && subtotal.compareTo(d.getMinOrderAmount()) < 0) {
            throw new BadRequestException("Order subtotal must be at least " + d.getMinOrderAmount() + " to use this code");
        }
        if (d.getUsageLimit() != null && d.getTimesUsed() >= d.getUsageLimit()) {
            throw new BadRequestException("Discount code usage limit reached");
        }
        return new AppliedDiscount(d.getId(), d.getCode(), calculate(d, subtotal));
    }

    public static BigDecimal calculate(Discount d, BigDecimal subtotal) {
        BigDecimal amount = switch (d.getType()) {
            case PERCENTAGE -> subtotal.multiply(d.getValue()).divide(HUNDRED, 2, RoundingMode.HALF_UP);
            case FIXED_AMOUNT -> d.getValue();
        };
        if (d.getMaxDiscountAmount() != null) {
            amount = amount.min(d.getMaxDiscountAmount());
        }
        return Money.of(amount.min(subtotal));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void redeem(Long discountId) {
        if (discountRepository.incrementUsage(discountId) == 0) {
            throw new BadRequestException("Discount code usage limit reached");
        }
    }

    @Transactional
    public DiscountResponse create(DiscountRequest request) {
        if (discountRepository.existsByCodeIgnoreCase(request.code())) {
            throw new ConflictException("Discount code '" + request.code() + "' already exists");
        }
        Discount discount = new Discount();
        apply(discount, request);
        return DiscountResponse.from(discountRepository.save(discount));
    }

    @Transactional
    public DiscountResponse update(Long id, DiscountRequest request) {
        Discount discount = require(id);
        if (!discount.getCode().equalsIgnoreCase(request.code()) && discountRepository.existsByCodeIgnoreCase(request.code())) {
            throw new ConflictException("Discount code '" + request.code() + "' already exists");
        }
        apply(discount, request);
        return DiscountResponse.from(discount);
    }

    @Transactional
    public void deactivate(Long id) {
        require(id).setActive(false);
    }

    @Transactional(readOnly = true)
    public List<DiscountResponse> list() {
        return discountRepository.findAll(Sort.by("code")).stream().map(DiscountResponse::from).toList();
    }

    private Discount require(Long id) {
        return discountRepository.findById(id).orElseThrow(() -> new NotFoundException("Discount not found"));
    }

    private void apply(Discount discount, DiscountRequest request) {
        if (request.type() == DiscountType.PERCENTAGE && request.value().compareTo(HUNDRED) > 0) {
            throw new BadRequestException("A percentage discount cannot exceed 100");
        }
        if (request.validFrom() != null && request.validUntil() != null && !request.validUntil().isAfter(request.validFrom())) {
            throw new BadRequestException("validUntil must be after validFrom");
        }
        discount.setCode(request.code().toUpperCase(Locale.ROOT));
        discount.setDescription(request.description());
        discount.setType(request.type());
        discount.setValue(Money.of(request.value()));
        discount.setMinOrderAmount(request.minOrderAmount());
        discount.setMaxDiscountAmount(request.maxDiscountAmount());
        discount.setValidFrom(request.validFrom());
        discount.setValidUntil(request.validUntil());
        discount.setUsageLimit(request.usageLimit());
        if (request.active() != null) {
            discount.setActive(request.active());
        }
    }
}
