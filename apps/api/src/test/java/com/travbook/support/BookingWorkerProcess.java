package com.travbook.support;
import com.travbook.TravelApplication;
import com.travbook.booking.application.port.out.*;
import com.travbook.booking.application.usecase.BookingWorker;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.*;
/** A real child JVM; halt intentionally bypasses shutdown hooks after a completed read. */
public class BookingWorkerProcess {
    public static void main(String[] args) {
        try(var context=new SpringApplicationBuilder(TravelApplication.class).run("--server.port=0","--spring.profiles.active=","--travbook.booking.worker-enabled=false","--travbook.booking.events-enabled=false","--travbook.oidc-issuer=","--travbook.login-enabled=false","--logging.level.root=ERROR","--spring.main.banner-mode=off")) {
            var store=context.getBean(BookingStore.class);var provider=context.getBean(BookingValidationPort.class);var jdbc=context.getBean(JdbcClient.class);
            var worker=new BookingWorker(store,quote->{
                if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Remote read inside transaction");
                var result=provider.current(quote);
                jdbc.sql("insert into test_validation_reads(quote_id,reads) values(:id,1) on conflict(quote_id) do update set reads=test_validation_reads.reads+1").param("id",quote.quoteId()).update();
                if(args[0].equals("crash"))Runtime.getRuntime().halt(23);
                return result;
            },context.getBean(Clock.class),Duration.ofSeconds(15),4);
            if(!worker.runNext())throw new IllegalStateException("Expected persisted work");
        }
    }
}
