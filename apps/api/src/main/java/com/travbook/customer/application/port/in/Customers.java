package com.travbook.customer.application.port.in;
import com.travbook.shared.domain.CustomerIdentity;
import com.travbook.customer.domain.model.Customer;
public interface Customers { Customer resolve(CustomerIdentity identity); }

