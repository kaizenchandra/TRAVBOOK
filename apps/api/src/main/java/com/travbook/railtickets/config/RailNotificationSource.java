package com.travbook.railtickets.config;
import com.travbook.shared.application.port.LifecycleSource;
import com.travbook.shared.adapter.out.persistence.JdbcLifecycleSource;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.ObjectMapper;
@Configuration public class RailNotificationSource {
 @Bean LifecycleSource railNotices(JdbcClient jdbc,ObjectMapper json){return new JdbcLifecycleSource("train","select e.id event_id,e.aggregate_id,b.customer_id owner_id,b.id booking_id,e.envelope from rail_ticket_outbox e join rail_ticket_booking b on b.id=e.aggregate_id","/rail-tickets/bookings/",jdbc,json);}
}
