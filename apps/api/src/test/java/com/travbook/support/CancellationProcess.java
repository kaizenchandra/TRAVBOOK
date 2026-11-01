package com.travbook.support;
import com.travbook.TravelApplication;
import com.travbook.booking.application.port.out.*;
import com.travbook.booking.application.usecase.CancellationWorker;
import com.travbook.booking.domain.model.*;
import com.travbook.payments.application.port.in.PaymentRefunds;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Clock;
import java.util.*;
public class CancellationProcess {
    public static void main(String[] args){
        try(var context=new SpringApplicationBuilder(TravelApplication.class).run("--server.port=0","--spring.profiles.active=","--travbook.booking.worker-enabled=false","--travbook.booking.events-enabled=false","--travbook.booking.checkout-enabled=false","--travbook.booking.cancellation-enabled=false","--travbook.payments.refund-worker-enabled=false","--travbook.oidc-issuer=","--travbook.login-enabled=false","--logging.level.root=ERROR","--spring.main.banner-mode=off")){
            var supplier=context.getBean(CancellationSupplier.class);var refunds=context.getBean(CancellationRefunds.class);
            var wrappedSupplier=new CancellationSupplier(){public void verifyCancellable(Checkout c){supplier.verifyCancellable(c);}public Optional<Cancellation.SupplierReceipt> lookup(UUID id){return supplier.lookup(id);}public Cancellation.SupplierReceipt cancel(Cancellation c){if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Provider in orchestration transaction");var r=supplier.cancel(c);if(args[0].equals("crash-cancel"))Runtime.getRuntime().halt(27);return r;}};
            var wrappedRefund=new CancellationRefunds(){public Optional<Cancellation.RefundReceipt> lookup(UUID id){return refunds.lookup(id);}public Cancellation.RefundReceipt submit(Cancellation c){if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Refund in orchestration transaction");var r=refunds.submit(c);if(args[0].equals("crash-refund")){if(!context.getBean(PaymentRefunds.class).settleNext())throw new IllegalStateException("Expected committed refund");Runtime.getRuntime().halt(28);}return r;}};
            if(!new CancellationWorker(context.getBean(CancellationStore.class),wrappedSupplier,wrappedRefund,context.getBean(Clock.class)).runNext())throw new IllegalStateException("Expected durable work");
        }
    }
}
