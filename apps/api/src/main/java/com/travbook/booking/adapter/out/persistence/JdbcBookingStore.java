package com.travbook.booking.adapter.out.persistence;
import com.travbook.booking.application.port.in.BookingIntents;
import com.travbook.booking.application.port.out.BookingStore;
import com.travbook.booking.domain.model.*;
import com.travbook.shared.domain.DomainFailure;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.sql.*;
import java.time.*;
import java.util.*;
import static com.travbook.shared.domain.DomainFailure.Code.*;
public final class JdbcBookingStore implements BookingStore {
    private final JdbcClient jdbc;private final TransactionTemplate tx;private final ObjectMapper json;private final Clock clock;
    private record Input(BookingQuote quote,List<Traveller> travellers,String fingerprint,Instant createdAt,UUID correlationId,UUID commandId) {}
    private record Command(String fingerprint,UUID bookingId) {}
    private record Job(String status,int attempts,UUID token,Instant leaseUntil,Instant readStarted,Instant databaseNow) {
        boolean owned(UUID expected){return "RUNNING".equals(status)&&expected.equals(token)&&leaseUntil.isAfter(databaseNow);}
    }
    public JdbcBookingStore(JdbcClient jdbc,TransactionTemplate tx,ObjectMapper json,Clock clock){this.jdbc=jdbc;this.tx=tx;this.json=json;this.clock=clock;}
    private Instant instant(ResultSet rs,String column)throws SQLException {var t=rs.getTimestamp(column);return t==null?null:t.toInstant();}
    private Booking booking(ResultSet rs,int n)throws SQLException {
        var input=json.readValue(rs.getString("input_snapshot"),Input.class);
        return new Booking(rs.getObject("id",UUID.class),input.quote(),input.travellers(),input.fingerprint(),input.createdAt(),
            instant(rs,"updated_at"),rs.getLong("version"),Booking.Status.valueOf(rs.getString("status")),rs.getString("failure_code"),input.correlationId(),input.commandId());
    }
    private Optional<Booking> byId(UUID owner,UUID id) {
        return jdbc.sql("select * from booking_intent where customer_id=:owner and id=:id").param("owner",owner).param("id",id).query(this::booking).optional();
    }
    public Optional<Booking> forQuote(UUID owner,UUID quoteId) {
        return jdbc.sql("select * from booking_intent where customer_id=:owner and quote_id=:quote").param("owner",owner).param("quote",quoteId).query(this::booking).optional();
    }
    public Optional<Booking> replay(UUID owner,String key,String fingerprint) {
        return jdbc.sql("select fingerprint,booking_id from booking_command where customer_id=:owner and operation='CREATE_BOOKING' and command_key=:key")
            .param("owner",owner).param("key",key).query((rs,n)->new Command(rs.getString(1),rs.getObject(2,UUID.class))).optional().map(c->{
                if(!c.fingerprint().equals(fingerprint))throw new DomainFailure(IDEMPOTENCY_CONFLICT,"This key was used for a different booking request.");
                return byId(owner,c.bookingId()).orElseThrow();
            });
    }
    public Booking create(Booking requested,String key) {
        return tx.execute(transaction->{
            UUID owner=requested.quote().customerId();
            var replay=replay(owner,key,requested.fingerprint());if(replay.isPresent())return replay.get();
            var input=new Input(requested.quote(),requested.travellers(),requested.fingerprint(),requested.createdAt(),requested.correlationId(),requested.commandId());
            boolean inserted=jdbc.sql("insert into booking_intent(id,quote_id,customer_id,fingerprint,mode,input_snapshot,created_at,updated_at,version,status) values(:id,:quote,:owner,:hash,'MOCK',cast(:input as jsonb),:created,:created,1,'CREATED') on conflict(quote_id) do nothing")
                .param("id",requested.id()).param("quote",requested.quote().quoteId()).param("owner",owner).param("hash",requested.fingerprint())
                .param("input",json.writeValueAsString(input)).param("created",Timestamp.from(requested.createdAt())).update()==1;
            var current=forQuote(owner,requested.quote().quoteId()).orElseThrow(()->new DomainFailure(NOT_FOUND,"Quotation not found."));
            boolean claimed=jdbc.sql("insert into booking_command(customer_id,operation,command_key,fingerprint,booking_id) values(:owner,'CREATE_BOOKING',:key,:hash,:id) on conflict do nothing")
                .param("owner",owner).param("key",key).param("hash",requested.fingerprint()).param("id",current.id()).update()==1;
            if(!claimed)return replay(owner,key,requested.fingerprint()).orElseThrow();
            if(!current.fingerprint().equals(requested.fingerprint()))throw new DomainFailure(BOOKING_CONFLICT,"This quote already has an intent with different traveller details.");
            if(inserted) {
                requested.quote().requireValid(clock.instant());
                jdbc.sql("insert into booking_workflow(booking_id,step,status,next_attempt_at) values(:id,'VALIDATE_OFFER','READY',clock_timestamp())").param("id",current.id()).update();
                jdbc.sql("insert into booking_reservation(booking_id,mode,status,inventory_status) values(:id,'MOCK','NOT_REQUESTED','NOT_CHECKED')").param("id",current.id()).update();
                event(current,"BookingCreated");
            }
            return current;
        });
    }
    public Optional<BookingIntents.Details> findOwned(UUID owner,UUID id) {
        return jdbc.sql("select b.*,w.status as workflow_status,w.definition_version,w.attempts,w.next_attempt_at,r.inventory_status from booking_intent b join booking_workflow w on w.booking_id=b.id join booking_reservation r on r.booking_id=b.id where b.id=:id and b.customer_id=:owner")
            .param("id",id).param("owner",owner).query((rs,n)->{
                String state=rs.getString("workflow_status");boolean terminal=Set.of("SUCCEEDED","FAILED","NEEDS_REVIEW").contains(state);
                return new BookingIntents.Details(booking(rs,n),new BookingIntents.WorkflowView(rs.getInt("definition_version"),state,rs.getInt("attempts"),terminal||state.equals("RUNNING")?null:instant(rs,"next_attempt_at"),terminal),"NOT_REQUESTED",rs.getString("inventory_status"));
            }).optional();
    }
    public BookingIntents.Page listOwned(UUID owner,UUID after,int size) {
        if(after!=null&&byId(owner,after).isEmpty())throw new DomainFailure(VALIDATION_FAILED,"Invalid booking cursor.");
        var statement=jdbc.sql("select * from booking_intent where customer_id=:owner "+
            (after==null?"":"and (created_at,id)<(select created_at,id from booking_intent where id=:after and customer_id=:owner) ")+
            "order by created_at desc,id desc limit :size").param("owner",owner).param("size",size+1);
        if(after!=null)statement.param("after",after);
        var rows=statement.query(this::booking).list();boolean more=rows.size()>size;var items=rows.stream().limit(size).toList();
        return new BookingIntents.Page(items,more?items.getLast().id():null);
    }
    public Optional<Claim> claim(Duration lease) {
        return tx.execute(transaction->{
            var id=jdbc.sql("select booking_id from booking_workflow where definition_version=1 and ((status in ('READY','WAITING_RETRY') and next_attempt_at<=clock_timestamp()) or (status='RUNNING' and lease_until<clock_timestamp())) order by next_attempt_at,booking_id for update skip locked limit 1")
                .query(UUID.class).optional();
            if(id.isEmpty())return Optional.empty();
            var current=jdbc.sql("select * from booking_intent where id=:id for update").param("id",id.get()).query(this::booking).single();
            var started=current.start(clock.instant());if(started!=current){save(current,started);event(started,"BookingValidationStarted");}
            UUID token=UUID.randomUUID();
            jdbc.sql("update booking_workflow set status='RUNNING',lease_token=:token,lease_until=clock_timestamp()+(:seconds * interval '1 second'),claim_count=claim_count+1,read_started_at=null where booking_id=:id")
                .param("token",token).param("seconds",lease.toSeconds()).param("id",id.get()).update();
            return Optional.of(new Claim(started,token));
        });
    }
    private Job lockJob(UUID id) {
        return jdbc.sql("select *,clock_timestamp() as db_now from booking_workflow where booking_id=:id for update").param("id",id)
            .query((rs,n)->new Job(rs.getString("status"),rs.getInt("attempts"),rs.getObject("lease_token",UUID.class),instant(rs,"lease_until"),instant(rs,"read_started_at"),instant(rs,"db_now"))).single();
    }
    public int beginRead(Claim claim,int maxAttempts) {
        return tx.execute(transaction->{
            var job=lockJob(claim.booking().id());
            if(!job.owned(claim.token())||job.readStarted()!=null)return 0;
            if(job.attempts()>=maxAttempts)return -1;
            int changed=jdbc.sql("update booking_workflow set attempts=attempts+1,read_started_at=clock_timestamp() where booking_id=:id and lease_token=:token and lease_until>clock_timestamp() and read_started_at is null")
                .param("id",claim.booking().id()).param("token",claim.token()).update();
            return changed==1?job.attempts()+1:0;
        });
    }
    public boolean finish(Claim claim,Booking.Decision requested) {
        return tx.execute(transaction->{
            UUID id=claim.booking().id();var job=lockJob(id);
            if(!job.owned(claim.token()))return false;
            if(job.readStarted()==null&&requested!=Booking.Decision.EXPIRED&&requested!=Booking.Decision.REVIEW)throw new IllegalStateException("Validation read was not durably started");
            var current=jdbc.sql("select * from booking_intent where id=:id for update").param("id",id).query(this::booking).single();
            var decision=current.quote().expiresAt().isAfter(clock.instant())?requested:Booking.Decision.EXPIRED;
            var next=current.complete(decision,clock.instant());
            save(current,next);event(next,eventType(decision));
            String state=switch(decision){case VALIDATED->"SUCCEEDED";case RETRY->"WAITING_RETRY";case REVIEW->"NEEDS_REVIEW";default->"FAILED";};
            String inventory=switch(decision){case VALIDATED,PRICE_CHANGED,TERMS_CHANGED->"AVAILABLE";case UNAVAILABLE->"UNAVAILABLE";default->"UNKNOWN";};
            jdbc.sql("update booking_reservation set inventory_status=:inventory,checked_at=clock_timestamp() where booking_id=:id").param("inventory",inventory).param("id",id).update();
            long delay=current.retryDelay(Math.max(job.attempts(),1)).toMillis();
            int updated=jdbc.sql("update booking_workflow set status=:status,next_attempt_at=case when :retry then clock_timestamp()+(:delay * interval '1 millisecond') else null end,lease_token=null,lease_until=null where booking_id=:id and lease_token=:token and lease_until>clock_timestamp()")
                .param("status",state).param("retry",decision==Booking.Decision.RETRY).param("delay",delay).param("id",id).param("token",claim.token()).update();
            if(updated==0){transaction.setRollbackOnly();return false;}return true;
        });
    }
    private String eventType(Booking.Decision d) {
        return switch(d){case VALIDATED->"BookingAwaitingPayment";case RETRY->"BookingValidationRetryScheduled";case REVIEW->"BookingManualReviewRequired";default->"BookingValidationRejected";};
    }
    private void save(Booking previous,Booking next) {
        int changed=jdbc.sql("update booking_intent set status=:status,failure_code=:failure,version=:next,updated_at=:now where id=:id and version=:previous")
            .param("status",next.status().name()).param("failure",next.failureCode()).param("next",next.version()).param("now",Timestamp.from(next.updatedAt()))
            .param("id",next.id()).param("previous",previous.version()).update();
        if(changed!=1)throw new IllegalStateException("Concurrent booking transition rejected");
    }
    private void event(Booking b,String type) {
        UUID eventId=UUID.randomUUID();var payload=new LinkedHashMap<String,Object>();
        payload.put("bookingId",b.id());payload.put("status",b.status());payload.put("failureCode",b.failureCode());
        var envelope=new LinkedHashMap<String,Object>(Map.of("eventId",eventId,"eventType",type,"schemaVersion",1,"aggregateId",b.id(),"aggregateVersion",b.version(),
            "occurredAt",b.updatedAt().toString(),"correlationId",b.correlationId(),"causationId",b.commandId(),"producer","booking","payload",payload));envelope.put("mode","MOCK");
        jdbc.sql("insert into booking_outbox(id,aggregate_id,aggregate_version,event_type,mode,envelope) values(:id,:aggregate,:version,:type,'MOCK',cast(:envelope as jsonb))")
            .param("id",eventId).param("aggregate",b.id()).param("version",b.version()).param("type",type).param("envelope",json.writeValueAsString(envelope)).update();
    }
}

