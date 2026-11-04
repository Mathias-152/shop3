package edu.arch.shop.order.dto;

import java.math.BigDecimal;

public record OrderRequest(
        Long customerId,
        String productName,
        Integer quantity,
        BigDecimal unitPrice
) {
}
