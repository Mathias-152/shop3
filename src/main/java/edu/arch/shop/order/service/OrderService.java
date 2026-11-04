package edu.arch.shop.order.service;

import edu.arch.shop.contracts.OrderCreated;
import edu.arch.shop.customer.service.CustomerService;
import edu.arch.shop.messaging.outbox.EventOutbox;
import edu.arch.shop.order.dto.OrderRequest;
import edu.arch.shop.order.dto.OrderResponse;
import edu.arch.shop.order.entity.Order;
import edu.arch.shop.order.repository.OrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final CustomerService customerService;
    private final EventOutbox eventOutbox;

    public OrderService(OrderRepository orderRepository, CustomerService customerService, EventOutbox eventOutbox) {
        this.orderRepository = orderRepository;
        this.customerService = customerService;
        this.eventOutbox = eventOutbox;
    }

    public List<OrderResponse> findAll() {
        return orderRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public OrderResponse findById(Long id) {
        return toResponse(findOrder(id));
    }

    @Transactional
    public OrderResponse create(OrderRequest request) {
        validate(request);
        Order order = new Order(
                customerService.requireCustomer(request.customerId()),
                request.productName(),
                request.quantity(),
                request.unitPrice()
        );
        OrderResponse response = toResponse(orderRepository.save(order));
        // Zamówienie i zdarzenie zapisujemy w tej samej transakcji.
        eventOutbox.append(new OrderCreated(UUID.randomUUID(), 1, Instant.now(),
                response.id(), response.customerId(), response.customerName(),
                response.productName(), response.quantity(), response.unitPrice(), response.totalPrice()));
        return response;
    }

    @Transactional
    public OrderResponse update(Long id, OrderRequest request) {
        validate(request);
        Order order = findOrder(id);
        order.setCustomer(customerService.requireCustomer(request.customerId()));
        order.setProductName(request.productName());
        order.setQuantity(request.quantity());
        order.setUnitPrice(request.unitPrice());
        return toResponse(orderRepository.save(order));
    }

    @Transactional
    public void delete(Long id) {
        Order order = findOrder(id);
        orderRepository.delete(order);
    }

    private Order findOrder(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private void validate(OrderRequest request) {
        if (request.customerId() == null || request.customerId() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Positive customer id is required");
        }
        if (request.productName() == null || request.productName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product name is required");
        }
        if (request.productName().length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product name is too long");
        }
        if (request.quantity() == null || request.quantity() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity must be positive");
        }
        if (request.unitPrice() == null || request.unitPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unit price must be positive");
        }
        if (request.unitPrice().scale() > 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unit price can have at most 2 decimal places");
        }
        if (request.unitPrice().compareTo(new BigDecimal("9999999999.99")) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unit price is too high");
        }
    }

    private OrderResponse toResponse(Order order) {
        BigDecimal totalPrice = order.getUnitPrice()
                .multiply(BigDecimal.valueOf(order.getQuantity()));

        return new OrderResponse(
                order.getId(),
                order.getCustomer().getId(),
                order.getCustomer().getName(),
                order.getProductName(),
                order.getQuantity(),
                order.getUnitPrice(),
                totalPrice
        );
    }
}
