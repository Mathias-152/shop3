package edu.arch.shop.contracts;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderCreated(
        UUID eventId,
        int schemaVersion,
        Instant occurredAt,
        Long orderId,
        Long customerId,
        String customerName,
        String productName,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal totalPrice
) {
}
