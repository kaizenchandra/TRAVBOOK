package com.travbook.hotels.config;
import com.travbook.hotels.application.port.in.DiscoverHotels;
import com.travbook.hotels.application.port.out.*;
import com.travbook.hotels.application.usecase.HotelDiscovery;
import com.travbook.hotels.adapter.out.supplier.*;
import com.travbook.hotels.adapter.out.persistence.JdbcHotelSearches;
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
public class HotelConfiguration {
    @Bean(destroyMethod="shutdownNow") ExecutorService hotelExecutor(){return new ThreadPoolExecutor(4,4,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(8),new ThreadPoolExecutor.AbortPolicy());}
    @Bean HotelRoomStock hotelRoomStock(JdbcClient jdbc){return new com.travbook.hotels.adapter.out.persistence.JdbcHotelRoomStock(jdbc);}
    @Bean com.travbook.hotels.application.port.in.HotelReservations hotelReservations(JdbcClient jdbc,PlatformTransactionManager manager,ObjectMapper json,HotelSupplierGateway gateway,HotelRoomStock stock,Clock clock){var tx=new TransactionTemplate(manager);tx.setTimeout(3);return new com.travbook.hotels.adapter.out.persistence.DurableMockHotelReservations(jdbc,tx,json,gateway,stock,clock);}
    @Bean HotelSupplierGateway hotelSuppliers(@Qualifier("hotelExecutor") ExecutorService pool,MeterRegistry metrics,HotelRoomStock stock,@Value("${travbook.integration-mode}") String mode){
        if(!"MOCK".equals(mode))throw new IllegalStateException("Hotels require explicit MOCK mode until authorized adapters are configured.");
        return new BoundedHotelGateway(List.of(new MockHotelSupplier("sample-lotus",0,stock),new MockHotelSupplier("sample-cove",1,stock)),pool,1500,metrics);
    }
    @Bean HotelSearchRepository hotelSearches(JdbcClient jdbc,PlatformTransactionManager manager,ObjectMapper json){return new JdbcHotelSearches(jdbc,new TransactionTemplate(manager),json);}
    @Bean DiscoverHotels discoverHotels(HotelSearchRepository repository,HotelSupplierGateway suppliers,Clock clock){return new HotelDiscovery(repository,suppliers,clock);}
}
