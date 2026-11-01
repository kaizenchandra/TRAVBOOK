package com.travbook.trips.config;
import com.travbook.trips.application.port.in.TripDocuments;
import com.travbook.trips.application.port.out.*;
import com.travbook.trips.application.usecase.DocumentService;
import com.travbook.trips.adapter.out.booking.BookingJourneyAdapter;
import com.travbook.trips.adapter.out.format.PlainMockSummary;
import com.travbook.trips.adapter.out.persistence.JdbcDocuments;
import com.travbook.booking.application.port.in.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.time.Clock;
@Configuration
public class TripsConfiguration {
    @Bean ConfirmedJourneyPort confirmedJourneyPort(BookingIntents bookings,BookingCheckouts checkouts,BookingCancellations cancellations){return new BookingJourneyAdapter(bookings,checkouts,cancellations);}
    @Bean DocumentRenderer documentRenderer(){return new PlainMockSummary();}
    @Bean DocumentStore documentStore(JdbcClient jdbc,PlatformTransactionManager manager,ObjectMapper json){var tx=new TransactionTemplate(manager);tx.setTimeout(3);return new JdbcDocuments(jdbc,tx,json);}
    @Bean TripDocuments tripDocuments(ConfirmedJourneyPort journeys,DocumentRenderer renderer,DocumentStore store,Clock clock){return new DocumentService(journeys,renderer,store,clock);}
}
