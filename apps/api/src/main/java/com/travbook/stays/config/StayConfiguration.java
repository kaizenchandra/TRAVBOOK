package com.travbook.stays.config;
import com.travbook.stays.application.port.in.HotelStays;
import com.travbook.stays.application.port.out.*;
import com.travbook.stays.application.usecase.*;
import com.travbook.stays.adapter.out.commerce.*;
import com.travbook.stays.adapter.out.persistence.JdbcStayStore;
import com.travbook.customer.application.port.in.Customers;
import com.travbook.hotels.application.port.in.*;
import com.travbook.payments.application.port.in.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.time.Clock;
@Configuration
public class StayConfiguration {
 @Bean StayStore stayStore(JdbcClient jdbc,PlatformTransactionManager manager,ObjectMapper json,Clock clock){var tx=new TransactionTemplate(manager);tx.setTimeout(3);return new JdbcStayStore(jdbc,tx,json,clock);}
 @Bean StaySelection staySelection(DiscoverHotels hotels){return new HotelSelectionAdapter(hotels);}
 @Bean StayEffects stayEffects(PaymentOperations payments,PaymentRefunds refunds,HotelReservations supplier){return new StayEffectAdapter(payments,refunds,supplier);}
 @Bean HotelStays hotelStays(Customers customers,StaySelection selection,StayStore store,Clock clock){return new StayService(customers,selection,store,clock);}
 @Bean StayWorker stayWorker(StayStore store,StaySelection selection,StayEffects effects,Clock clock){return new StayWorker(store,selection,effects,clock);}
 @Bean com.travbook.shared.application.port.in.ReviewRecovery stayRecovery(StayStore store,StayEffects effects){return new StayRecovery(store,effects);}
}
