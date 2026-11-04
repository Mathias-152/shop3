package edu.arch.shop.customer.service;

import edu.arch.shop.customer.dto.CustomerRequest;
import edu.arch.shop.customer.dto.CustomerResponse;
import edu.arch.shop.customer.entity.Customer;
import edu.arch.shop.customer.repository.CustomerRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class CustomerService {
    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public List<CustomerResponse> findAll() {
        return customerRepository.findAll().stream().map(this::toResponse).toList();
    }

    public CustomerResponse findById(Long id) {
        return toResponse(requireCustomer(id));
    }

    public Customer requireCustomer(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));
    }

    @Transactional
    public CustomerResponse create(CustomerRequest request) {
        validate(request);
        Customer customer = new Customer();
        customer.setName(request.name().trim());
        return toResponse(customerRepository.save(customer));
    }

    @Transactional
    public CustomerResponse update(Long id, CustomerRequest request) {
        validate(request);
        Customer customer = requireCustomer(id);
        customer.setName(request.name().trim());
        return toResponse(customerRepository.save(customer));
    }

    @Transactional
    public void delete(Long id) {
        Customer customer = requireCustomer(id);
        try {
            customerRepository.delete(customer);
            customerRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Customer has orders", ex);
        }
    }

    private void validate(CustomerRequest request) {
        if (request.name() == null || request.name().isBlank() || request.name().length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name must contain 1 to 100 characters");
        }
    }

    private CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(customer.getId(), customer.getName());
    }
}
