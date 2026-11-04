package edu.arch.shop.audit.repository;

import edu.arch.shop.audit.entity.AuditEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface AuditRepository extends JpaRepository<AuditEntry, UUID> {
    List<AuditEntry> findByOrderIdOrderByOccurredAtAsc(Long orderId);
}
