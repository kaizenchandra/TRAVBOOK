package com.travbook.flightprovider.config;
import com.travbook.flightprovider.application.port.in.FlightProvider;
import com.travbook.flightprovider.application.port.out.ProviderStore;
import com.travbook.flightprovider.application.usecase.FlightProviderService;
import com.travbook.flightprovider.adapter.out.persistence.JdbcProviderStore;
import com.travbook.customer.application.port.in.Customers;
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
public class FlightProviderConfiguration {
    @Bean com.travbook.flightprovider.application.port.out.CouponClaims couponClaims(com.travbook.offers.application.port.in.Coupons coupons){return new com.travbook.flightprovider.adapter.out.commerce.CouponAdapter(coupons);}
    @Bean ProviderStore flightProviderStore(JdbcClient jdbc,PlatformTransactionManager manager,ObjectMapper json,@Value("${travbook.integration-mode}")String mode,com.travbook.flightprovider.application.port.out.CouponClaims coupons){
        if(!"MOCK".equals(mode))throw new IllegalStateException("Fictional airline/payment APIs require explicit MOCK mode");
        var tx=new TransactionTemplate(manager);tx.setTimeout(3);return new JdbcProviderStore(jdbc,tx,json,coupons);
    }
    @Bean FlightProvider flightProvider(ProviderStore store,Customers customers,Clock clock){return new FlightProviderService(store,customers,clock);}
}
