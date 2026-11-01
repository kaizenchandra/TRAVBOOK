package com.travbook.booking.config;
import com.travbook.booking.application.port.in.*;
import com.travbook.booking.application.port.out.*;
import com.travbook.booking.application.usecase.*;
import com.travbook.booking.adapter.out.commerce.ApprovedFlightQuoteAdapter;
import com.travbook.booking.adapter.out.supplier.FlightValidationAdapter;
import com.travbook.booking.adapter.out.persistence.JdbcBookingStore;
import com.travbook.booking.adapter.out.persistence.JdbcBookingEventOutbox;
import com.travbook.booking.adapter.out.persistence.JdbcBookingEventInbox;
import com.travbook.booking.adapter.out.messaging.LocalBookingEventDelivery;
import com.travbook.customer.application.port.in.Customers;
import com.travbook.pricing.application.port.in.Quotations;
import com.travbook.search.application.port.in.DiscoverFlights;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
@Configuration
@EnableScheduling
public class BookingConfiguration {
    @Bean BookingEventOutbox bookingEventOutbox(JdbcClient jdbc,PlatformTransactionManager manager){return new JdbcBookingEventOutbox(jdbc,new TransactionTemplate(manager));}
    @Bean BookingEventInbox bookingEventInbox(JdbcClient jdbc,PlatformTransactionManager manager,ObjectMapper json){return new JdbcBookingEventInbox(jdbc,new TransactionTemplate(manager),json);}
    @Bean BookingHistoryService bookingHistory(BookingEventInbox inbox,Customers customers){return new BookingHistoryService(inbox,customers);}
    @Bean BookingEventDelivery bookingEventDelivery(ObjectMapper json,RecordBookingEvent consumer){return new LocalBookingEventDelivery(json,consumer);}
    @Bean DispatchBookingEvents dispatchBookingEvents(BookingEventOutbox outbox,BookingEventDelivery delivery){return new BookingEventDispatcher(outbox,delivery,Duration.ofSeconds(15));}
    @Bean BookingQuotePort bookingQuotePort(Quotations quotes,DiscoverFlights flights){return new ApprovedFlightQuoteAdapter(quotes,flights);}
    @Bean BookingValidationPort bookingValidationPort(DiscoverFlights flights){return new FlightValidationAdapter(flights);}
    @Bean BookingStore bookingStore(JdbcClient jdbc,PlatformTransactionManager manager,ObjectMapper json,Clock clock){return new JdbcBookingStore(jdbc,new TransactionTemplate(manager),json,clock);}
    @Bean BookingIntents bookingIntents(Customers customers,BookingQuotePort quotes,BookingStore store,Clock clock){return new BookingService(customers,quotes,store,clock);}
    @Bean RunBookingValidation runBookingValidation(BookingStore store,BookingValidationPort validation,Clock clock,
        @Value("${travbook.booking.lease-seconds:15}") int lease,@Value("${travbook.booking.max-attempts:4}") int max){return new BookingWorker(store,validation,clock,Duration.ofSeconds(lease),max);}
}

