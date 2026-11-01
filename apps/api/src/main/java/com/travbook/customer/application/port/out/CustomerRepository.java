package com.travbook.customer.application.port.out;
import com.travbook.shared.domain.CustomerIdentity;
import com.travbook.customer.domain.model.Customer;
import java.time.Instant;
public interface CustomerRepository { Customer findOrCreate(CustomerIdentity identity, Instant now); }

