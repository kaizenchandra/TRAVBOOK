package com.travbook.support;
import com.travbook.TravelApplication;
import com.travbook.booking.application.port.out.*;
import com.travbook.booking.application.usecase.CheckoutWorker;
import com.travbook.booking.domain.model.Checkout;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Clock;
import java.util.*;
public class CheckoutProcess {
    public static void main(String[] args){
        try(var context=new SpringApplicationBuilder(TravelApplication.class).run("--server.port=0","--spring.profiles.active=","--travbook.booking.worker-enabled=false","--travbook.booking.events-enabled=false","--travbook.booking.checkout-enabled=false","--travbook.oidc-issuer=","--travbook.login-enabled=false","--logging.level.root=ERROR","--spring.main.banner-mode=off")){
            var provider=context.getBean(CheckoutSupplier.class);
            var wrapped=new CheckoutSupplier(){public Optional<Checkout.Effect> lookup(UUID id){return provider.lookup(id);}public Checkout.Effect execute(Checkout c){
                if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Provider invoked inside orchestration transaction");
                var result=provider.execute(c);if(args[0].equals("crash"))Runtime.getRuntime().halt(25);return result;
            }};
            var worker=new CheckoutWorker(context.getBean(CheckoutStore.class),context.getBean(BookingValidationPort.class),context.getBean(CheckoutPayments.class),wrapped,context.getBean(Clock.class));
            if(!worker.runNext())throw new IllegalStateException("Expected durable checkout work");
        }
    }
}
