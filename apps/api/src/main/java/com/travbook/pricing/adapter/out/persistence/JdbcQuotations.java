package com.travbook.pricing.adapter.out.persistence;
import com.travbook.pricing.application.port.out.QuotationRepository;
import com.travbook.pricing.domain.model.*;
import com.travbook.shared.domain.DomainFailure;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.*;
import static com.travbook.shared.domain.DomainFailure.Code.*;
public final class JdbcQuotations implements QuotationRepository {
    private final JdbcClient jdbc; private final TransactionTemplate tx; private final ObjectMapper json; private final Clock clock;
    public JdbcQuotations(JdbcClient jdbc,TransactionTemplate tx,ObjectMapper json,Clock clock) { this.jdbc=jdbc; this.tx=tx; this.json=json; this.clock=clock; }
    private record Command(UUID commandId,String fingerprint,UUID resourceId) {}
    private Optional<Command> command(UUID owner,String operation,String key) {
        return jdbc.sql("select command_id,fingerprint,resource_id from pricing_command where customer_id=:owner and operation=:op and command_key=:key")
            .param("owner",owner).param("op",operation).param("key",key)
            .query((rs,n)->new Command(rs.getObject(1,UUID.class),rs.getString(2),rs.getObject(3,UUID.class))).optional();
    }
    private void same(Command command,String fingerprint) {
        if(!command.fingerprint().equals(fingerprint)) throw new DomainFailure(IDEMPOTENCY_CONFLICT,"This idempotency key was already used for a different request.");
    }
    private boolean claim(UUID commandId,UUID owner,String operation,String key,String fingerprint,UUID resourceId) {
        return jdbc.sql("insert into pricing_command(command_id,customer_id,operation,command_key,fingerprint,resource_id) values(:id,:owner,:op,:key,:hash,:result) on conflict(customer_id,operation,command_key) do nothing")
            .param("id",commandId).param("owner",owner).param("op",operation).param("key",key).param("hash",fingerprint).param("result",resourceId).update()==1;
    }
    public Optional<Quotation> replayCreation(UUID owner,String key,String fingerprint) {
        return command(owner,"CREATE_QUOTE",key).map(c->{same(c,fingerprint);return findOwned(owner,c.resourceId()).orElseThrow();});
    }
    public Quotation create(Quotation q,String key,String fingerprint,UUID correlationId) {
        return tx.execute(status->{
            UUID commandId=UUID.randomUUID();
            if(!claim(commandId,q.customerId(),"CREATE_QUOTE",key,fingerprint,q.id()))
                return replayCreation(q.customerId(),key,fingerprint).orElseThrow();
            q.requireValid(clock.instant());
            jdbc.sql("insert into pricing_quotation(id,customer_id,offer_id,version,mode,total,currency,created_at,expires_at,payload) values(:id,:owner,:offer,1,'MOCK',:total,'INR',:created,:expires,cast(:payload as jsonb))")
                .param("id",q.id()).param("owner",q.customerId()).param("offer",q.selection().offerId()).param("total",q.selection().total().amount())
                .param("created",Timestamp.from(q.createdAt())).param("expires",Timestamp.from(q.expiresAt())).param("payload",json.writeValueAsString(q)).update();
            event(q.id(),"QuotationCreated",commandId,correlationId,Map.of("quoteId",q.id().toString(),"quoteVersion",q.version()));
            return q;
        });
    }
    public Optional<Quotation> findOwned(UUID owner,UUID id) {
        return jdbc.sql("select payload::text from pricing_quotation where id=:id and customer_id=:owner").param("id",id).param("owner",owner)
            .query(String.class).optional().map(s->json.readValue(s,Quotation.class));
    }
    public Optional<QuoteAcceptance> acceptance(UUID owner,UUID quoteId) {
        return jdbc.sql("select a.payload::text from pricing_acceptance a join pricing_quotation q on q.id=a.quote_id where q.id=:quote and q.customer_id=:owner")
            .param("quote",quoteId).param("owner",owner).query(String.class).optional().map(s->json.readValue(s,QuoteAcceptance.class));
    }
    public QuoteAcceptance accept(UUID owner,UUID quoteId,int version,String key,String fingerprint,UUID correlationId) {
        return tx.execute(status->{
            UUID commandId=UUID.randomUUID();
            if(!claim(commandId,owner,"ACCEPT_QUOTE",key,fingerprint,quoteId)) {
                var replay=command(owner,"ACCEPT_QUOTE",key).orElseThrow(); same(replay,fingerprint);
                return acceptance(owner,replay.resourceId()).orElseThrow();
            }
            // Lock the quote to serialize different keys and validate expiry at the commit boundary.
            jdbc.sql("select id from pricing_quotation where id=:id and customer_id=:owner for update").param("id",quoteId).param("owner",owner)
                .query(UUID.class).optional().orElseThrow(()->new DomainFailure(NOT_FOUND,"Quotation not found."));
            var q=findOwned(owner,quoteId).orElseThrow();
            q.requireAcceptance(version,clock.instant());
            var existing=acceptance(owner,quoteId);
            if(existing.isPresent())return existing.get();
            var receipt=new QuoteAcceptance(UUID.randomUUID(),quoteId,version,clock.instant(),"MOCK");
            jdbc.sql("insert into pricing_acceptance(id,quote_id,quote_version,accepted_at,payload) values(:id,:quote,:version,:now,cast(:payload as jsonb))")
                .param("id",receipt.id()).param("quote",quoteId).param("version",version).param("now",Timestamp.from(receipt.acceptedAt()))
                .param("payload",json.writeValueAsString(receipt)).update();
            event(receipt.id(),"QuotationAccepted",commandId,correlationId,Map.of("quoteId",quoteId.toString(),"quoteVersion",version));
            return receipt;
        });
    }
    private void event(UUID aggregate,String type,UUID command,UUID correlation,Map<String,Object> payload) {
        UUID eventId=UUID.randomUUID();
        var envelope=new LinkedHashMap<String,Object>(Map.of("eventId",eventId,"eventType",type,"schemaVersion",1,"aggregateId",aggregate,"aggregateVersion",1,
            "occurredAt",clock.instant().toString(),"correlationId",correlation,"causationId",command,"producer","pricing","payload",payload));
        envelope.put("mode","MOCK");
        jdbc.sql("insert into pricing_outbox(id,aggregate_id,event_type,mode,envelope) values(:id,:aggregate,:type,'MOCK',cast(:envelope as jsonb))")
            .param("id",eventId).param("aggregate",aggregate).param("type",type).param("envelope",json.writeValueAsString(envelope)).update();
    }
}

