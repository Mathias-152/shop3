package edu.arch.shop.audit.controller;

import edu.arch.shop.audit.dto.AuditResponse;
import edu.arch.shop.audit.service.AuditService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/audit/orders")
public class AuditController {
    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/{orderId}")
    public List<AuditResponse> findByOrderId(@PathVariable Long orderId) {
        return auditService.findByOrderId(orderId);
    }
}
