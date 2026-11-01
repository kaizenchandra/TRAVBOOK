package com.travbook.booking.config;
import com.travbook.booking.application.port.in.*;
import com.travbook.booking.application.port.out.*;
import com.travbook.booking.application.usecase.*;
import com.travbook.booking.adapter.out.persistence.*;
import com.travbook.booking.adapter.out.commerce.CheckoutPaymentAdapter;
import com.travbook.payments.application.port.in.PaymentOperations;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.time.Clock;
@Configuration
public class CheckoutConfiguration {
    private TransactionTemplate transaction(PlatformTransactionManager manager){var tx=new TransactionTemplate(manager);tx.setTimeout(3);return tx;}
    @Bean CheckoutStore checkoutStore(JdbcClient jdbc,PlatformTransactionManager manager,ObjectMapper json,Clock clock){return new JdbcCheckoutStore(jdbc,transaction(manager),json,clock);}
    @Bean CheckoutSupplier checkoutSupplier(JdbcClient jdbc,PlatformTransactionManager manager,ObjectMapper json,Clock clock){return new DurableMockSupplier(jdbc,transaction(manager),json,clock);}
    @Bean CheckoutPayments checkoutPayments(PaymentOperations payments){return new CheckoutPaymentAdapter(payments);}
    @Bean BookingCheckouts bookingCheckouts(BookingIntents bookings,CheckoutStore store,Clock clock){return new CheckoutService(bookings,store,clock);}
    @Bean RunCheckout runCheckout(CheckoutStore store,BookingValidationPort validation,CheckoutPayments payments,CheckoutSupplier supplier,Clock clock){return new CheckoutWorker(store,validation,payments,supplier,clock);}
}
