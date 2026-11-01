package com.travbook.support;
import com.travbook.TravelApplication;
import com.travbook.journeys.application.port.out.*;
import com.travbook.journeys.domain.model.Journey.Step;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.UUID;
/** Failure-injection subprocess: crash after remote commit, before local acknowledgement. */
public class JourneyProcess {
 public static void main(String[] args){try(var c=new SpringApplicationBuilder(TravelApplication.class).run("--server.port=0","--spring.profiles.active=","--JOURNEY_WORKER_ENABLED=false","--STAY_WORKER_ENABLED=false","--RAIL_TICKET_WORKER_ENABLED=false","--travbook.booking.worker-enabled=false","--travbook.booking.events-enabled=false","--travbook.booking.checkout-enabled=false","--travbook.booking.cancellation-enabled=false","--travbook.payments.refund-worker-enabled=false","--travbook.oidc-issuer=","--travbook.login-enabled=false","--logging.level.root=ERROR","--spring.main.banner-mode=off")){var b=c.getBean(JourneyStore.class).booking(UUID.fromString(args[0]),UUID.fromString(args[1]));if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Effect inside transaction");c.getBean(JourneyEffects.class).execute(b,Step.valueOf(args[2]));Runtime.getRuntime().halt(29);}}
}
