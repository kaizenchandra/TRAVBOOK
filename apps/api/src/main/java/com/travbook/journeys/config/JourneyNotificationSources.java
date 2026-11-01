package com.travbook.journeys.config;
import com.travbook.shared.application.port.LifecycleSource;
import com.travbook.shared.adapter.out.persistence.JdbcLifecycleSource;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.ObjectMapper;
@Configuration public class JourneyNotificationSources {
 @Bean LifecycleSource journeyNotices(JdbcClient jdbc,ObjectMapper json){return new JdbcLifecycleSource("journey","select e.id event_id,e.aggregate_id,b.customer_id owner_id,b.id booking_id,e.envelope from journey_outbox e join journey_booking b on b.id=e.aggregate_id","/journeys/bookings/",jdbc,json);}
 @Bean LifecycleSource journeyCareNotices(JdbcClient jdbc,ObjectMapper json){return new JdbcLifecycleSource("journey-aftercare","select e.id event_id,e.aggregate_id,b.customer_id owner_id,b.booking_id,e.envelope from journey_care_outbox e join journey_care_action b on b.id=e.aggregate_id","/journeys/bookings/",jdbc,json);}
}
