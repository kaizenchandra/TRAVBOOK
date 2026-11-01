package com.travbook.booking.adapter.in.worker;
import com.travbook.booking.application.port.in.DispatchBookingEvents;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import io.micrometer.core.instrument.MeterRegistry;
@Component
@ConditionalOnProperty(name="travbook.booking.events-enabled",havingValue="true",matchIfMissing=true)
public class BookingEventScheduler {
    private final DispatchBookingEvents worker;private final MeterRegistry metrics;
    private volatile DispatchBookingEvents.Health health=new DispatchBookingEvents.Health(0,0,0);
    public BookingEventScheduler(DispatchBookingEvents worker,MeterRegistry metrics){
        this.worker=worker;this.metrics=metrics;
        io.micrometer.core.instrument.Gauge.builder("travbook.booking.events.pending",this,s->s.health.pending()).register(metrics);
        io.micrometer.core.instrument.Gauge.builder("travbook.booking.events.quarantined",this,s->s.health.quarantined()).register(metrics);
        io.micrometer.core.instrument.Gauge.builder("travbook.booking.events.oldest.seconds",this,s->s.health.oldestSeconds()).register(metrics);
    }
    @Scheduled(fixedDelayString="${travbook.booking.events-health-ms:10000}")
    public void inspect(){
        try{health=worker.health();}
        catch(RuntimeException unavailable){metrics.counter("travbook.booking.events","outcome","HEALTH_READ_FAILURE","mode","MOCK").increment();}
    }
    @Scheduled(fixedDelayString="${travbook.booking.events-poll-ms:250}")
    public void tick(){
        try{var result=worker.runNext();if(result!=DispatchBookingEvents.Outcome.IDLE)metrics.counter("travbook.booking.events","outcome",result.name(),"mode","MOCK").increment();}
        catch(RuntimeException unavailable){metrics.counter("travbook.booking.events","outcome","STORAGE_FAILURE","mode","MOCK").increment();}
    }
}
