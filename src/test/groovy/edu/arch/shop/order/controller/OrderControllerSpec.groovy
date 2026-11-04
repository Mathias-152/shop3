package edu.arch.shop.order.controller

import edu.arch.shop.support.IntegrationSpec
import edu.arch.shop.customer.entity.Customer
import edu.arch.shop.order.entity.Order
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import spock.lang.Unroll

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class OrderControllerSpec extends IntegrationSpec {

    Customer customer
    Customer otherCustomer

    def setup() {
        customer = customerRepository.save(new Customer(name: "Jan Kowalski"))
        otherCustomer = customerRepository.save(new Customer(name: "Anna Nowak"))
    }

    def "zamówienie utworzone przez API można odczytać, zaktualizować i usunąć"() {
        when: "klient tworzy zamówienie"
        def result = mockMvc.perform(post("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonOutput.toJson(validOrder())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath('$.customerName').value("Jan Kowalski"))
                .andExpect(jsonPath('$.productName').value("Książka"))
                .andExpect(jsonPath('$.quantity').value(2))
                .andExpect(jsonPath('$.unitPrice').value(19.99d))
                .andExpect(jsonPath('$.totalPrice').value(39.98d))
                .andReturn()
        def body = new JsonSlurper().parseText(result.response.contentAsString)
        Long id = body.id as Long

        then:
        id > 0
        result.response.getHeader("Location") == "/orders/$id"
        orderRepository.findById(id).orElseThrow().quantity == 2

        when: "klient pobiera zamówienie i listę"
        mockMvc.perform(get("/orders/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.id').value(id.intValue()))
                .andExpect(jsonPath('$.totalPrice').value(39.98d))
        mockMvc.perform(get("/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.length()').value(1))
                .andExpect(jsonPath('$[0].id').value(id.intValue()))

        then:
        noExceptionThrown()

        when: "klient zmienia dane zamówienia"
        def updated = [customerId: otherCustomer.id, productName: "Notes", quantity: 3, unitPrice: 5.50]
        mockMvc.perform(put("/orders/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonOutput.toJson(updated)))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.id').value(id.intValue()))
                .andExpect(jsonPath('$.customerName').value("Anna Nowak"))
                .andExpect(jsonPath('$.productName').value("Notes"))
                .andExpect(jsonPath('$.quantity').value(3))
                .andExpect(jsonPath('$.unitPrice').value(5.50d))
                .andExpect(jsonPath('$.totalPrice').value(16.50d))

        then: "zmiany są zapisane w bazie"
        def stored = orderRepository.findById(id).orElseThrow()
        stored.customer.id == otherCustomer.id
        stored.productName == "Notes"
        stored.quantity == 3
        stored.unitPrice == 5.50G
        orderRepository.count() == 1

        when: "klient usuwa zamówienie"
        mockMvc.perform(delete("/orders/{id}", id))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""))
        mockMvc.perform(get("/orders/{id}", id))
                .andExpect(status().isNotFound())

        then:
        orderRepository.findById(id).isEmpty()
    }

    def "GET /orders zwraca pustą listę, gdy nie ma zamówień"() {
        expect:
        mockMvc.perform(get("/orders"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"))
    }

    @Unroll
    def "#method /orders/{id} zwraca 404, gdy zamówienie nie istnieje"() {
        expect:
        mockMvc.perform(request(HttpMethod.valueOf(method), "/orders/999")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonOutput.toJson(validOrder())))
                .andExpect(status().isNotFound())

        where:
        method << ["GET", "PUT", "DELETE"]
    }

    @Unroll
    def "tworzenie i aktualizacja zamówienia zwracają 400 dla #field = #value"() {
        given:
        def existing = orderRepository.save(new Order(customer, "Książka", 2, 19.99G))
        def invalid = validOrder()
        invalid[field] = value
        def json = JsonOutput.toJson(invalid)

        expect:
        mockMvc.perform(post("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isBadRequest())
        mockMvc.perform(put("/orders/{id}", existing.id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isBadRequest())
        orderRepository.count() == 1
        def stored = orderRepository.findById(existing.id).orElseThrow()
        outboxRepository.count() == 0
        stored.customer.id == customer.id
        stored.productName == "Książka"
        stored.quantity == 2
        stored.unitPrice == 19.99G

        where:
        field          | value
        "customerId"   | 0
        "customerId"   | null
        "customerId"   | -1
        "productName"  | ""
        "productName"  | null
        "productName"  | "a" * 101
        "quantity"     | 0
        "quantity"     | -1
        "quantity"     | null
        "unitPrice"    | 0
        "unitPrice"    | -1.00
        "unitPrice"    | null
        "unitPrice"    | 1.001
        "unitPrice"    | 10000000000.00
    }

    private Map validOrder() {
        [customerId: customer.id, productName: "Książka", quantity: 2, unitPrice: 19.99]
    }
}
