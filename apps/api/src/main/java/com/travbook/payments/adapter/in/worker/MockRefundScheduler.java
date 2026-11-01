package com.travbook.payments.adapter.in.worker;
import com.travbook.payments.application.port.in.PaymentRefunds;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import io.micrometer.core.instrument.MeterRegistry;
@Component
@ConditionalOnProperty(name="travbook.payments.refund-worker-enabled",havingValue="true",matchIfMissing=true)
public class MockRefundScheduler {
    private final PaymentRefunds refunds;private final MeterRegistry metrics;
    public MockRefundScheduler(PaymentRefunds refunds,MeterRegistry metrics){this.refunds=refunds;this.metrics=metrics;}
    @Scheduled(fixedDelay=300) public void tick(){try{if(refunds.settleNext())metrics.counter("travbook.refund.settlement","outcome","settled","mode","MOCK").increment();}catch(RuntimeException unknown){metrics.counter("travbook.refund.settlement","outcome","storage_failure","mode","MOCK").increment();}}
}
