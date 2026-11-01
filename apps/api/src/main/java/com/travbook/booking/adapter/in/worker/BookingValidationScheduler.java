package com.travbook.booking.adapter.in.worker;
import com.travbook.booking.application.port.in.RunBookingValidation;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import io.micrometer.core.instrument.MeterRegistry;
@Component
@ConditionalOnProperty(name="travbook.booking.worker-enabled",havingValue="true",matchIfMissing=true)
public class BookingValidationScheduler {
    private final RunBookingValidation worker;private final MeterRegistry metrics;
    public BookingValidationScheduler(RunBookingValidation worker,MeterRegistry metrics){this.worker=worker;this.metrics=metrics;}
    @Scheduled(fixedDelayString="${travbook.booking.poll-ms:500}")
    public void tick() {
        try{if(worker.runNext())metrics.counter("travbook.booking.validation.ticks","outcome","processed","mode","MOCK").increment();}
        catch(RuntimeException failure){metrics.counter("travbook.booking.validation.ticks","outcome","storage_failure","mode","MOCK").increment();}
    }
}

