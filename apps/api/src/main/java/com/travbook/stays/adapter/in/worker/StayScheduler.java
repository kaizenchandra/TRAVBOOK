package com.travbook.stays.adapter.in.worker;
import com.travbook.stays.application.port.in.ProcessStays;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
import io.micrometer.core.instrument.MeterRegistry;
@Component
public class StayScheduler {
 private final ProcessStays worker;private final boolean enabled;private final MeterRegistry metrics;
 public StayScheduler(ProcessStays worker,@Value("${STAY_WORKER_ENABLED:true}") boolean enabled,MeterRegistry metrics){this.worker=worker;this.enabled=enabled;this.metrics=metrics;}
 @Scheduled(fixedDelayString="${STAY_POLL_MS:300}") public void run(){if(enabled&&worker.tick())metrics.counter("travbook.stays.worker","mode","MOCK").increment();}
}
