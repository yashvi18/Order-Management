package com.ecommerce.oms.discount;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DiscountRepository extends JpaRepository<Discount, Long> {

    Optional<Discount> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    /** Single conditional UPDATE: two concurrent checkouts can never both take the last use. Returns rows changed. */
    @Modifying(flushAutomatically = true)
    @Query("update Discount d set d.timesUsed = d.timesUsed + 1 "
            + "where d.id = :id and (d.usageLimit is null or d.timesUsed < d.usageLimit)")
    int incrementUsage(@Param("id") Long id);
}
