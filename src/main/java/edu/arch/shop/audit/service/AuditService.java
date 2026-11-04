package edu.arch.shop.audit.service;

import edu.arch.shop.audit.dto.AuditResponse;
import edu.arch.shop.audit.entity.AuditEntry;
import edu.arch.shop.audit.repository.AuditRepository;
import edu.arch.shop.contracts.OrderCreated;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class AuditService {
    private final AuditRepository repository;

    public AuditService(AuditRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void record(OrderCreated event) {
        if (event.schemaVersion() != 1 || event.eventId() == null) {
            throw new IllegalArgumentException("Unsupported or invalid OrderCreated event");
        }
        if (!repository.existsById(event.eventId())) {
            repository.saveAndFlush(new AuditEntry(event));
        }
    }

    public List<AuditResponse> findByOrderId(Long orderId) {
        return repository.findByOrderIdOrderByOccurredAtAsc(orderId).stream()
                .map(entry -> new AuditResponse(entry.getEventId(), entry.getOccurredAt(), entry.getRecordedAt(),
                        entry.getOrderId(), entry.getCustomerId(), entry.getCustomerName(), entry.getProductName(),
                        entry.getQuantity(), entry.getUnitPrice(), entry.getTotalPrice()))
                .toList();
    }
}
