package edu.arch.shop.lab.controller;

import edu.arch.shop.customer.dto.CustomerRequest;
import edu.arch.shop.customer.dto.CustomerResponse;
import edu.arch.shop.customer.service.CustomerService;
import edu.arch.shop.order.dto.OrderRequest;
import edu.arch.shop.order.dto.OrderResponse;
import edu.arch.shop.order.service.OrderService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;

@RestController
@RequestMapping("/lab")
@Profile("local")
public class LabController {
    private final CustomerService customerService;
    private final OrderService orderService;

    public LabController(CustomerService customerService, OrderService orderService) {
        this.customerService = customerService;
        this.orderService = orderService;
    }

    @GetMapping("/create-customer")
    public ResponseEntity<CustomerResponse> createCustomer(
            @RequestParam(defaultValue = "Jan Kowalski") String name) {
        var customer = customerService.create(new CustomerRequest(name));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(customer);
    }

    @GetMapping("/create-orders")
    public ResponseEntity<List<OrderResponse>> createOrders(@RequestParam Long customerId,
                                                           @RequestParam(defaultValue = "5") int count) {
        if (count < 1 || count > 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Count must be between 1 and 20");
        }
        var orders = IntStream.range(0, count)
                .mapToObj(index -> orderService.create(
                        new OrderRequest(customerId, "Notes", 1, new BigDecimal("5.00"))))
                .toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(orders);
    }
}
