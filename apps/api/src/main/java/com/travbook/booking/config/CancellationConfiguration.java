package com.travbook.booking.config;
import com.travbook.booking.application.port.in.*;
import com.travbook.booking.application.port.out.*;
import com.travbook.booking.application.usecase.*;
import com.travbook.booking.adapter.out.persistence.*;
import com.travbook.booking.adapter.out.commerce.RefundPaymentAdapter;
import com.travbook.payments.application.port.in.PaymentRefunds;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.time.Clock;
@Configuration
public class CancellationConfiguration {
    private TransactionTemplate transaction(PlatformTransactionManager manager){var tx=new TransactionTemplate(manager);tx.setTimeout(3);return tx;}
    @Bean CancellationStore cancellationStore(JdbcClient jdbc,PlatformTransactionManager manager,ObjectMapper json,Clock clock){return new JdbcCancellationStore(jdbc,transaction(manager),json,clock);}
    @Bean CancellationSupplier cancellationSupplier(JdbcClient jdbc,PlatformTransactionManager manager,ObjectMapper json,Clock clock){return new DurableCancellationSupplier(jdbc,transaction(manager),json,clock);}
    @Bean CancellationRefunds cancellationRefunds(PaymentRefunds refunds){return new RefundPaymentAdapter(refunds);}
    @Bean BookingCancellations bookingCancellations(BookingIntents bookings,BookingCheckouts checkouts,CancellationStore store,CancellationSupplier supplier,Clock clock){return new CancellationService(bookings,checkouts,store,supplier,clock);}
    @Bean RunCancellation runCancellation(CancellationStore store,CancellationSupplier supplier,CancellationRefunds refunds,Clock clock){return new CancellationWorker(store,supplier,refunds,clock);}
}
