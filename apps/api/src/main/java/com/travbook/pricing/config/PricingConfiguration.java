package com.travbook.pricing.config;
import com.travbook.customer.application.port.in.Customers;
import com.travbook.pricing.application.port.in.Quotations;
import com.travbook.pricing.application.port.out.*;
import com.travbook.pricing.application.usecase.QuotationService;
import com.travbook.pricing.adapter.out.persistence.JdbcQuotations;
import com.travbook.pricing.adapter.out.search.FlightRevalidationAdapter;
import com.travbook.search.application.port.in.DiscoverFlights;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.time.Clock;
@Configuration
public class PricingConfiguration {
    @Bean RevalidateSelection revalidateSelection(DiscoverFlights flights) { return new FlightRevalidationAdapter(flights); }
    @Bean QuotationRepository quotationRepository(JdbcClient jdbc,PlatformTransactionManager manager,ObjectMapper json,Clock clock) {
        return new JdbcQuotations(jdbc,new TransactionTemplate(manager),json,clock);
    }
    @Bean Quotations quotations(Customers customers,RevalidateSelection pricing,QuotationRepository repository,Clock clock) {
        return new QuotationService(customers,pricing,repository,clock);
    }
}

