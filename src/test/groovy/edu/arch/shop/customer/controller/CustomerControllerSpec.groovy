package edu.arch.shop.customer.controller

import edu.arch.shop.customer.entity.Customer
import edu.arch.shop.order.dto.OrderRequest
import edu.arch.shop.order.service.OrderService
import edu.arch.shop.support.IntegrationSpec
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import spock.lang.Unroll

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class CustomerControllerSpec extends IntegrationSpec {
    @Autowired OrderService orderService

    def "klienta utworzonego przez API można odczytać, zaktualizować i usunąć"() {
        when:
        def result = mockMvc.perform(post("/customers").contentType(MediaType.APPLICATION_JSON)
                .content(json([name: "  Jan Kowalski  "])))
                .andExpect(status().isCreated()).andReturn()
        long id = body(result).id

        then:
        result.response.getHeader("Location") == "/customers/$id"
        body(result).name == "Jan Kowalski"
        orderRepository.count() == 0
        outboxRepository.count() == 0

        when:
        mockMvc.perform(get("/customers/{id}", id))
                .andExpect(status().isOk()).andExpect(jsonPath('$.name').value("Jan Kowalski"))
        mockMvc.perform(get("/customers"))
                .andExpect(status().isOk()).andExpect(jsonPath('$.length()').value(1))
        mockMvc.perform(put("/customers/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content(json([name: "Anna"])))
                .andExpect(status().isOk()).andExpect(jsonPath('$.name').value("Anna"))
        mockMvc.perform(delete("/customers/{id}", id)).andExpect(status().isNoContent())
        mockMvc.perform(get("/customers/{id}", id)).andExpect(status().isNotFound())

        then:
        customerRepository.count() == 0
    }

    @Unroll
    def "tworzenie i aktualizacja klienta zwracają 400 dla nieprawidłowej nazwy: #name"() {
        given:
        def existing = customerRepository.save(new Customer(name: "Jan"))

        expect:
        mockMvc.perform(post("/customers").contentType(MediaType.APPLICATION_JSON)
                .content(json([name: name]))).andExpect(status().isBadRequest())
        mockMvc.perform(put("/customers/{id}", existing.id).contentType(MediaType.APPLICATION_JSON)
                .content(json([name: name]))).andExpect(status().isBadRequest())
        customerRepository.count() == 1
        customerRepository.findById(existing.id).orElseThrow().name == "Jan"

        where:
        name << [null, "", " ", "a" * 101]
    }

    def "GET, PUT i DELETE nieistniejącego klienta zwracają 404"() {
        expect:
        mockMvc.perform(get("/customers")).andExpect(status().isOk()).andExpect(content().json("[]"))
        mockMvc.perform(get("/customers/999999")).andExpect(status().isNotFound())
        mockMvc.perform(put("/customers/999999").contentType(MediaType.APPLICATION_JSON)
                .content(json([name: "Jan"]))).andExpect(status().isNotFound())
        mockMvc.perform(delete("/customers/999999")).andExpect(status().isNotFound())
    }

    def "usunięcie klienta zwraca 409, dopóki klient ma zamówienia"() {
        given:
        def customer = customerRepository.save(new Customer(name: "Jan"))
        def order = orderService.create(new OrderRequest(customer.id, "Notes", 1, 5.00G))

        when:
        mockMvc.perform(delete("/customers/{id}", customer.id)).andExpect(status().isConflict())

        then:
        customerRepository.existsById(customer.id)
        orderRepository.existsById(order.id())

        when:
        mockMvc.perform(delete("/orders/{id}", order.id())).andExpect(status().isNoContent())
        mockMvc.perform(delete("/customers/{id}", customer.id)).andExpect(status().isNoContent())

        then:
        !customerRepository.existsById(customer.id)
    }

    def "zamówienie dla nieistniejącego klienta jest odrzucane statusem 404"() {
        expect:
        mockMvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON)
                .content(json([customerId: 999999, productName: "Notes", quantity: 1, unitPrice: 5.00])))
                .andExpect(status().isNotFound())
        orderRepository.count() == 0
        outboxRepository.count() == 0
        auditRepository.count() == 0
    }
}
