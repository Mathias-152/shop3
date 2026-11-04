package edu.arch.shop.order.service

import edu.arch.shop.customer.entity.Customer
import edu.arch.shop.customer.service.CustomerService
import edu.arch.shop.messaging.outbox.EventOutbox
import edu.arch.shop.contracts.OrderCreated
import edu.arch.shop.order.dto.OrderRequest
import edu.arch.shop.order.entity.Order
import edu.arch.shop.order.repository.OrderRepository
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import spock.lang.Specification
import spock.lang.Unroll

class OrderServiceSpec extends Specification {

    OrderRepository orderRepository = Mock()
    CustomerService customerService = Stub()
    EventOutbox eventOutbox = Mock()
    OrderService orderService = new OrderService(orderRepository, customerService, eventOutbox)

    def setup() {
        customerService.requireCustomer(1L) >> new Customer(id: 1L, name: "Jan Kowalski")
        customerService.requireCustomer(2L) >> new Customer(id: 2L, name: "Anna")
    }

    @Unroll
    def "create zapisuje zamówienie i zdarzenie oraz oblicza wartość #quantity * #unitPrice = #totalPrice"() {
        given:
        def request = new OrderRequest(1L, "Książka", quantity, unitPrice)

        when:
        def response = orderService.create(request)

        then:
        1 * orderRepository.save(_ as Order) >> { Order order ->
            assert order.customer.name == "Jan Kowalski"
            assert order.productName == "Książka"
            assert order.quantity == quantity
            assert order.unitPrice == unitPrice
            order.id = 10L
            order
        }
        1 * eventOutbox.append({ OrderCreated event ->
            event.orderId() == 10L && event.customerId() == 1L &&
                    event.totalPrice() == totalPrice && event.schemaVersion() == 1
        })
        response.customerName() == "Jan Kowalski"
        response.productName() == "Książka"
        response.quantity() == quantity
        response.unitPrice() == unitPrice
        response.totalPrice() == totalPrice

        where:
        quantity | unitPrice | totalPrice
        2        | 19.99G    | 39.98G
        3        | 0.10G     | 0.30G
        1        | 25.00G    | 25.00G
    }

    def "findAll zwraca dane i wyliczone wartości wszystkich zamówień"() {
        given:
        orderRepository.findAll() >> [
                new Order(new Customer(id: 1L, name: "Jan"), "Książka", 2, 19.99G),
                new Order(new Customer(id: 1L, name: "Anna"), "Notes", 3, 5.00G)
        ]

        when:
        def orders = orderService.findAll()

        then:
        orders.size() == 2
        orders[0].customerName() == "Jan"
        orders[0].totalPrice() == 39.98G
        orders[1].customerName() == "Anna"
        orders[1].totalPrice() == 15.00G
    }

    def "findAll zwraca pustą listę, gdy repozytorium nie zawiera zamówień"() {
        given:
        orderRepository.findAll() >> []

        expect:
        orderService.findAll().isEmpty()
    }

    def "findById zwraca dane zamówienia o podanym identyfikatorze"() {
        given:
        orderRepository.findById(1L) >> Optional.of(new Order(new Customer(id: 1L, name: "Jan"), "Książka", 2, 19.99G))

        when:
        def response = orderService.findById(1L)

        then:
        response.customerName() == "Jan"
        response.productName() == "Książka"
        response.totalPrice() == 39.98G
    }

    def "update zapisuje nowe dane zamówienia i przelicza jego wartość"() {
        given:
        def order = new Order(new Customer(id: 1L, name: "Jan"), "Książka", 2, 19.99G)
        def request = new OrderRequest(2L, "Notes", 3, 5.50G)
        orderRepository.findById(1L) >> Optional.of(order)

        when:
        def response = orderService.update(1L, request)

        then:
        1 * orderRepository.save(order) >> order
        order.customer.name == "Anna"
        order.productName == "Notes"
        order.quantity == 3
        order.unitPrice == 5.50G
        response.customerName() == "Anna"
        response.productName() == "Notes"
        response.quantity() == 3
        response.unitPrice() == 5.50G
        response.totalPrice() == 16.50G
    }

    def "delete usuwa zamówienie znalezione pod podanym identyfikatorem"() {
        given:
        def order = new Order(new Customer(id: 1L, name: "Jan"), "Książka", 2, 19.99G)
        orderRepository.findById(1L) >> Optional.of(order)

        when:
        orderService.delete(1L)

        then:
        1 * orderRepository.delete(order)
    }

    def "create odrzuca zerową liczbę sztuk błędem 400 i nie zapisuje zamówienia"() {
        given:
        def request = new OrderRequest(1L, "Notes", 0, 5.00G)

        when:
        orderService.create(request)

        then:
        def exception = thrown(ResponseStatusException)
        exception.statusCode == HttpStatus.BAD_REQUEST
        0 * orderRepository._
    }

    def "update odrzuca ujemną cenę błędem 400 i nie zapisuje zmian"() {
        given:
        def request = new OrderRequest(2L, "Notes", 2, -1.00G)

        when:
        orderService.update(1L, request)

        then:
        def exception = thrown(ResponseStatusException)
        exception.statusCode == HttpStatus.BAD_REQUEST
        0 * orderRepository._
    }

    def "findById zwraca błąd 404, gdy zamówienie nie istnieje"() {
        given:
        orderRepository.findById(99L) >> Optional.empty()

        when:
        orderService.findById(99L)

        then:
        def exception = thrown(ResponseStatusException)
        exception.statusCode == HttpStatus.NOT_FOUND
    }

    def "update zgłasza błąd i nie zapisuje zmian, gdy zamówienie nie istnieje"() {
        given:
        orderRepository.findById(99L) >> Optional.empty()

        when:
        orderService.update(99L, new OrderRequest(2L, "Notes", 1, 5.00G))

        then:
        thrown(ResponseStatusException)
        0 * orderRepository.save(_)
    }

    def "delete zgłasza błąd bez usuwania danych, gdy zamówienie nie istnieje"() {
        given:
        orderRepository.findById(99L) >> Optional.empty()

        when:
        orderService.delete(99L)

        then:
        thrown(ResponseStatusException)
        0 * orderRepository.delete(_)
    }
}
