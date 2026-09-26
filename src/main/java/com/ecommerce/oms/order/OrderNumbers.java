package com.ecommerce.oms.order;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public final class OrderNumbers {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    private OrderNumbers() {
    }

    public static String next() {
        StringBuilder suffix = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            suffix.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return "ORD-" + LocalDate.now(ZoneOffset.UTC).format(DAY) + "-" + suffix;
    }
}
