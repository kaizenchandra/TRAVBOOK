package com.travbook.support;
import com.travbook.TravelApplication;
import com.travbook.journeys.application.port.out.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import java.util.UUID;
public class JourneyCareProcess {
 public static void main(String[] args){try(var c=new SpringApplicationBuilder(TravelApplication.class).run("--server.port=0","--spring.profiles.active=","--JOURNEY_WORKER_ENABLED=false","--JOURNEY_CARE_WORKER_ENABLED=false","--STAY_WORKER_ENABLED=false","--RAIL_TICKET_WORKER_ENABLED=false","--travbook.booking.worker-enabled=false","--travbook.booking.events-enabled=false","--travbook.booking.checkout-enabled=false","--travbook.booking.cancellation-enabled=false","--travbook.payments.refund-worker-enabled=false","--travbook.oidc-issuer=","--travbook.login-enabled=false","--logging.level.root=ERROR","--spring.main.banner-mode=off")){var a=c.getBean(JourneyCareStore.class).summary(UUID.fromString(args[0]),UUID.fromString(args[1])).actions().stream().filter(v->v.id().equals(UUID.fromString(args[2]))).findFirst().orElseThrow();c.getBean(JourneyCareEffects.class).execute(a);Runtime.getRuntime().halt(31);}}
}
