package com.travbook.notifications.adapter.in.messaging;
import com.travbook.notifications.application.port.in.ProcessNotifications;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import io.micrometer.core.instrument.MeterRegistry;
@Component
@ConditionalOnProperty(name="travbook.notifications.worker-enabled",havingValue="true")
public class NotificationScheduler {
    private final ProcessNotifications notifications;private final MeterRegistry metrics;
    public NotificationScheduler(ProcessNotifications notifications,MeterRegistry metrics){this.notifications=notifications;this.metrics=metrics;}
    @Scheduled(fixedDelayString="${travbook.notifications.poll-ms:2000}") public void run(){try{metrics.counter("travbook.notifications.forwarded").increment(notifications.relay());}catch(RuntimeException e){metrics.counter("travbook.notifications.relay.errors").increment();}for(int i=0;i<10;i++)try{if(!notifications.deliver())break;}catch(RuntimeException e){metrics.counter("travbook.notifications.delivery.errors").increment();}}
}
