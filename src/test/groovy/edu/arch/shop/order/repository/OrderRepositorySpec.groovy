package edu.arch.shop.order.repository

import edu.arch.shop.customer.entity.Customer
import edu.arch.shop.order.entity.Order
import jakarta.persistence.EntityManager
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import spock.lang.Specification

@DataJpaTest
class OrderRepositorySpec extends Specification {

    @Autowired
    OrderRepository orderRepository

    @Autowired
    EntityManager entityManager

    private Customer customer(String name) {
        def customer = new Customer(name: name)
        entityManager.persist(customer)
        customer
    }

    def "zapisane zamówienie otrzymuje identyfikator i można je odczytać z bazy"() {
        when:
        def saved = orderRepository.saveAndFlush(new Order(customer("Jan Kowalski"), "Książka", 2, 19.99G))
        entityManager.clear()
        def loaded = orderRepository.findById(saved.id).orElseThrow()

        then:
        loaded.id != null
        loaded.customer.name == "Jan Kowalski"
        loaded.productName == "Książka"
        loaded.quantity == 2
        loaded.unitPrice == 19.99G
    }

    def "zapis i odczyt zamówienia nie zmieniają ceny 9999999999.99"() {
        when:
        def saved = orderRepository.saveAndFlush(new Order(customer("Anna"), "Produkt", 1, 9999999999.99G))
        entityManager.clear()

        then:
        orderRepository.findById(saved.id).orElseThrow().unitPrice == 9999999999.99G
    }

    def "usuniętego zamówienia nie można odczytać po identyfikatorze"() {
        given:
        def order = orderRepository.saveAndFlush(new Order(customer("Jan"), "Notes", 1, 5.00G))

        when:
        orderRepository.delete(order)
        orderRepository.flush()
        entityManager.clear()

        then:
        orderRepository.findById(order.id).isEmpty()
    }

    def "zamówienia przypisane do jednego klienta zachowują to powiązanie po odczycie"() {
        given:
        def customer = new Customer(name: "Jan Kowalski")
        entityManager.persist(customer)

        def firstOrder = new Order(customer, "Książka", 2, 19.99G)
        def secondOrder = new Order(customer, "Notes", 1, 5.00G)

        when:
        orderRepository.saveAndFlush(firstOrder)
        orderRepository.saveAndFlush(secondOrder)
        entityManager.clear()

        then:
        def loadedFirst = orderRepository.findById(firstOrder.id).orElseThrow()
        def loadedSecond = orderRepository.findById(secondOrder.id).orElseThrow()
        loadedFirst.customer.id == customer.id
        loadedSecond.customer.id == customer.id
        loadedFirst.customer.name == "Jan Kowalski"
        loadedSecond.customer.name == "Jan Kowalski"
    }
}
