package edu.arch.shop.messaging

import edu.arch.shop.contracts.OrderCreated
import edu.arch.shop.customer.entity.Customer
import edu.arch.shop.messaging.outbox.OutboxPublisher
import edu.arch.shop.order.dto.OrderRequest
import edu.arch.shop.order.service.OrderService
import edu.arch.shop.support.IntegrationSpec
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.config.KafkaListenerEndpointRegistry
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.test.EmbeddedKafkaBroker
import org.springframework.kafka.test.utils.ContainerTestUtils
import org.springframework.kafka.test.utils.KafkaTestUtils
import org.springframework.http.MediaType
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import spock.util.concurrent.PollingConditions
import tools.jackson.databind.ObjectMapper

import java.util.concurrent.TimeUnit
import java.time.Duration

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class OrderEventsIntegrationSpec extends IntegrationSpec {
    @Autowired OutboxPublisher publisher
    @Autowired OrderService orderService
    @Autowired KafkaTemplate<String, String> kafkaTemplate
    @Autowired KafkaListenerEndpointRegistry registry
    @Autowired EmbeddedKafkaBroker broker
    @Autowired PlatformTransactionManager transactionManager
    @Autowired ObjectMapper objectMapper
    @Value('${shop.kafka.order-created.topic}') String orderCreatedTopic
    @Value('${shop.kafka.order-created.partitions}') int orderCreatedPartitions

    PollingConditions eventually = new PollingConditions(timeout: 15, delay: 0.1)
    Customer customer

    def setup() {
        customer = customerRepository.save(new Customer(name: "Jan Kowalski"))
        ContainerTestUtils.waitForAssignment(registry.getListenerContainer("audit-orders"), orderCreatedPartitions)
    }

    def "audyt zapisuje dane zamówienia po odebraniu zdarzenia OrderCreated"() {
        when:
        def result = mockMvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON)
                .content(json([customerId: customer.id, productName: "Książka", quantity: 2, unitPrice: 19.99])))
                .andExpect(status().isCreated()).andReturn()
        long id = body(result).id
        def pending = onlyOutboxMessage()
        def event = objectMapper.readValue(pending.payload, OrderCreated)

        then:
        pending.messageKey == id.toString()
        event.orderId() == id
        event.customerId() == customer.id
        event.customerName() == "Jan Kowalski"
        event.schemaVersion() == 1
        event.eventId() != null
        event.occurredAt() != null
        event.productName() == "Książka"
        event.quantity() == 2
        event.unitPrice() == 19.99G
        event.totalPrice() == 39.98G
        auditRepository.count() == 0

        when:
        publisher.publishPending()

        then:
        outboxRepository.count() == 0
        eventually.eventually {
            assert auditRepository.existsById(event.eventId())
        }
        mockMvc.perform(get("/audit/orders/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath('$.length()').value(1))
                .andExpect(jsonPath('$[0].customerName').value("Jan Kowalski"))
                .andExpect(jsonPath('$[0].totalPrice').value(39.98d))
    }

    def "po rollbacku w bazie nie ma zamówienia ani zdarzenia do publikacji"() {
        when:
        new TransactionTemplate(transactionManager).executeWithoutResult { status ->
            orderService.create(new OrderRequest(customer.id, "Notes", 1, 5.00G))
            assert orderRepository.count() == 1
            assert outboxRepository.count() == 1
            status.setRollbackOnly()
        }
        publisher.publishPending()

        then:
        orderRepository.count() == 0
        outboxRepository.count() == 0
        auditRepository.count() == 0
    }

    def "po wznowieniu konsument przetwarza zdarzenie wysłane podczas jego zatrzymania"() {
        given:
        def listener = registry.getListenerContainer("audit-orders")
        listener.stop()

        when:
        def result = mockMvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON)
                .content(json([customerId: customer.id, productName: "Notes", quantity: 1, unitPrice: 5.00])))
                .andExpect(status().isCreated()).andReturn()
        long id = body(result).id
        publisher.publishPending()

        then:
        outboxRepository.count() == 0
        mockMvc.perform(get("/orders/{id}", id)).andExpect(status().isOk())
        mockMvc.perform(get("/audit/orders/{id}", id)).andExpect(status().isOk())
                .andExpect(content().json("[]"))

        when:
        listener.start()

        then:
        eventually.eventually {
            assert auditRepository.findByOrderIdOrderByOccurredAtAsc(id).size() == 1
        }

        cleanup:
        if (!listener.isRunning()) listener.start()
    }

    def "ponowny odbiór zdarzenia z tym samym eventId nie dodaje wpisu w audycie"() {
        given:
        orderService.create(new OrderRequest(customer.id, "Notes", 1, 5.00G))
        def message = onlyOutboxMessage()
        publisher.publishPending()
        eventually.eventually { assert auditRepository.existsById(message.eventId) }
        def recordedAt = auditRepository.findById(message.eventId).orElseThrow().recordedAt

        when:
        def duplicate = kafkaTemplate.send(orderCreatedTopic, message.messageKey, message.payload)
                .get(10, TimeUnit.SECONDS)
        awaitCommitted(duplicate.recordMetadata.topic(), duplicate.recordMetadata.partition(),
                duplicate.recordMetadata.offset())

        then: "offset duplikatu został zatwierdzony, więc sprawdzamy efekt po jego obsłużeniu"
        auditRepository.count() == 1
        auditRepository.findById(message.eventId).orElseThrow().recordedAt == recordedAt
    }

    def "zmiana i usunięcie zamówienia nie zmieniają danych zapisanych w audycie"() {
        given:
        def order = orderService.create(new OrderRequest(customer.id, "Książka", 2, 19.99G))
        publisher.publishPending()
        eventually.eventually { assert auditRepository.count() == 1 }

        when:
        mockMvc.perform(put("/customers/{id}", customer.id).contentType(MediaType.APPLICATION_JSON)
                .content(json([name: "Nowa nazwa"]))).andExpect(status().isOk())
        mockMvc.perform(put("/orders/{id}", order.id()).contentType(MediaType.APPLICATION_JSON)
                .content(json([customerId: customer.id, productName: "Notes", quantity: 3, unitPrice: 5.00])))
                .andExpect(status().isOk()).andExpect(jsonPath('$.totalPrice').value(15.00d))
        mockMvc.perform(delete("/orders/{id}", order.id())).andExpect(status().isNoContent())
        mockMvc.perform(delete("/customers/{id}", customer.id)).andExpect(status().isNoContent())

        then:
        outboxRepository.count() == 0
        mockMvc.perform(get("/audit/orders/{id}", order.id())).andExpect(status().isOk())
                .andExpect(jsonPath('$.length()').value(1))
                .andExpect(jsonPath('$[0].customerName').value("Jan Kowalski"))
                .andExpect(jsonPath('$[0].productName').value("Książka"))
                .andExpect(jsonPath('$[0].totalPrice').value(39.98d))
    }

    def "po błędzie publikacji zdarzenie pozostaje w outboxie i zostaje wysłane przy ponowieniu"() {
        given:
        orderService.create(new OrderRequest(customer.id, "Notes", 1, 5.00G))
        def eventId = onlyOutboxMessage().eventId
        // Port jest zajęty, ale nie działa na nim broker Kafka.
        def unavailable = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        def factory = new DefaultKafkaProducerFactory<String, String>([
                (ProducerConfig.BOOTSTRAP_SERVERS_CONFIG): "127.0.0.1:${unavailable.localPort}",
                (ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG): StringSerializer,
                (ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG): StringSerializer,
                (ProducerConfig.MAX_BLOCK_MS_CONFIG): 500,
                (ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG): 300,
                (ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG): 1000
        ])
        def failedPublisher = new OutboxPublisher(outboxRepository, new KafkaTemplate<String, String>(factory),
                orderCreatedTopic)

        when:
        new TransactionTemplate(transactionManager).executeWithoutResult { failedPublisher.publishPending() }

        then:
        orderRepository.count() == 1
        onlyOutboxMessage().eventId == eventId
        auditRepository.count() == 0

        when: "ten sam rekord trafia teraz do działającego brokera"
        publisher.publishPending()

        then:
        outboxRepository.count() == 0
        eventually.eventually { assert auditRepository.existsById(eventId) }

        cleanup:
        factory?.destroy()
        unavailable?.close()
    }

    def "konsumenci z różnych grup otrzymują to samo zdarzenie"() {
        given:
        def first = observer("first")
        def second = observer("second")
        first.subscribe([orderCreatedTopic])
        second.subscribe([orderCreatedTopic])
        eventually.eventually {
            first.poll(Duration.ofMillis(100))
            second.poll(Duration.ofMillis(100))
            assert first.assignment().size() == orderCreatedPartitions
            assert second.assignment().size() == orderCreatedPartitions
        }
        first.assignment().each { first.position(it) }
        second.assignment().each { second.position(it) }
        def order = orderService.create(new OrderRequest(customer.id, "Notes", 1, 5.00G))

        when:
        publisher.publishPending()
        def firstRecord = KafkaTestUtils.getSingleRecord(first, orderCreatedTopic)
        def secondRecord = KafkaTestUtils.getSingleRecord(second, orderCreatedTopic)

        then:
        firstRecord.key() == order.id().toString()
        firstRecord.value() == secondRecord.value()
        eventually.eventually { assert auditRepository.count() == 1 }

        cleanup:
        first?.close()
        second?.close()
    }

    private def onlyOutboxMessage() {
        def messages = outboxRepository.findAll()
        assert messages.size() == 1
        messages.first()
    }

    private def observer(String label) {
        def props = KafkaTestUtils.consumerProps(broker,
                "observer-$label-${UUID.randomUUID()}".toString(), false)
        props["auto.offset.reset"] = "latest"
        new DefaultKafkaConsumerFactory<String, String>(props, new StringDeserializer(), new StringDeserializer())
                .createConsumer()
    }

    private void awaitCommitted(String topic, int partition, long offset) {
        def admin = AdminClient.create([(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG): broker.brokersAsString])
        try {
            eventually.eventually {
                def offsets = admin.listConsumerGroupOffsets("shop-audit-v1").partitionsToOffsetAndMetadata()
                        .get(5, TimeUnit.SECONDS)
                assert offsets[new TopicPartition(topic, partition)]?.offset() > offset
            }
        } finally {
            admin.close()
        }
    }
}
