package com.travbook.booking.adapter.out.messaging;
import com.travbook.booking.application.port.in.RecordBookingEvent;
import com.travbook.booking.application.port.out.BookingEventDelivery;
import com.travbook.booking.domain.event.*;
import tools.jackson.databind.ObjectMapper;
import java.util.UUID;
import java.util.List;
/** Local transport; the published inbound consumer contract returns only after its own commit. */
public final class LocalBookingEventDelivery implements BookingEventDelivery {
    private final ObjectMapper json;private final RecordBookingEvent consumer;
    public LocalBookingEventDelivery(ObjectMapper json,RecordBookingEvent consumer){this.json=json;this.consumer=consumer;}
    public void deliver(UUID id,UUID aggregate,long version,String envelope){
        BookingLifecycleEvent event;
        try{
            var node=json.readTree(envelope);
            if(!node.isObject()||node.size()!=11||!node.path("schemaVersion").isIntegralNumber()||!node.path("aggregateVersion").isIntegralNumber())throw new IllegalArgumentException();
            for(String field:List.of("eventId","eventType","aggregateId","occurredAt","correlationId","causationId","producer","mode"))if(!node.path(field).isString())throw new IllegalArgumentException();
            var payload=node.path("payload");
            if(!payload.isObject()||payload.size()!=3||!payload.path("bookingId").isString()||!payload.path("status").isString()||
                !payload.has("failureCode")||!(payload.path("failureCode").isNull()||payload.path("failureCode").isString()))throw new IllegalArgumentException();
            event=json.treeToValue(node,BookingLifecycleEvent.class);
        }
        catch(RuntimeException malformed){throw new EventRejected(EventRejected.Code.INVALID_ENVELOPE);}
        if(event==null||!event.eventId().equals(id)||!event.aggregateId().equals(aggregate)||event.aggregateVersion()!=version)throw new EventRejected(EventRejected.Code.INVALID_ENVELOPE);
        consumer.accept(event);
    }
}
