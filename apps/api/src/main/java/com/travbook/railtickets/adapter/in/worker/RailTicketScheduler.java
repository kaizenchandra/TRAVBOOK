package com.travbook.railtickets.adapter.in.worker;
import com.travbook.railtickets.application.port.in.ProcessRailTickets;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
import io.micrometer.core.instrument.MeterRegistry;
@Component
public class RailTicketScheduler {
 private final ProcessRailTickets worker;private final boolean enabled;private final MeterRegistry metrics;
 public RailTicketScheduler(ProcessRailTickets worker,@Value("${RAIL_TICKET_WORKER_ENABLED:true}") boolean enabled,MeterRegistry metrics){this.worker=worker;this.enabled=enabled;this.metrics=metrics;}
 @Scheduled(fixedDelayString="${RAIL_TICKET_POLL_MS:300}") public void run(){if(enabled&&worker.tick())metrics.counter("travbook.railtickets.worker","mode","MOCK").increment();}
}
