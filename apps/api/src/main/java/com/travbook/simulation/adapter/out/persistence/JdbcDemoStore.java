package com.travbook.simulation.adapter.out.persistence;
import com.travbook.simulation.application.port.out.DemoStore;
import com.travbook.simulation.domain.model.DemoOperation;
import com.travbook.shared.domain.DomainFailure;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import static com.travbook.shared.domain.DomainFailure.Code.*;
public final class JdbcDemoStore implements DemoStore {
    private final JdbcClient jdbc;private final TransactionTemplate tx;private final ObjectMapper json;
    public JdbcDemoStore(JdbcClient jdbc,TransactionTemplate tx,ObjectMapper json){this.jdbc=jdbc;this.tx=tx;this.json=json;}
    private DemoOperation decode(String value){return json.readValue(value,DemoOperation.class);}
    private DomainFailure absent(){return new DomainFailure(NOT_FOUND,"Simulation operation not found.");}
    public DemoOperation create(DemoOperation o,String key){return Objects.requireNonNull(tx.execute(t->{
        int inserted=jdbc.sql("""
            insert into demo_operation(id,owner_id,command_key,fingerprint,status,attempts,version,created_at,next_at,payload)
            values(:id,:owner,:key,:fingerprint,:status,0,1,:created,:next,cast(:payload as jsonb))
            on conflict(owner_id,command_key) do nothing
            """).param("id",o.id()).param("owner",o.owner()).param("key",key).param("fingerprint",o.fingerprint())
            .param("status",o.status().name()).param("created",Timestamp.from(o.createdAt())).param("next",Timestamp.from(o.nextAttemptAt())).param("payload",json.writeValueAsString(o)).update();
        if(inserted==0){var old=jdbc.sql("select payload::text from demo_operation where owner_id=:owner and command_key=:key").param("owner",o.owner()).param("key",key).query(String.class).single();
            var prior=decode(old);if(!prior.fingerprint().equals(o.fingerprint()))throw new DomainFailure(IDEMPOTENCY_CONFLICT,"This simulation key was used for different inputs.");return prior;}
        event(o,"DemoOperationQueued");return o;
    }));}
    public DemoOperation get(UUID owner,UUID id){return jdbc.sql("select payload::text from demo_operation where owner_id=:owner and id=:id").param("owner",owner).param("id",id).query(String.class).optional().map(this::decode).orElseThrow(this::absent);}
    public Page list(UUID owner,UUID after){
        if(after!=null)get(owner,after);
        var items=jdbc.sql("""
            select payload::text from demo_operation where owner_id=:owner
              and (cast(:after as uuid) is null or (created_at,id)<(select created_at,id from demo_operation where owner_id=:owner and id=:after))
            order by created_at desc,id desc limit 21
            """).param("owner",owner).param("after",after).query(String.class).list().stream().map(this::decode).toList();
        boolean more=items.size()>20;var page=items.subList(0,Math.min(20,items.size()));return new Page(page,more?page.getLast().id():null);
    }
    public Optional<Claim> claim(Instant now){return Objects.requireNonNull(tx.execute(t->{
        var candidate=jdbc.sql("""
            select payload::text from demo_operation where status in ('QUEUED','RUNNING','RETRY') and next_at<=:now
              and (lease_until is null or lease_until<=:now) order by created_at,id for update skip locked limit 1
            """).param("now",Timestamp.from(now)).query(String.class).optional();
        if(candidate.isEmpty())return Optional.empty();var old=decode(candidate.get());var next=old.claimed(now);var token=UUID.randomUUID();
        jdbc.sql("update demo_operation set status=:status,attempts=:attempts,version=:version,payload=cast(:payload as jsonb),lease_token=:token,lease_until=:until where id=:id")
            .param("status",next.status().name()).param("attempts",next.attempts()).param("version",next.version()).param("payload",json.writeValueAsString(next))
            .param("token",token).param("until",Timestamp.from(now.plusSeconds(15))).param("id",next.id()).update();
        event(next,"DemoOperationClaimed");return Optional.of(new Claim(next,token,old.attempts()>=3));
    }));}
    public boolean complete(Claim claim,DemoOperation next){return Boolean.TRUE.equals(tx.execute(t->{
        int updated=jdbc.sql("""
            update demo_operation set status=:status,attempts=:attempts,version=:version,payload=cast(:payload as jsonb),next_at=:next,lease_token=null,lease_until=null
            where id=:id and lease_token=:token and version=:previous and lease_until>:now
            """).param("status",next.status().name()).param("attempts",next.attempts()).param("version",next.version()).param("payload",json.writeValueAsString(next))
            .param("next",Timestamp.from(next.nextAttemptAt())).param("id",next.id()).param("token",claim.token()).param("previous",claim.operation().version())
            .param("now",Timestamp.from(next.updatedAt())).update();
        if(updated==1)event(next,"DemoOperationUpdated");return updated==1;
    }));}
    private void event(DemoOperation o,String type){
        var id=UUID.randomUUID();var envelope=new LinkedHashMap<String,Object>(Map.of("eventId",id,"aggregateId",o.id(),"aggregateVersion",o.version(),"eventType",type,"schemaVersion",1,"occurredAt",o.updatedAt(),"correlationId",o.correlation(),"causationId",o.id(),"producer","simulation","payload",Map.of("kind",o.kind(),"status",o.status(),"attempts",o.attempts())));envelope.put("mode","MOCK");
        jdbc.sql("insert into demo_event(id,operation_id,version,envelope) values(:id,:operation,:version,cast(:envelope as jsonb))")
            .param("id",id).param("operation",o.id()).param("version",o.version()).param("envelope",json.writeValueAsString(envelope)).update();
    }
}
