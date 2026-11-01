package com.travbook.payments.config;
import com.travbook.payments.application.port.in.PaymentOperations;
import com.travbook.payments.application.port.out.PaymentStore;
import com.travbook.payments.application.usecase.MockPaymentService;
import com.travbook.payments.adapter.out.persistence.JdbcMockPayments;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.time.Clock;
@Configuration
public class PaymentsConfiguration {
    @Bean PaymentStore paymentStore(JdbcClient jdbc,PlatformTransactionManager manager,ObjectMapper json,Clock clock,@Value("${travbook.integration-mode}") String mode){
        if(!"MOCK".equals(mode))throw new IllegalStateException("Only explicitly configured MOCK payments are implemented");var tx=new TransactionTemplate(manager);tx.setTimeout(3);return new JdbcMockPayments(jdbc,tx,json,clock);
    }
    @Bean PaymentOperations paymentOperations(PaymentStore store){return new MockPaymentService(store);}
    @Bean com.travbook.payments.application.port.out.FinancialReportStore financialReportStore(JdbcClient jdbc,PlatformTransactionManager manager){var tx=new TransactionTemplate(manager);tx.setReadOnly(true);tx.setTimeout(3);return new com.travbook.payments.adapter.out.persistence.JdbcFinancialReports(jdbc,tx);}
    @Bean com.travbook.payments.application.port.in.PaymentReports paymentReports(com.travbook.payments.application.port.out.FinancialReportStore store,Clock clock){return new com.travbook.payments.application.usecase.PaymentReportService(store,clock);}
}
