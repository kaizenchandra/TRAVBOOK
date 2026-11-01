package com.travbook.search.config;
import com.travbook.catalog.application.port.in.LookupLocations;
import com.travbook.search.application.port.in.DiscoverFlights;
import com.travbook.search.application.port.out.*;
import com.travbook.search.application.usecase.FlightDiscovery;
import com.travbook.search.adapter.out.persistence.JdbcSearchRepository;
import com.travbook.search.adapter.out.supplier.*;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.*;
@Configuration
public class SearchConfiguration {
    @Bean Clock clock() { return Clock.systemUTC(); }
    @Bean(destroyMethod="shutdownNow") ExecutorService supplierExecutor() {
        return new ThreadPoolExecutor(8,8,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(16),new ThreadPoolExecutor.AbortPolicy());
    }
    @Bean SupplierGateway supplierGateway(@org.springframework.beans.factory.annotation.Qualifier("supplierExecutor") ExecutorService supplierExecutor,MeterRegistry metrics,
        @Value("${travbook.integration-mode}") String mode,@Value("${travbook.search-budget-ms}") long budget) {
        if(!"MOCK".equals(mode)) throw new IllegalStateException("Only explicit MOCK integration is implemented. SANDBOX/LIVE require authorized adapters.");
        return new BoundedSupplierGateway(List.of(new MockFlightSupplier("sample-aster","Aster Air",0),
            new MockFlightSupplier("sample-horizon","Horizon Airways",15)),supplierExecutor,budget,metrics);
    }
    @Bean SearchRepository searchRepository(JdbcClient jdbc,PlatformTransactionManager manager,ObjectMapper json) {
        return new JdbcSearchRepository(jdbc,new TransactionTemplate(manager),json);
    }
    @Bean DiscoverFlights discoverFlights(LookupLocations locations,SupplierGateway suppliers,SearchRepository repository,Clock clock) {
        return new FlightDiscovery(locations,suppliers,repository,clock);
    }
}

