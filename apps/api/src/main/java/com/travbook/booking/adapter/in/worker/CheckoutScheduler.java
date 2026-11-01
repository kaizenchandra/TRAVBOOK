package com.travbook.booking.adapter.in.worker;
import com.travbook.booking.application.port.in.RunCheckout;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import io.micrometer.core.instrument.MeterRegistry;
@Component
@ConditionalOnProperty(name="travbook.booking.checkout-enabled",havingValue="true",matchIfMissing=true)
public class CheckoutScheduler {
    private final RunCheckout worker;private final MeterRegistry metrics;
    public CheckoutScheduler(RunCheckout worker,MeterRegistry metrics){this.worker=worker;this.metrics=metrics;}
    @Scheduled(fixedDelayString="${travbook.booking.checkout-poll-ms:300}")
    public void tick(){
        try{if(worker.runNext())metrics.counter("travbook.booking.checkout.ticks","outcome","processed","mode","MOCK").increment();}
        catch(RuntimeException unavailable){metrics.counter("travbook.booking.checkout.ticks","outcome","storage_failure","mode","MOCK").increment();}
    }
}
