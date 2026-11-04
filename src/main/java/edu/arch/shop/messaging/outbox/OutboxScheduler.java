package edu.arch.shop.messaging.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "shop.outbox.scheduling-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxScheduler {
    private final OutboxPublisher publisher;

    public OutboxScheduler(OutboxPublisher publisher) {
        this.publisher = publisher;
    }

    @Scheduled(fixedDelayString = "${shop.outbox.delay-ms:1000}")
    public void publish() {
        publisher.publishPending();
    }
}
