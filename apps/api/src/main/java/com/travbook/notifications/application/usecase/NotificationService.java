package com.travbook.notifications.application.usecase;
import com.travbook.notifications.application.port.in.*;
import com.travbook.notifications.application.port.out.*;
import com.travbook.notifications.domain.model.*;
import com.travbook.customer.application.port.in.*;
import com.travbook.shared.application.port.LifecycleSource;
import com.travbook.shared.domain.*;
import java.time.Clock;
import java.util.*;
public final class NotificationService implements Notifications,ProcessNotifications {
    private final NotificationStore store;private final NotificationGateway gateway;private final Customers customers;private final CustomerOrganizer organizer;private final List<LifecycleSource> sources;private final Clock clock;
    public NotificationService(NotificationStore store,NotificationGateway gateway,Customers customers,CustomerOrganizer organizer,List<LifecycleSource> sources,Clock clock){this.store=store;this.gateway=gateway;this.customers=customers;this.organizer=organizer;this.sources=List.copyOf(sources);this.clock=clock;}
    public Page list(CustomerIdentity identity,UUID after){return store.list(customers.resolve(identity).id(),after);}
    public Notice read(CustomerIdentity identity,UUID id){return store.read(customers.resolve(identity).id(),id,clock.instant());}
    public void accept(LifecycleSignal signal){var preferences=organizer.preferences(signal.owner());store.accept(signal,preferences.email(),preferences.sms(),clock.instant());}
    public int relay(){int count=0;RuntimeException last=null;for(var source:sources)try{for(var signal:source.pending()){accept(signal);source.acknowledge(signal.eventId());count++;}}catch(RuntimeException error){last=error;}if(last!=null)throw last;return count;}
    public boolean deliver(){var claim=store.claim(clock.instant());if(claim.isEmpty())return false;var c=claim.get();var d=c.delivery();
        try{var receipt=gateway.lookup(d);if(receipt.isPresent()){store.complete(c,d.completed(Delivery.State.SIMULATED_DELIVERED,receipt.get(),clock.instant()),clock.instant());return true;}
            var p=organizer.preferences(d.owner());if(!(d.channel()==Delivery.Channel.EMAIL?p.email():p.sms())){store.complete(c,d.completed(Delivery.State.SUPPRESSED,null,clock.instant()),clock.instant());return true;}
            if(c.lookupOnly()){store.complete(c,d.completed(Delivery.State.REVIEW,null,clock.instant()),clock.instant());return true;}
            String reference=gateway.deliver(d);store.complete(c,d.completed(Delivery.State.SIMULATED_DELIVERED,reference,clock.instant()),clock.instant());
        }catch(RuntimeException error){store.complete(c,d.completed(d.attempts()>=3?Delivery.State.REVIEW:Delivery.State.RETRY,null,clock.instant()),clock.instant());throw error;}
        return true;
    }
}
