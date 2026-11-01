package com.travbook.booking.adapter.out.persistence;
import com.travbook.booking.application.port.in.BookingHistory;
import com.travbook.booking.application.port.out.BookingEventInbox;
import com.travbook.booking.domain.event.*;
import com.travbook.shared.domain.DomainFailure;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import static com.travbook.booking.domain.event.EventRejected.Code.*;
public final class JdbcBookingEventInbox implements BookingEventInbox {
    private final JdbcClient jdbc;private final TransactionTemplate tx;private final ObjectMapper json;
    public JdbcBookingEventInbox(JdbcClient jdbc,TransactionTemplate tx,ObjectMapper json){this.jdbc=jdbc;this.tx=tx;this.json=json;}
    public void record(BookingLifecycleEvent event){
        tx.executeWithoutResult(t->{
            // Serialize the projection for this aggregate, including concurrent exact redelivery.
            if(jdbc.sql("select id from booking_intent where id=:id for update").param("id",event.aggregateId()).query(UUID.class).optional().isEmpty())throw new EventRejected(EVENT_CONFLICT);
            var existing=jdbc.sql("select envelope::text from booking_timeline_event where event_id=:id").param("id",event.eventId()).query(String.class).optional();
            if(existing.isPresent()){
                if(!json.readValue(existing.get(),BookingLifecycleEvent.class).equals(event))throw new EventRejected(EVENT_CONFLICT);
                return;
            }
            // Bind the trusted local delivery to the actual immutable source, not just its routing metadata.
            var source=jdbc.sql("select envelope::text from booking_outbox where id=:id and aggregate_id=:aggregate and aggregate_version=:version")
                .param("id",event.eventId()).param("aggregate",event.aggregateId()).param("version",event.aggregateVersion()).query(String.class).optional();
            if(source.isEmpty()||!json.readValue(source.get(),BookingLifecycleEvent.class).equals(event))throw new EventRejected(EVENT_CONFLICT);
            var previous=jdbc.sql("select envelope::text from booking_timeline_event where booking_id=:id order by aggregate_version desc limit 1")
                .param("id",event.aggregateId()).query(String.class).optional().map(body->json.readValue(body,BookingLifecycleEvent.class));
            event.requireSuccessorOf(previous.orElse(null));
            jdbc.sql("insert into booking_timeline_event(event_id,booking_id,aggregate_version,envelope) values(:id,:booking,:version,cast(:body as jsonb))")
                .param("id",event.eventId()).param("booking",event.aggregateId()).param("version",event.aggregateVersion()).param("body",json.writeValueAsString(event)).update();
        });
    }
    public BookingHistory.Page ownedHistory(UUID owner,UUID bookingId,long after,int size){
        return tx.execute(t->{
            // Shared aggregate lock gives a coherent booking/projection snapshot; no external call is made.
            long version=jdbc.sql("select version from booking_intent where id=:id and customer_id=:owner for share")
                .param("id",bookingId).param("owner",owner).query(Long.class).optional().orElseThrow(()->new DomainFailure(DomainFailure.Code.NOT_FOUND,"Booking intent not found."));
            long delivered=jdbc.sql("select coalesce(max(aggregate_version),0) from booking_timeline_event where booking_id=:id").param("id",bookingId).query(Long.class).single();
            if(after>delivered)throw new DomainFailure(DomainFailure.Code.VALIDATION_FAILED,"The cursor exceeds the delivered history.");
            var rows=jdbc.sql("select envelope::text,recorded_at from booking_timeline_event where booking_id=:id and aggregate_version>:after order by aggregate_version limit :size")
                .param("id",bookingId).param("after",after).param("size",size+1).query((rs,n)->{
                    var e=json.readValue(rs.getString(1),BookingLifecycleEvent.class);
                    return new BookingHistory.Entry(e.eventId(),e.aggregateVersion(),e.eventType(),e.payload().status(),e.payload().failureCode(),e.occurredAt(),rs.getTimestamp(2).toInstant());
                }).list();
            boolean dead=jdbc.sql("select exists(select 1 from booking_event_delivery d join booking_outbox o on o.id=d.event_id where o.aggregate_id=:id and d.status='DEAD')")
                .param("id",bookingId).query(Boolean.class).single();
            var items=rows.stream().limit(size).toList();
            var state=dead?BookingHistory.DeliveryState.ATTENTION_REQUIRED:delivered==version?BookingHistory.DeliveryState.CAUGHT_UP:BookingHistory.DeliveryState.PENDING;
            return new BookingHistory.Page(bookingId,"MOCK",version,delivered,state,items,rows.size()>size?items.getLast().version():null);
        });
    }
}
