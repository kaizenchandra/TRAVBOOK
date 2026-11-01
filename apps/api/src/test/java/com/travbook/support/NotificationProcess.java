package com.travbook.support;
import com.travbook.TravelApplication;
import com.travbook.notifications.application.port.out.NotificationGateway;
import com.travbook.notifications.domain.model.Delivery;
import org.springframework.boot.builder.SpringApplicationBuilder;
import java.time.Instant;import java.util.UUID;
public class NotificationProcess {
 public static void main(String[] args){try(var c=new SpringApplicationBuilder(TravelApplication.class).run("--server.port=0","--spring.profiles.active=","--travbook.notifications.worker-enabled=false","--travbook.demo.worker-enabled=false","--JOURNEY_WORKER_ENABLED=false","--JOURNEY_CARE_WORKER_ENABLED=false","--STAY_WORKER_ENABLED=false","--RAIL_TICKET_WORKER_ENABLED=false","--travbook.booking.worker-enabled=false","--travbook.booking.events-enabled=false","--travbook.booking.checkout-enabled=false","--travbook.booking.cancellation-enabled=false","--travbook.payments.refund-worker-enabled=false","--travbook.oidc-issuer=","--travbook.login-enabled=false","--logging.level.root=ERROR","--spring.main.banner-mode=off")){c.getBean(NotificationGateway.class).deliver(new Delivery(UUID.fromString(args[0]),UUID.fromString(args[1]),UUID.fromString(args[2]),Delivery.Channel.valueOf(args[3]),Delivery.State.RUNNING,1,2,Instant.now(),null));Runtime.getRuntime().halt(35);}}
}
