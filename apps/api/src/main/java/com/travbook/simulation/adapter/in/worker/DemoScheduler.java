package com.travbook.simulation.adapter.in.worker;
import com.travbook.simulation.application.port.in.ProcessDemoOperations;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import io.micrometer.core.instrument.*;
@Component
@ConditionalOnProperty(name={"travbook.demo.enabled","travbook.demo.worker-enabled"},havingValue="true")
public class DemoScheduler {
    private final ProcessDemoOperations worker;private final Counter errors;
    public DemoScheduler(ProcessDemoOperations worker,MeterRegistry metrics){this.worker=worker;errors=metrics.counter("travbook.demo.worker.errors");}
    @Scheduled(fixedDelayString="${travbook.demo.poll-ms:1000}") public void run(){try{for(int i=0;i<10&&worker.tick();i++){/* bounded batch */}}catch(RuntimeException e){errors.increment();}}
}
