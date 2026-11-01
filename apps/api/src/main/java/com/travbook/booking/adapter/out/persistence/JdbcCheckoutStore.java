package com.travbook.booking.adapter.out.persistence;
import com.travbook.booking.application.port.in.BookingCheckouts;
import com.travbook.booking.application.port.out.CheckoutStore;
import com.travbook.booking.domain.model.Checkout;
import com.travbook.shared.domain.DomainFailure;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import static com.travbook.shared.domain.DomainFailure.Code.*;
public final class JdbcCheckoutStore implements CheckoutStore {
    private final JdbcClient jdbc;private final TransactionTemplate tx;private final ObjectMapper json;private final Clock clock;
    public JdbcCheckoutStore(JdbcClient jdbc,TransactionTemplate tx,ObjectMapper json,Clock clock){this.jdbc=jdbc;this.tx=tx;this.json=json;this.clock=clock;}
    public Optional<Checkout> replay(UUID owner,String key,String fingerprint){
        return jdbc.sql("select c.snapshot::text,k.fingerprint from booking_checkout_command k join booking_checkout c on c.id=k.checkout_id where k.customer_id=:owner and k.command_key=:key")
            .param("owner",owner).param("key",key).query((rs,n)->{if(!fingerprint.equals(rs.getString(2)))throw new DomainFailure(IDEMPOTENCY_CONFLICT,"This key belongs to another checkout request.");return json.readValue(rs.getString(1),Checkout.class);}).optional();
    }
    public Optional<BookingCheckouts.View> find(UUID owner,UUID bookingId){
        return jdbc.sql("select c.snapshot::text,w.attempts from booking_checkout c join booking_checkout_work w on w.checkout_id=c.id where c.customer_id=:owner and c.booking_id=:booking")
            .param("owner",owner).param("booking",bookingId).query((rs,n)->new BookingCheckouts.View(json.readValue(rs.getString(1),Checkout.class),Math.min(rs.getInt(2),5))).optional();
    }
    public Checkout create(Checkout c,String key,String fingerprint){return tx.execute(t->{
        var owner=c.quote().customerId();var prior=replay(owner,key,fingerprint);if(prior.isPresent())return prior.get();
        boolean inserted=jdbc.sql("insert into booking_checkout(id,booking_id,customer_id,scenario,snapshot,version,status,step,payment_status,reservation_status,created_at,updated_at) values(:id,:booking,:owner,:scenario,cast(:snapshot as jsonb),:version,:status,:step,:payment,:reservation,:created,:updated) on conflict(booking_id) do nothing")
            .param("id",c.id()).param("booking",c.bookingId()).param("owner",owner).param("scenario",c.scenario().name()).param("snapshot",json.writeValueAsString(c)).param("version",c.version()).param("status",c.status().name()).param("step",c.step().name()).param("payment",c.payment().name()).param("reservation",c.reservation().name()).param("created",Timestamp.from(c.createdAt())).param("updated",Timestamp.from(c.updatedAt())).update()==1;
        var current=jdbc.sql("select snapshot::text from booking_checkout where booking_id=:id and customer_id=:owner").param("id",c.bookingId()).param("owner",owner).query(String.class).optional().map(s->json.readValue(s,Checkout.class)).orElseThrow(()->new DomainFailure(NOT_FOUND,"Booking intent not found."));
        if(current.scenario()!=c.scenario())throw new DomainFailure(BOOKING_CONFLICT,"A different checkout already exists.");
        boolean keyClaimed=jdbc.sql("insert into booking_checkout_command(customer_id,command_key,fingerprint,checkout_id) values(:owner,:key,:fingerprint,:id) on conflict do nothing")
            .param("owner",owner).param("key",key).param("fingerprint",fingerprint).param("id",current.id()).update()==1;
        if(!keyClaimed)return replay(owner,key,fingerprint).orElseThrow();
        if(inserted){c.quote().requireValid(clock.instant());jdbc.sql("insert into booking_checkout_work(checkout_id,state) values(:id,'READY')").param("id",c.id()).update();event(c);}
        return current;
    });}
    public Optional<Claim> claim(Duration lease){return tx.execute(t->{
        var selected=jdbc.sql("select checkout_id,attempts from booking_checkout_work where definition_version=1 and ((state in ('READY','RETRY') and next_attempt_at<=clock_timestamp()) or (state='RUNNING' and lease_until<clock_timestamp())) order by next_attempt_at,checkout_id for update skip locked limit 1")
            .query((rs,n)->Map.entry(rs.getObject(1,UUID.class),rs.getInt(2))).optional();if(selected.isEmpty())return Optional.empty();
        var id=selected.get().getKey();var body=jdbc.sql("select snapshot::text from booking_checkout where id=:id").param("id",id).query(String.class).single();
        var c=json.readValue(body,Checkout.class);UUID token=UUID.randomUUID();int attempt=Math.min(6,selected.get().getValue()+1);
        // The provider may commit before its response is recorded. Never leave NOT_STARTED/NOT_REQUESTED visible after dispatch begins.
        if(c.step()!=Checkout.Step.REVALIDATE){var pending=c.uncertain(false,clock.instant());save(c,pending);event(pending);c=pending;}
        jdbc.sql("update booking_checkout_work set state='RUNNING',attempts=:attempt,lease_token=:token,lease_until=clock_timestamp()+(:seconds*interval '1 second') where checkout_id=:id")
            .param("attempt",attempt).param("token",token).param("seconds",lease.toSeconds()).param("id",id).update();
        return Optional.of(new Claim(c,token,attempt));
    });}
    public boolean finish(Claim claim,Checkout next){return tx.execute(t->{
        var c=claim.checkout();
        boolean owned=jdbc.sql("select (state='RUNNING' and lease_token=:token and lease_until>clock_timestamp()) from booking_checkout_work where checkout_id=:id for update")
            .param("token",claim.token()).param("id",c.id()).query(Boolean.class).single();if(!owned)return false;
        if(!next.id().equals(c.id())||next.version()!=c.version()+1)throw new IllegalArgumentException("Invalid next checkout version");
        save(c,next);event(next);
        boolean same=c.step()==next.step();String state=next.status()==Checkout.Status.REVIEW?"REVIEW":next.terminal()?"DONE":same?"RETRY":"READY";
        long delay=same&&!next.terminal()?(1L<<Math.min(claim.attempt()-1,3))*1000+Math.floorMod(c.id().hashCode()+claim.attempt(),251):0;
        int updated=jdbc.sql("update booking_checkout_work set state=:state,attempts=:attempt,next_attempt_at=clock_timestamp()+(:delay*interval '1 millisecond'),lease_token=null,lease_until=null where checkout_id=:id and lease_token=:token and lease_until>clock_timestamp()")
            .param("state",state).param("attempt",same||next.terminal()?claim.attempt():0).param("delay",delay).param("id",c.id()).param("token",claim.token()).update();
        if(updated==0){t.setRollbackOnly();return false;}return true;
    });}
    private void save(Checkout previous,Checkout next){
        int updated=jdbc.sql("update booking_checkout set snapshot=cast(:snapshot as jsonb),version=:version,status=:status,step=:step,payment_status=:payment,reservation_status=:reservation,updated_at=:updated where id=:id and version=:previous")
            .param("snapshot",json.writeValueAsString(next)).param("version",next.version()).param("status",next.status().name()).param("step",next.step().name()).param("payment",next.payment().name()).param("reservation",next.reservation().name()).param("updated",Timestamp.from(next.updatedAt())).param("id",previous.id()).param("previous",previous.version()).update();
        if(updated!=1)throw new IllegalStateException("Concurrent checkout transition");
    }
    private void event(Checkout c){
        UUID id=UUID.randomUUID();var payload=new LinkedHashMap<String,Object>();payload.put("bookingId",c.bookingId());payload.put("status",c.status());payload.put("step",c.step());payload.put("payment",c.payment());payload.put("reservation",c.reservation());payload.put("failureCode",c.failureCode());
        var envelope=new LinkedHashMap<String,Object>(Map.of("eventId",id,"eventType","CheckoutStateRecorded","schemaVersion",1,"aggregateId",c.id(),"aggregateVersion",c.version(),"occurredAt",c.updatedAt(),"correlationId",c.correlationId(),"causationId",c.commandId(),"producer","booking","payload",payload));envelope.put("mode","MOCK");
        jdbc.sql("insert into booking_checkout_outbox(id,aggregate_id,aggregate_version,event_type,mode,envelope) values(:id,:aggregate,:version,'CheckoutStateRecorded','MOCK',cast(:body as jsonb))")
            .param("id",id).param("aggregate",c.id()).param("version",c.version()).param("body",json.writeValueAsString(envelope)).update();
    }
}
