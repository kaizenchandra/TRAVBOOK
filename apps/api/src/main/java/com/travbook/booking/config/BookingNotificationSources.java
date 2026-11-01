package com.travbook.booking.config;
import com.travbook.shared.application.port.LifecycleSource;
import com.travbook.shared.adapter.out.persistence.JdbcLifecycleSource;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.ObjectMapper;
@Configuration
public class BookingNotificationSources {
    @Bean LifecycleSource bookingNotices(JdbcClient jdbc,ObjectMapper json){return new JdbcLifecycleSource("flight-intent","select e.id event_id,e.aggregate_id,b.customer_id owner_id,b.id booking_id,e.envelope from booking_outbox e join booking_intent b on b.id=e.aggregate_id","/bookings/",jdbc,json);}
    @Bean LifecycleSource checkoutNotices(JdbcClient jdbc,ObjectMapper json){return new JdbcLifecycleSource("flight-checkout","select e.id event_id,e.aggregate_id,b.customer_id owner_id,b.booking_id,e.envelope from booking_checkout_outbox e join booking_checkout b on b.id=e.aggregate_id","/bookings/",jdbc,json);}
    @Bean LifecycleSource cancellationNotices(JdbcClient jdbc,ObjectMapper json){return new JdbcLifecycleSource("flight-aftercare","select e.id event_id,e.aggregate_id,b.customer_id owner_id,b.booking_id,e.envelope from booking_aftercare_outbox e join booking_cancellation b on b.id=e.aggregate_id","/bookings/",jdbc,json);}
}
