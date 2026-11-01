package com.travbook.support;
import com.travbook.TravelApplication;
import com.travbook.flightprovider.application.port.in.FlightProvider;
import com.travbook.flightprovider.domain.model.ProviderReservation;
import com.travbook.shared.domain.CustomerIdentity;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.*;
import java.time.*;
import java.util.*;
/** Isolated integration-test process; dies after commit before acknowledging the command. */
public class FlightProviderProcess {
    static Instant now;
    @org.springframework.boot.test.context.TestConfiguration static class TimeConfig {@Bean @Primary Clock crashClock(){return Clock.fixed(now,ZoneOffset.UTC);}}
    public static void main(String[] args){now=Instant.parse(args[4]);try(var c=new SpringApplicationBuilder(TravelApplication.class,TimeConfig.class).run("--server.port=0","--spring.profiles.active=","--travbook.demo.enabled=true","--travbook.demo.worker-enabled=false","--JOURNEY_WORKER_ENABLED=false","--JOURNEY_CARE_WORKER_ENABLED=false","--STAY_WORKER_ENABLED=false","--RAIL_TICKET_WORKER_ENABLED=false","--travbook.booking.worker-enabled=false","--travbook.booking.events-enabled=false","--travbook.booking.checkout-enabled=false","--travbook.booking.cancellation-enabled=false","--travbook.payments.refund-worker-enabled=false","--travbook.oidc-issuer=","--travbook.login-enabled=false","--logging.level.root=ERROR","--spring.main.banner-mode=off")){
        c.getBean(FlightProvider.class).act(new CustomerIdentity(args[0],args[1]),UUID.fromString(args[2]),ProviderReservation.Action.PAY_SUCCESS,List.of(),true,args[3],UUID.randomUUID());Runtime.getRuntime().halt(34);
    }}
}
