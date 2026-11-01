package com.travbook.flightprovider.config;
import com.travbook.shared.application.port.LifecycleSource;
import com.travbook.shared.adapter.out.persistence.JdbcLifecycleSource;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.ObjectMapper;
@Configuration public class FlightNotificationSource {
 @Bean LifecycleSource flightProviderNotices(JdbcClient jdbc,ObjectMapper json){return new JdbcLifecycleSource("flight-extras","select e.id event_id,e.aggregate_id,b.owner_id,b.id booking_id,e.envelope from fp_event e join fp_reservation b on b.id=e.aggregate_id","/flight-services?booking=",jdbc,json);}
}
