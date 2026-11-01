package com.travbook.stays.config;
import com.travbook.shared.application.port.LifecycleSource;
import com.travbook.shared.adapter.out.persistence.JdbcLifecycleSource;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.ObjectMapper;
@Configuration public class StayNotificationSource {
 @Bean LifecycleSource stayNotices(JdbcClient jdbc,ObjectMapper json){return new JdbcLifecycleSource("hotel","select e.id event_id,e.aggregate_id,b.customer_id owner_id,b.id booking_id,e.envelope from stay_outbox e join stay_booking b on b.id=e.aggregate_id","/stays/bookings/",jdbc,json);}
}
