package com.travbook.payments.config;
import com.travbook.payments.application.port.in.PaymentRefunds;
import com.travbook.payments.application.port.out.RefundStore;
import com.travbook.payments.application.usecase.MockRefundService;
import com.travbook.payments.adapter.out.persistence.JdbcMockRefunds;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.time.Clock;
@Configuration
public class RefundConfiguration {
    @Bean RefundStore refundStore(JdbcClient jdbc,PlatformTransactionManager manager,ObjectMapper json,Clock clock,@Value("${travbook.integration-mode}") String mode){if(!"MOCK".equals(mode))throw new IllegalStateException("Only MOCK refunds are implemented");var tx=new TransactionTemplate(manager);tx.setTimeout(3);return new JdbcMockRefunds(jdbc,tx,json,clock);}
    @Bean PaymentRefunds paymentRefunds(RefundStore store){return new MockRefundService(store);}
}
