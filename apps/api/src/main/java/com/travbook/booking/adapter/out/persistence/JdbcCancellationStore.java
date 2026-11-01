package com.travbook.booking.adapter.out.persistence;
import com.travbook.booking.application.port.in.BookingCancellations;
import com.travbook.booking.application.port.out.CancellationStore;
import com.travbook.booking.domain.model.*;
import com.travbook.shared.domain.DomainFailure;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import static com.travbook.shared.domain.DomainFailure.Code.*;
public final class JdbcCancellationStore implements CancellationStore {
    private final JdbcClient jdbc;private final TransactionTemplate tx;private final ObjectMapper json;private final Clock clock;
    public JdbcCancellationStore(JdbcClient jdbc,TransactionTemplate tx,ObjectMapper json,Clock clock){this.jdbc=jdbc;this.tx=tx;this.json=json;this.clock=clock;}
    private Optional<UUID> command(UUID owner,String operation,String key,String fingerprint){return jdbc.sql("select resource_id,fingerprint from booking_cancellation_command where customer_id=:owner and operation=:op and command_key=:key")
        .param("owner",owner).param("op",operation).param("key",key).query((r,n)->{if(!fingerprint.equals(r.getString(2)))throw new DomainFailure(IDEMPOTENCY_CONFLICT,"Cancellation key belongs to a different request.");return r.getObject(1,UUID.class);}).optional();}
    private boolean command(UUID owner,String operation,String key,String fingerprint,UUID resource){return jdbc.sql("insert into booking_cancellation_command(customer_id,operation,command_key,fingerprint,resource_id) values(:owner,:op,:key,:fingerprint,:resource) on conflict do nothing")
        .param("owner",owner).param("op",operation).param("key",key).param("fingerprint",fingerprint).param("resource",resource).update()==1;}
    public Optional<CancellationQuote> replayQuote(UUID owner,String key,UUID bookingId){return command(owner,"QUOTE",key,bookingId.toString()).map(id->quote(owner,id).orElseThrow());}
    public Optional<CancellationQuote> quote(UUID owner,UUID id){return jdbc.sql("select snapshot::text from booking_cancellation_quote where customer_id=:owner and id=:id").param("owner",owner).param("id",id).query(String.class).optional().map(s->json.readValue(s,CancellationQuote.class));}
    public CancellationQuote saveQuote(CancellationQuote q,String key,UUID correlation){return tx.execute(t->{
        var prior=replayQuote(q.owner(),key,q.bookingId());if(prior.isPresent())return prior.get();
        jdbc.sql("insert into booking_cancellation_quote(id,customer_id,booking_id,checkout_id,snapshot,created_at,expires_at) values(:id,:owner,:booking,:checkout,cast(:body as jsonb),:created,:expires)")
            .param("id",q.id()).param("owner",q.owner()).param("booking",q.bookingId()).param("checkout",q.checkout().id()).param("body",json.writeValueAsString(q)).param("created",Timestamp.from(q.createdAt())).param("expires",Timestamp.from(q.expiresAt())).update();
        if(!command(q.owner(),"QUOTE",key,q.bookingId().toString(),q.id())){var original=replayQuote(q.owner(),key,q.bookingId()).orElseThrow();t.setRollbackOnly();return original;}
        q.requireValid(clock.instant());event(q.id(),1,"CancellationQuoteCreated",q.createdAt(),correlation,q.id(),Map.of("bookingId",q.bookingId(),"refundAmount",q.refundAmount().amount().toPlainString(),"currency",q.refundAmount().currency()));return q;
    });}
    private Optional<Cancellation> byId(UUID owner,UUID id){return jdbc.sql("select snapshot::text from booking_cancellation where customer_id=:owner and id=:id").param("owner",owner).param("id",id).query(String.class).optional().map(s->json.readValue(s,Cancellation.class));}
    public Optional<Cancellation> replay(UUID owner,String key,String fingerprint){return command(owner,"CANCEL",key,fingerprint).map(id->byId(owner,id).orElseThrow());}
    public Optional<BookingCancellations.View> find(UUID owner,UUID bookingId){return jdbc.sql("select c.snapshot::text,w.attempts from booking_cancellation c join booking_cancellation_work w on w.cancellation_id=c.id where c.customer_id=:owner and c.booking_id=:booking")
        .param("owner",owner).param("booking",bookingId).query((r,n)->new BookingCancellations.View(json.readValue(r.getString(1),Cancellation.class),Math.min(5,r.getInt(2)))).optional();}
    public Cancellation create(Cancellation c,String key,String fingerprint){return tx.execute(t->{
        UUID owner=c.quote().owner();var prior=replay(owner,key,fingerprint);if(prior.isPresent())return prior.get();
        boolean inserted=jdbc.sql("insert into booking_cancellation(id,customer_id,booking_id,quote_id,snapshot,version,cancellation_status,refund_status) values(:id,:owner,:booking,:quote,cast(:body as jsonb),1,'PENDING','NOT_REQUESTED') on conflict(booking_id) do nothing")
            .param("id",c.id()).param("owner",owner).param("booking",c.quote().bookingId()).param("quote",c.quote().id()).param("body",json.writeValueAsString(c)).update()==1;
        var current=jdbc.sql("select snapshot::text from booking_cancellation where customer_id=:owner and booking_id=:booking").param("owner",owner).param("booking",c.quote().bookingId()).query(String.class).optional().map(s->json.readValue(s,Cancellation.class)).orElseThrow(()->new DomainFailure(NOT_FOUND,"Cancellation not found."));
        if(!current.quote().id().equals(c.quote().id())||current.scenario()!=c.scenario())throw new DomainFailure(BOOKING_CONFLICT,"A different cancellation already exists.");
        if(!command(owner,"CANCEL",key,fingerprint,current.id()))return replay(owner,key,fingerprint).orElseThrow();
        if(inserted){c.quote().requireValid(clock.instant());jdbc.sql("insert into booking_cancellation_work(cancellation_id,state) values(:id,'READY')").param("id",c.id()).update();event(c);}return current;
    });}
    public Optional<Claim> claim(Duration lease){return tx.execute(t->{
        var row=jdbc.sql("select cancellation_id,attempts from booking_cancellation_work where definition_version=1 and ((state in ('READY','RETRY') and next_attempt_at<=clock_timestamp()) or (state='RUNNING' and lease_until<clock_timestamp())) order by next_attempt_at,cancellation_id for update skip locked limit 1")
            .query((r,n)->Map.entry(r.getObject(1,UUID.class),r.getInt(2))).optional();if(row.isEmpty())return Optional.empty();var id=row.get().getKey();int attempt=Math.min(6,row.get().getValue()+1);UUID token=UUID.randomUUID();
        var c=json.readValue(jdbc.sql("select snapshot::text from booking_cancellation where id=:id").param("id",id).query(String.class).single(),Cancellation.class);
        jdbc.sql("update booking_cancellation_work set state='RUNNING',attempts=:attempt,lease_token=:token,lease_until=clock_timestamp()+(:seconds*interval '1 second') where cancellation_id=:id")
            .param("attempt",attempt).param("token",token).param("seconds",lease.toSeconds()).param("id",id).update();return Optional.of(new Claim(c,token,attempt));
    });}
    public boolean finish(Claim claim,Cancellation next){return tx.execute(t->{
        var c=claim.cancellation();boolean owned=jdbc.sql("select (state='RUNNING' and lease_token=:token and lease_until>clock_timestamp()) from booking_cancellation_work where cancellation_id=:id for update")
            .param("token",claim.token()).param("id",c.id()).query(Boolean.class).single();if(!owned)return false;
        if(!next.id().equals(c.id())||next.version()!=c.version()+1)throw new IllegalArgumentException("Invalid cancellation version");
        int changed=jdbc.sql("update booking_cancellation set snapshot=cast(:body as jsonb),version=:next,cancellation_status=:cancel,refund_status=:refund where id=:id and version=:previous")
            .param("body",json.writeValueAsString(next)).param("next",next.version()).param("cancel",next.cancellationStatus().name()).param("refund",next.refundStatus().name()).param("id",c.id()).param("previous",c.version()).update();if(changed!=1)throw new IllegalStateException("Concurrent cancellation update");event(next);
        boolean same=c.step()==next.step();boolean review=next.cancellationStatus()==Cancellation.State.REVIEW||next.refundStatus()==Cancellation.RefundState.REVIEW;
        long delay=same&&!next.terminal()?(1L<<Math.min(claim.attempt()-1,3))*1000+Math.floorMod(c.id().hashCode()+claim.attempt(),251):0;
        changed=jdbc.sql("update booking_cancellation_work set state=:state,attempts=:attempt,next_attempt_at=clock_timestamp()+(:delay*interval '1 millisecond'),lease_token=null,lease_until=null where cancellation_id=:id and lease_token=:token and lease_until>clock_timestamp()")
            .param("state",review?"REVIEW":next.terminal()?"DONE":same?"RETRY":"READY").param("attempt",same||next.terminal()?claim.attempt():0).param("delay",delay).param("id",c.id()).param("token",claim.token()).update();if(changed==0){t.setRollbackOnly();return false;}return true;
    });}
    private void event(Cancellation c){var payload=new LinkedHashMap<String,Object>();payload.put("bookingId",c.quote().bookingId());payload.put("cancellationStatus",c.cancellationStatus());payload.put("refundStatus",c.refundStatus());payload.put("failureCode",c.failureCode());event(c.id(),c.version(),"CancellationStateRecorded",c.updatedAt(),c.correlationId(),c.commandId(),payload);}
    private void event(UUID aggregate,long version,String type,Instant now,UUID correlation,UUID causation,Map<String,?> payload){
        UUID id=UUID.randomUUID();var body=new LinkedHashMap<String,Object>(Map.of("eventId",id,"aggregateId",aggregate,"aggregateVersion",version,"eventType",type,"schemaVersion",1,"producer","booking","mode","MOCK","occurredAt",now,"correlationId",correlation,"causationId",causation));body.put("payload",payload);
        jdbc.sql("insert into booking_aftercare_outbox(id,aggregate_id,aggregate_version,event_type,mode,envelope) values(:id,:aggregate,:version,:type,'MOCK',cast(:body as jsonb))").param("id",id).param("aggregate",aggregate).param("version",version).param("type",type).param("body",json.writeValueAsString(body)).update();
    }
}
