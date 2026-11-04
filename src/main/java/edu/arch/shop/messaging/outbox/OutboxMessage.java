package edu.arch.shop.messaging.outbox;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "outbox_messages")
@Getter
@NoArgsConstructor
public class OutboxMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private UUID eventId;
    @Column(nullable = false)
    private String messageKey;
    @Lob
    @Column(nullable = false)
    private String payload;

    public OutboxMessage(UUID eventId, String messageKey, String payload) {
        this.eventId = eventId;
        this.messageKey = messageKey;
        this.payload = payload;
    }
}
