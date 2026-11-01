package com.travbook.support;
import com.travbook.TravelApplication;
import com.travbook.simulation.application.port.out.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import java.util.UUID;
public class DemoDeliveryProcess {
    public static void main(String[] args){try(var c=new SpringApplicationBuilder(TravelApplication.class).run("--server.port=0","--spring.profiles.active=","--travbook.demo.enabled=true","--travbook.demo.worker-enabled=false","--JOURNEY_WORKER_ENABLED=false","--JOURNEY_CARE_WORKER_ENABLED=false","--STAY_WORKER_ENABLED=false","--RAIL_TICKET_WORKER_ENABLED=false","--travbook.booking.worker-enabled=false","--travbook.booking.events-enabled=false","--travbook.booking.checkout-enabled=false","--travbook.booking.cancellation-enabled=false","--travbook.payments.refund-worker-enabled=false","--travbook.oidc-issuer=","--travbook.login-enabled=false","--logging.level.root=ERROR","--spring.main.banner-mode=off")){
        var op=c.getBean(DemoStore.class).get(UUID.fromString(args[0]),UUID.fromString(args[1]));c.getBean(DemoDelivery.class).deliver(op);Runtime.getRuntime().halt(33);
    }}
}
