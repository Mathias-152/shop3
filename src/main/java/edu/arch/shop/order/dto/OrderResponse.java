package edu.arch.shop.order.dto;

import java.math.BigDecimal;

public record OrderResponse(
        Long id,
        Long customerId,
        String customerName,
        String productName,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal totalPrice
) {
}
