package com.travbook.notifications.domain.model;
import com.travbook.shared.domain.LifecycleSignal;
import java.time.Instant;
import java.util.*;
public record Notice(UUID id,UUID owner,UUID bookingId,String source,String title,String body,String route,Instant occurredAt,Instant readAt,int templateVersion,String mode,List<DeliveryView> deliveries) {
    public record DeliveryView(UUID id,String channel,String state,int attempts,String reference){}
    public Notice {deliveries=List.copyOf(deliveries);}
    public static Notice from(LifecycleSignal signal){
        String label=switch(signal.source()){case "hotel"->"Hotel";case "train"->"Train";case "journey","journey-aftercare"->"Journey";default->"Flight";};
        String body="MOCK update — "+signal.states().entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e->e.getKey()+": "+e.getValue().replace('_',' ')).reduce((a,b)->a+"; "+b).orElse("Status updated")+". Open your booking for its current status. No real funds or travel.";
        return new Notice(UUID.randomUUID(),signal.owner(),signal.bookingId(),signal.source(),label+" booking update",body,signal.route(),signal.occurredAt(),null,1,"MOCK",List.of());
    }
}
