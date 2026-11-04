package edu.arch.shop.lab.controller

import edu.arch.shop.customer.entity.Customer
import edu.arch.shop.messaging.outbox.OutboxPublisher
import edu.arch.shop.support.IntegrationSpec
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import spock.util.concurrent.PollingConditions

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@ActiveProfiles("local")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:shop-lab;DB_CLOSE_DELAY=-1")
class LabControllerSpec extends IntegrationSpec {
    @Autowired OutboxPublisher publisher

    def "zamówienia utworzone przez przeglądarkę trafiają przez Kafkę do audytu"() {
        given:
        def customerResult = mockMvc.perform(get("/lab/create-customer"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andReturn()
        long customerId = body(customerResult).id

        when:
        def result = mockMvc.perform(get("/lab/create-orders")
                .param("customerId", customerId.toString()).param("count", "3"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andReturn()
        def orders = body(result)

        then:
        customerRepository.findById(customerId).orElseThrow().name == "Jan Kowalski"
        orders.size() == 3
        orders*.id.unique().size() == 3
        orders.every { it.customerId == customerId && it.productName == "Notes" }
        orderRepository.count() == 3
        outboxRepository.count() == 3
        auditRepository.count() == 0

        when:
        publisher.publishPending()

        then:
        outboxRepository.count() == 0
        new PollingConditions(timeout: 15, delay: 0.1).eventually {
            assert auditRepository.count() == 3
            assert auditRepository.findAll()*.orderId.toSet() == orders*.id.toSet()
        }
    }

    def "liczba zamówień spoza zakresu 1–20 jest odrzucana: #count"() {
        given:
        def customer = customerRepository.save(new Customer(name: "Jan"))

        expect:
        mockMvc.perform(get("/lab/create-orders")
                .param("customerId", customer.id.toString()).param("count", count.toString()))
                .andExpect(status().isBadRequest())
        orderRepository.count() == 0
        outboxRepository.count() == 0

        where:
        count << [-1, 0, 21]
    }

    def "tworzenie zamówień dla nieistniejącego klienta zwraca 404"() {
        expect:
        mockMvc.perform(get("/lab/create-orders").param("customerId", "999999").param("count", "5"))
                .andExpect(status().isNotFound())
        orderRepository.count() == 0
        outboxRepository.count() == 0
    }
}
