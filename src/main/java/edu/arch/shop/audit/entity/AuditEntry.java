package edu.arch.shop.audit.entity;

import edu.arch.shop.contracts.OrderCreated;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_entries")
@Getter
@NoArgsConstructor
public class AuditEntry {
    @Id
    private UUID eventId;
    @Column(nullable = false)
    private Instant occurredAt;
    @Column(nullable = false)
    private Instant recordedAt;
    @Column(nullable = false)
    private Long orderId;
    @Column(nullable = false)
    private Long customerId;
    @Column(nullable = false, length = 100)
    private String customerName;
    @Column(nullable = false, length = 100)
    private String productName;
    @Column(nullable = false)
    private Integer quantity;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;
    @Column(nullable = false, precision = 22, scale = 2)
    private BigDecimal totalPrice;

    public AuditEntry(OrderCreated event) {
        this.eventId = event.eventId();
        this.occurredAt = event.occurredAt();
        this.recordedAt = Instant.now();
        this.orderId = event.orderId();
        this.customerId = event.customerId();
        this.customerName = event.customerName();
        this.productName = event.productName();
        this.quantity = event.quantity();
        this.unitPrice = event.unitPrice();
        this.totalPrice = event.totalPrice();
    }
}
