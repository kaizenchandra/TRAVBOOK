package com.travbook.flightprovider.adapter.out.persistence;

import com.travbook.flightprovider.application.port.out.ProviderStore;
import com.travbook.flightprovider.application.port.out.CouponClaims;
import com.travbook.flightprovider.domain.model.*;
import com.travbook.shared.domain.DomainFailure;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.function.*;
import static com.travbook.shared.domain.DomainFailure.Code.*;

public final class JdbcProviderStore implements ProviderStore {
    private final JdbcClient jdbc;private final TransactionTemplate tx;private final ObjectMapper json;private final CouponClaims coupons;
    public JdbcProviderStore(JdbcClient jdbc,TransactionTemplate tx,ObjectMapper json,CouponClaims coupons){this.jdbc=jdbc;this.tx=tx;this.json=json;this.coupons=coupons;}
    private <T>T decode(String payload,Class<T> type){return json.readValue(payload,type);}
    private DomainFailure absent(){return new DomainFailure(NOT_FOUND,"Fictional provider resource not found.");}
    public Set<String> occupied(String flight){return Set.copyOf(jdbc.sql("select seat from fp_seat where flight_id=:flight").param("flight",flight).query(String.class).list());}
    public ProviderQuote quote(UUID owner,UUID id){return jdbc.sql("select payload::text from fp_quote where owner_id=:owner and id=:id").param("owner",owner).param("id",id).query(String.class).optional().map(s->decode(s,ProviderQuote.class)).orElseThrow(this::absent);}
    public ProviderReservation reservation(UUID owner,UUID id){return read(owner,id,false);}
    public com.travbook.flightprovider.application.port.in.FlightProvider.Page reservations(UUID owner,UUID after){
        if(after!=null)reservation(owner,after);
        var rows=jdbc.sql("select payload::text from fp_reservation where owner_id=:owner and (cast(:after as uuid) is null or ((payload->>'createdAt')::timestamptz,id)<(select (payload->>'createdAt')::timestamptz,id from fp_reservation where owner_id=:owner and id=:after)) order by (payload->>'createdAt')::timestamptz desc,id desc limit 21")
            .param("owner",owner).param("after",after).query(String.class).list().stream().map(p->decode(p,ProviderReservation.class)).toList();
        var items=rows.subList(0,Math.min(20,rows.size()));return new com.travbook.flightprovider.application.port.in.FlightProvider.Page(owner,items,rows.size()>20?items.getLast().id():null);
    }
    private ProviderReservation read(UUID owner,UUID id,boolean lock){return jdbc.sql("select payload::text from fp_reservation where owner_id=:owner and id=:id"+(lock?" for update":"")).param("owner",owner).param("id",id).query(String.class).optional().map(s->decode(s,ProviderReservation.class)).orElseThrow(this::absent);}
    private void lock(UUID owner,String operation,String key){
        jdbc.sql("select pg_advisory_xact_lock(hashtextextended(:scope,0))").param("scope",owner+"|"+operation+"|"+key).query((rs,n)->true).single();
    }
    private <T> Optional<T> replay(UUID owner,String operation,String key,String fingerprint,Class<T> type){
        var rows=jdbc.sql("select fingerprint,response::text from fp_command where owner_id=:owner and operation=:operation and command_key=:key")
            .param("owner",owner).param("operation",operation).param("key",key).query((r,n)->new String[]{r.getString(1),r.getString(2)}).list();
        if(rows.isEmpty())return Optional.empty();var old=rows.getFirst();
        if(!old[0].equals(fingerprint))throw new DomainFailure(IDEMPOTENCY_CONFLICT,"This provider operation key was used with different inputs.");
        return Optional.of(decode(old[1],type));
    }
    private void receipt(UUID owner,String operation,String key,String fingerprint,UUID aggregate,Object response){
        jdbc.sql("insert into fp_command(owner_id,operation,command_key,fingerprint,aggregate_id,response) values(:owner,:operation,:key,:fingerprint,:id,cast(:response as jsonb))")
            .param("owner",owner).param("operation",operation).param("key",key).param("fingerprint",fingerprint).param("id",aggregate).param("response",json.writeValueAsString(response)).update();
    }
    private void lockFlight(String flight){
        jdbc.sql("insert into fp_flight(id) values(:id) on conflict do nothing").param("id",flight).update();
        jdbc.sql("select id from fp_flight where id=:id for update").param("id",flight).query(String.class).single();
    }
    public ProviderQuote createQuote(UUID owner,String key,String fingerprint,Supplier<ProviderQuote> factory,UUID correlation){return Objects.requireNonNull(tx.execute(t->{
        lock(owner,"QUOTE",key);var replay=replay(owner,"QUOTE",key,fingerprint,ProviderQuote.class);if(replay.isPresent())return replay.get();
        var q=factory.get();jdbc.sql("insert into fp_quote(id,owner_id,payload) values(:id,:owner,cast(:payload as jsonb))").param("id",q.id()).param("owner",owner).param("payload",json.writeValueAsString(q)).update();
        receipt(owner,"QUOTE",key,fingerprint,q.id(),q);event(q.id(),1,"MockFlightQuoteCreated",q.createdAt(),correlation,Map.of("total",q.total()));return q;
    }));}
    public ProviderQuote promotionalQuote(UUID owner,String key,String fingerprint,Supplier<ProviderQuote> factory,String code,UUID correlation){return Objects.requireNonNull(tx.execute(t->{
        final FlightPromotion[] issued={null};
        var q=createQuote(owner,key,fingerprint,()->{var gross=factory.get();if(code.isEmpty())return gross;issued[0]=coupons.issue(gross,code);return gross.discounted(issued[0]);},correlation);
        if(issued[0]!=null)jdbc.sql("insert into fp_promotion(quote_id,owner_id,payload) values(:quote,:owner,cast(:payload as jsonb))").param("quote",q.id()).param("owner",owner).param("payload",json.writeValueAsString(issued[0])).update();
        return q;
    }));}
    public FlightPromotion promotion(UUID owner,UUID id){quote(owner,id);return jdbc.sql("select payload::text from fp_promotion where quote_id=:id and owner_id=:owner").param("id",id).param("owner",owner).query(String.class).optional().map(s->decode(s,FlightPromotion.class)).orElse(null);}
    public ProviderReservation reserve(UUID owner,UUID quoteId,String key,String fingerprint,Function<ProviderQuote,ProviderReservation> factory,UUID correlation){return Objects.requireNonNull(tx.execute(t->{
        lock(owner,"RESERVE",key);var replay=replay(owner,"RESERVE",key,fingerprint,ProviderReservation.class);if(replay.isPresent())return replay.get();
        var q=quote(owner,quoteId);lockFlight(q.flight().id());
        if(jdbc.sql("select exists(select 1 from fp_reservation where quote_id=:quote)").param("quote",quoteId).query(Boolean.class).single())throw new DomainFailure(BOOKING_CONFLICT,"This quote already has a reservation; retrieve its original operation.");
        var reservation=factory.apply(q);var occupied=occupied(q.flight().id());
        if(q.selections().stream().anyMatch(s->occupied.contains(s.seat())))throw new DomainFailure(OFFER_UNAVAILABLE,"A selected seat is no longer available. Retrieve the seat map and request a new quote.");
        var promotion=promotion(owner,quoteId);if(promotion!=null)coupons.redeem(promotion,reservation.id(),correlation);
        jdbc.sql("insert into fp_reservation(id,owner_id,quote_id,version,payload) values(:id,:owner,:quote,1,cast(:payload as jsonb))")
            .param("id",reservation.id()).param("owner",owner).param("quote",q.id()).param("payload",json.writeValueAsString(reservation)).update();
        for(var s:q.selections())jdbc.sql("insert into fp_seat(flight_id,seat,reservation_id,passenger_id) values(:flight,:seat,:id,:passenger)")
            .param("flight",q.flight().id()).param("seat",s.seat()).param("id",reservation.id()).param("passenger",s.passengerId()).update();
        receipt(owner,"RESERVE",key,fingerprint,reservation.id(),reservation);event(reservation,correlation);return reservation;
    }));}
    public ProviderReservation change(UUID owner,UUID id,String operation,String key,String fingerprint,UnaryOperator<ProviderReservation> mutation,UUID correlation){return Objects.requireNonNull(tx.execute(t->{
        lock(owner,operation,key);var replay=replay(owner,operation,key,fingerprint,ProviderReservation.class);if(replay.isPresent())return replay.get();
        var old=read(owner,id,true);var next=mutation.apply(old);
        jdbc.sql("update fp_reservation set version=:version,payload=cast(:payload as jsonb) where id=:id and version=:previous")
            .param("version",next.version()).param("payload",json.writeValueAsString(next)).param("id",id).param("previous",old.version()).update();
        if(old.reservation()!=next.reservation()) {lockFlight(old.quote().flight().id());jdbc.sql("delete from fp_seat where reservation_id=:id").param("id",id).update();}
        if(old.payment()!=next.payment() && next.payment()==ProviderReservation.PaymentState.CAPTURED)money(next,"CAPTURE",next.paymentReference());
        if(old.payment()!=next.payment() && next.payment()==ProviderReservation.PaymentState.REFUNDED)money(next,"REFUND",next.refundReference());
        receipt(owner,operation,key,fingerprint,id,next);event(next,correlation);return next;
    }));}
    private void money(ProviderReservation r,String kind,String reference){
        var effect=UUID.randomUUID();
        jdbc.sql("insert into fp_money_effect(id,reservation_id,kind,amount,reference,occurred_at) values(:id,:reservation,:kind,:amount,:reference,:at)")
            .param("id",effect).param("reservation",r.id()).param("kind",kind).param("amount",r.quote().total().decimal()).param("reference",reference).param("at",Timestamp.from(r.updatedAt())).update();
        for(int line=1;line<=2;line++)jdbc.sql("insert into fp_journal(effect_id,line,account,side,amount) values(:id,:line,:account,:side,:amount)")
            .param("id",effect).param("line",line).param("account",line==1?"SIMULATED_CASH":"SIMULATED_CUSTOMER_LIABILITY")
            .param("side",(line==1)==kind.equals("CAPTURE")?"DEBIT":"CREDIT").param("amount",r.quote().total().decimal()).update();
    }
    private void event(ProviderReservation r,UUID correlation){event(r.id(),r.version(),"MockFlightReservationUpdated",r.updatedAt(),correlation,Map.of("reservation",r.reservation(),"payment",r.payment(),"ticket",r.ticket(),"refund",r.refund(),"checkedInCount",r.checkedIn().size()));}
    private void event(UUID aggregate,long version,String type,Instant at,UUID correlation,Object payload){
        var id=UUID.randomUUID();var envelope=new LinkedHashMap<String,Object>();envelope.put("eventId",id);envelope.put("aggregateId",aggregate);envelope.put("aggregateVersion",version);envelope.put("eventType",type);envelope.put("schemaVersion",1);envelope.put("occurredAt",at);envelope.put("correlationId",correlation);envelope.put("causationId",aggregate);envelope.put("producer","flightprovider");envelope.put("mode","MOCK");envelope.put("payload",payload);
        jdbc.sql("insert into fp_event(id,aggregate_id,version,envelope) values(:id,:aggregate,:version,cast(:envelope as jsonb))")
            .param("id",id).param("aggregate",aggregate).param("version",version).param("envelope",json.writeValueAsString(envelope)).update();
    }
}
