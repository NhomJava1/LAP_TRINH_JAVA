package com.octapoint.core.ledger;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerEnsureService {

    private final CustomerRepository customerRepository;

    public CustomerEnsureService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensureExists(String compositeId, String tenantId, String externalCustomerId) {
        if (customerRepository.existsById(compositeId)) {
            return;
        }
        try {
            Customer customer = new Customer();
            customer.setId(compositeId);
            customer.setTenantId(tenantId);
            customer.setExternalCustomerId(externalCustomerId);
            customer.setBalance(0L);
            customer.setChainSeq(-1L);
            customerRepository.saveAndFlush(customer);
        } catch (DataIntegrityViolationException ignored) {
            // Concurrent first-issue already created the row
        }
    }
}
