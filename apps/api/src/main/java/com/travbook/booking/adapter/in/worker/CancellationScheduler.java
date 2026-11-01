package com.travbook.booking.adapter.in.worker;
import com.travbook.booking.application.port.in.RunCancellation;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import io.micrometer.core.instrument.MeterRegistry;
@Component
@ConditionalOnProperty(name="travbook.booking.cancellation-enabled",havingValue="true",matchIfMissing=true)
public class CancellationScheduler {
    private final RunCancellation worker;private final MeterRegistry metrics;
    public CancellationScheduler(RunCancellation worker,MeterRegistry metrics){this.worker=worker;this.metrics=metrics;}
    @Scheduled(fixedDelay=300) public void tick(){try{if(worker.runNext())metrics.counter("travbook.cancellation.ticks","outcome","processed","mode","MOCK").increment();}catch(RuntimeException unknown){metrics.counter("travbook.cancellation.ticks","outcome","storage_failure","mode","MOCK").increment();}}
}
