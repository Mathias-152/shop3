package edu.arch.shop.lab.controller

import edu.arch.shop.support.IntegrationSpec

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class LabControllerProfileSpec extends IntegrationSpec {
    def "adresy do tworzenia danych laboratoryjnych są niedostępne bez profilu local"() {
        expect:
        mockMvc.perform(get("/lab/create-customer")).andExpect(status().isNotFound())
        mockMvc.perform(get("/lab/create-orders").param("customerId", "1"))
                .andExpect(status().isNotFound())
        customerRepository.count() == 0
        orderRepository.count() == 0
        outboxRepository.count() == 0
    }
}
