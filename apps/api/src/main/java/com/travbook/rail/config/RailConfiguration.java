package com.travbook.rail.config;
import com.travbook.rail.application.port.in.DiscoverTrains;
import com.travbook.rail.application.port.out.*;
import com.travbook.rail.application.usecase.TrainDiscovery;
import com.travbook.rail.adapter.out.supplier.*;
import com.travbook.rail.adapter.out.persistence.JdbcRailSearches;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.*;
@Configuration
public class RailConfiguration {
    @Bean(destroyMethod="shutdownNow") ExecutorService railExecutor(){return new ThreadPoolExecutor(2,2,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(8),new ThreadPoolExecutor.AbortPolicy());}
    @Bean RailGateway railGateway(@Qualifier("railExecutor") ExecutorService pool,MeterRegistry metrics,@Value("${travbook.integration-mode}") String mode){if(!"MOCK".equals(mode))throw new IllegalStateException("Railway integration requires explicit MOCK until authorized providers are implemented.");return new BoundedRailGateway(List.of(new MockRailSupplier("sample-meridian",0),new MockRailSupplier("sample-starlight",1)),pool,1500,metrics);}
    @Bean RailRepository railRepository(JdbcClient jdbc,PlatformTransactionManager manager,ObjectMapper json){return new JdbcRailSearches(jdbc,new TransactionTemplate(manager),json);}
    @Bean DiscoverTrains discoverTrains(RailRepository repository,RailGateway gateway,Clock clock){return new TrainDiscovery(repository,gateway,clock);}
}
