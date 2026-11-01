package com.travbook.journeys.adapter.in.worker;
import com.travbook.journeys.application.port.in.ProcessJourneyCare;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
import io.micrometer.core.instrument.MeterRegistry;
@Component public class JourneyCareScheduler {
 private final ProcessJourneyCare worker;private final boolean enabled;private final MeterRegistry metrics;
 public JourneyCareScheduler(ProcessJourneyCare w,@Value("${JOURNEY_CARE_WORKER_ENABLED:true}") boolean enabled,MeterRegistry metrics){worker=w;this.enabled=enabled;this.metrics=metrics;}
 @Scheduled(fixedDelayString="${JOURNEY_CARE_POLL_MS:300}") public void run(){if(!enabled)return;try{if(worker.tick())metrics.counter("travbook.journey.care","outcome","progressed","mode","MOCK").increment();}catch(RuntimeException unknown){metrics.counter("travbook.journey.care","outcome","storage_failure","mode","MOCK").increment();}}
}
