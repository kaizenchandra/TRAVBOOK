package com.travbook.journeys.adapter.in.worker;
import com.travbook.journeys.application.port.in.ProcessJourneys;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
import io.micrometer.core.instrument.MeterRegistry;
@Component public class JourneyScheduler {
 private final ProcessJourneys worker;private final boolean enabled;private final MeterRegistry metrics;
 public JourneyScheduler(ProcessJourneys w,@Value("${JOURNEY_WORKER_ENABLED:true}") boolean e,MeterRegistry m){worker=w;enabled=e;metrics=m;}
 @Scheduled(fixedDelayString="${JOURNEY_POLL_MS:300}") public void run(){if(!enabled)return;try{if(worker.tick())metrics.counter("travbook.journeys.worker","outcome","progressed","mode","MOCK").increment();}catch(RuntimeException e){metrics.counter("travbook.journeys.worker","outcome","storage_failure","mode","MOCK").increment();}}
}
