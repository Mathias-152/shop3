package edu.arch.shop.audit.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AuditResponse(UUID eventId, Instant occurredAt, Instant recordedAt,
                            Long orderId, Long customerId, String customerName,
                            String productName, Integer quantity, BigDecimal unitPrice, BigDecimal totalPrice) {
}
