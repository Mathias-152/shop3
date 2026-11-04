package edu.arch.shop.messaging.outbox;

import edu.arch.shop.contracts.OrderCreated;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Component
public class EventOutbox {
    private final OutboxRepository repository;
    private final ObjectMapper objectMapper;

    public EventOutbox(OutboxRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(OrderCreated event) {
        repository.save(new OutboxMessage(event.eventId(), event.orderId().toString(),
                objectMapper.writeValueAsString(event)));
    }
}
