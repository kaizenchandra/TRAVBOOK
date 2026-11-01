package com.travbook.customer.config;
import com.travbook.customer.application.port.in.Customers;
import com.travbook.customer.application.port.out.CustomerRepository;
import com.travbook.customer.application.usecase.CustomerService;
import com.travbook.customer.adapter.out.persistence.JdbcCustomers;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import java.time.Clock;
import com.travbook.customer.application.port.in.CustomerProfiles;
import com.travbook.customer.application.port.out.ProfileStore;
import com.travbook.customer.application.usecase.ProfileService;
import com.travbook.customer.adapter.out.persistence.JdbcProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
@Configuration
public class CustomerConfiguration {
    @Bean com.travbook.customer.application.port.out.OrganizerStore organizerStore(JdbcClient jdbc,PlatformTransactionManager manager,tools.jackson.databind.ObjectMapper json){var tx=new TransactionTemplate(manager);tx.setTimeout(3);return new com.travbook.customer.adapter.out.persistence.JdbcOrganizer(jdbc,tx,json);}
    @Bean com.travbook.customer.application.port.in.CustomerOrganizer customerOrganizer(Customers customers,com.travbook.customer.application.port.out.OrganizerStore store,Clock clock){return new com.travbook.customer.application.usecase.OrganizerService(customers,store,clock);}
    @Bean CustomerRepository customerRepository(JdbcClient jdbc) { return new JdbcCustomers(jdbc); }
    @Bean Customers customers(CustomerRepository repository,Clock clock) { return new CustomerService(repository,clock); }
    @Bean ProfileStore profileStore(JdbcClient jdbc,PlatformTransactionManager manager) {
        var tx=new TransactionTemplate(manager);tx.setTimeout(3);
        return new JdbcProfiles(jdbc,tx);
    }
    @Bean CustomerProfiles customerProfiles(Customers customers,ProfileStore store,Clock clock) { return new ProfileService(customers,store,clock); }
}

