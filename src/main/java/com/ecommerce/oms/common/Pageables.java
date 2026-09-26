package com.ecommerce.oms.common;

import java.util.Set;
import java.util.TreeSet;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class Pageables {

    private Pageables() {
    }

    /** Rejects client-supplied sort properties outside the whitelist (otherwise JPA fails with a 500). */
    public static void requireSortableBy(Pageable pageable, Set<String> allowed) {
        for (Sort.Order order : pageable.getSort()) {
            if (!allowed.contains(order.getProperty())) {
                throw new BadRequestException("Cannot sort by '" + order.getProperty() + "'; allowed: "
                        + String.join(", ", new TreeSet<>(allowed)));
            }
        }
    }
}
