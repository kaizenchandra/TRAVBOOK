package com.travbook.customer.application.usecase;
import com.travbook.customer.application.port.in.Customers;
import com.travbook.customer.application.port.out.CustomerRepository;
import com.travbook.customer.domain.model.Customer;
import com.travbook.shared.domain.CustomerIdentity;
import java.time.Clock;
public final class CustomerService implements Customers {
    private final CustomerRepository repository; private final Clock clock;
    public CustomerService(CustomerRepository repository, Clock clock) { this.repository=repository; this.clock=clock; }
    public Customer resolve(CustomerIdentity identity) { return repository.findOrCreate(identity,clock.instant()); }
}

