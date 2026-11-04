package edu.arch.shop.audit.messaging;

import edu.arch.shop.audit.service.AuditService;
import edu.arch.shop.contracts.OrderCreated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;

@Component
public class OrderCreatedListener {
    private static final Logger log = LoggerFactory.getLogger(OrderCreatedListener.class);
    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    public OrderCreatedListener(ObjectMapper objectMapper, AuditService auditService) {
        this.objectMapper = objectMapper;
        this.auditService = auditService;
    }

    @KafkaListener(id = "audit-orders", topics = "${shop.kafka.order-created.topic}", groupId = "shop-audit-v1")
    public void onOrderCreated(ConsumerRecord<String, String> record) {
        auditService.record(objectMapper.readValue(record.value(), OrderCreated.class));
        log.info(
                "topic={} key={} partition={} offset={}",
                record.topic(),
                record.key(),
                record.partition(),
                record.offset()
        );
    }
}
