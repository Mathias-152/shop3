package edu.arch.shop.support

import edu.arch.shop.audit.repository.AuditRepository
import edu.arch.shop.customer.repository.CustomerRepository
import edu.arch.shop.messaging.outbox.OutboxRepository
import edu.arch.shop.order.repository.OrderRepository
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.web.servlet.MockMvc
import spock.lang.Specification

@SpringBootTest(properties = [
        "shop.outbox.scheduling-enabled=false",
        "shop.kafka.order-created.topic=shop.order-created.integration",
        "shop.kafka.order-created.partitions=4",
        "spring.datasource.url=jdbc:h2:mem:shop-integration;DB_CLOSE_DELAY=-1",
        "spring.jpa.show-sql=false",
        "logging.level.org.apache.kafka=ERROR",
        "logging.level.kafka=ERROR",
        "logging.level.org.springframework.kafka=ERROR"
])
@AutoConfigureMockMvc
@EmbeddedKafka(bootstrapServersProperty = "spring.kafka.bootstrap-servers")
abstract class IntegrationSpec extends Specification {
    @Autowired MockMvc mockMvc
    @Autowired OrderRepository orderRepository
    @Autowired CustomerRepository customerRepository
    @Autowired OutboxRepository outboxRepository
    @Autowired AuditRepository auditRepository

    def setup() {
        outboxRepository.deleteAll()
        orderRepository.deleteAll()
        customerRepository.deleteAll()
        auditRepository.deleteAll()
    }

    protected static String json(Object value) {
        JsonOutput.toJson(value)
    }

    protected static def body(result) {
        new JsonSlurper().parseText(result.response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
    }
}
