package edu.arch.shop.messaging.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private final OutboxRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String orderCreatedTopic;

    public OutboxPublisher(OutboxRepository repository, KafkaTemplate<String, String> kafkaTemplate,
                           @Value("${shop.kafka.order-created.topic}") String orderCreatedTopic) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.orderCreatedTopic = orderCreatedTopic;
    }

    @Transactional
    public void publishPending() {
        for (OutboxMessage message : repository.findTop20ByOrderByIdAsc()) {
            try {
                // Rekord usuwamy dopiero po potwierdzeniu publikacji.
                kafkaTemplate.send(orderCreatedTopic, message.getMessageKey(), message.getPayload())
                        .get(5, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            } catch (ExecutionException | TimeoutException | RuntimeException ex) {
                log.warn("Event {} remains in outbox; publication will be retried", message.getEventId(), ex);
                return;
            }
            // Awaria przed commitem może spowodować ponowną publikację tego samego zdarzenia.
            repository.delete(message);
        }
    }
}
