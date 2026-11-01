package com.travbook.support;
import com.travbook.TravelApplication;
import com.travbook.booking.application.port.out.*;
import com.travbook.booking.application.usecase.BookingEventDispatcher;
import com.travbook.booking.application.port.in.DispatchBookingEvents;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Duration;
/** Process-loss fixture: the consumer commits; the publisher never receives its acknowledgement. */
public class BookingEventProcess {
    public static void main(String[] args){
        try(var context=new SpringApplicationBuilder(TravelApplication.class).run("--server.port=0","--spring.profiles.active=","--travbook.booking.worker-enabled=false","--travbook.booking.events-enabled=false","--travbook.oidc-issuer=","--travbook.login-enabled=false","--logging.level.root=ERROR","--spring.main.banner-mode=off")){
            var transport=context.getBean(BookingEventDelivery.class);
            var dispatcher=new BookingEventDispatcher(context.getBean(BookingEventOutbox.class),(id,aggregate,version,body)->{
                if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Delivery called within publisher transaction");
                transport.deliver(id,aggregate,version,body);
                if(args[0].equals("crash"))Runtime.getRuntime().halt(24);
            },Duration.ofSeconds(15));
            if(dispatcher.runNext()!=DispatchBookingEvents.Outcome.DELIVERED)throw new IllegalStateException("Expected delivery");
        }
    }
}
