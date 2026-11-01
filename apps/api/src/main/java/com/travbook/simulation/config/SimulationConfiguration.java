package com.travbook.simulation.config;
import com.travbook.simulation.application.port.in.*;
import com.travbook.simulation.application.port.out.*;
import com.travbook.simulation.application.usecase.*;
import com.travbook.simulation.adapter.out.persistence.*;
import com.travbook.customer.application.port.in.*;
import com.travbook.pricing.application.port.in.Quotations;
import com.travbook.journeys.application.port.in.AdvancedJourneys;
import com.travbook.payments.application.port.in.*;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.time.Clock;
@Configuration
@ConditionalOnProperty(name="travbook.demo.enabled",havingValue="true")
public class SimulationConfiguration {
    private TransactionTemplate tx(PlatformTransactionManager manager){var t=new TransactionTemplate(manager);t.setTimeout(3);return t;}
    @Bean DemoStore demoStore(JdbcClient jdbc,PlatformTransactionManager manager,ObjectMapper json,@Value("${travbook.integration-mode}")String mode){
        if(!"MOCK".equals(mode))throw new IllegalStateException("Dummy APIs require explicit MOCK mode");return new JdbcDemoStore(jdbc,tx(manager),json);
    }
    @Bean DemoDelivery demoDelivery(JdbcClient jdbc,PlatformTransactionManager manager,Clock clock){return new LocalDemoDelivery(jdbc,tx(manager),clock);}
    @Bean ProcessDemoOperations demoWorker(DemoStore store,DemoDelivery delivery,Clock clock){return new DemoWorker(store,delivery,clock);}
    @Bean DemoPositions demoPositions(){return new com.travbook.simulation.adapter.out.supplier.FixedDemoPositions();}
    @Bean DemoServices demoServices(Customers customers,CustomerProfiles profiles,Quotations quotes,AdvancedJourneys journeys,PaymentOperations payments,PaymentReports reports,DemoStore store,DemoPositions positions,Clock clock){return new DemoService(customers,profiles,quotes,journeys,payments,reports,store,positions,clock);}
}
