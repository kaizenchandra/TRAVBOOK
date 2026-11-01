package com.travbook.support;
import com.travbook.TravelApplication;
import com.travbook.railtickets.application.port.out.*;
import com.travbook.railtickets.application.usecase.RailTicketWorker;
import com.travbook.railtickets.domain.model.RailTicket.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Clock;
import java.util.*;
public class RailTicketProcess {
 public static void main(String[] args){try(var context=new SpringApplicationBuilder(TravelApplication.class).run("--server.port=0","--spring.profiles.active=","--STAY_WORKER_ENABLED=false","--RAIL_TICKET_WORKER_ENABLED=false","--travbook.booking.worker-enabled=false","--travbook.booking.events-enabled=false","--travbook.booking.checkout-enabled=false","--travbook.booking.cancellation-enabled=false","--travbook.payments.refund-worker-enabled=false","--travbook.oidc-issuer=","--travbook.login-enabled=false","--logging.level.root=ERROR","--spring.main.banner-mode=off")){var delegate=context.getBean(RailTicketEffects.class);var effects=new RailTicketEffects(){public Optional<Result> lookup(Booking b,Step step){return delegate.lookup(b,step);}public Result execute(Booking b,Step step){if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Provider call inside orchestration transaction");var result=delegate.execute(b,step);if(step.name().equals(args[0])){if(step==Step.REFUND&&!context.getBean(com.travbook.payments.application.port.in.PaymentRefunds.class).settleNext())throw new IllegalStateException("Expected committed hotel refund");Runtime.getRuntime().halt(29);}return result;}};if(!new RailTicketWorker(context.getBean(RailTicketStore.class),context.getBean(RailTicketSelection.class),effects,context.getBean(Clock.class)).tick())throw new IllegalStateException("Expected durable hotel work");}}
}
