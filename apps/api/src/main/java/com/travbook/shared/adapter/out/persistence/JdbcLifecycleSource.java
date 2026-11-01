package com.travbook.shared.adapter.out.persistence;
import com.travbook.shared.application.port.LifecycleSource;
import com.travbook.shared.domain.LifecycleSignal;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
/** Reusable infrastructure; each producer config supplies its own constant query over its own tables. */
public final class JdbcLifecycleSource implements LifecycleSource {
    private final String name,query,prefix;private final JdbcClient jdbc;private final ObjectMapper json;
    public JdbcLifecycleSource(String name,String query,String prefix,JdbcClient jdbc,ObjectMapper json){this.name=name;this.query=query;this.prefix=prefix;this.jdbc=jdbc;this.json=json;}
    public String name(){return name;}
    public List<LifecycleSignal> pending(){return jdbc.sql("select src.* from ("+query+") src where not exists(select 1 from lifecycle_forwarded f where f.source=:source and f.event_id=src.event_id) and not exists(select 1 from lifecycle_rejected f where f.source=:source and f.event_id=src.event_id) order by src.aggregate_id,lpad(coalesce(src.envelope->>'aggregateVersion','0'),20,'0'),src.event_id limit 20")
        .param("source",name).query((r,n)->{UUID event=r.getObject("event_id",UUID.class);try{var e=json.readTree(r.getString("envelope"));if(!"MOCK".equals(e.path("mode").asString()))throw new IllegalArgumentException("Unsupported event mode");var p=e.get("payload");var states=new TreeMap<String,String>();
            for(String field:List.of("status","payment","reservation","refund","ticket")){var value=p.get(field);if(value==null)value=p.get(field+"Status");if(value!=null&&value.isTextual()&&value.asString().matches("[A-Z0-9_]{1,64}"))states.put(field,value.asString());}
            if(states.isEmpty())states.put("status","UPDATED");var at=e.get("occurredAt");if(at==null)at=e.get("timestamp");UUID booking=r.getObject("booking_id",UUID.class);
            return new LifecycleSignal(event,r.getObject("owner_id",UUID.class),r.getObject("aggregate_id",UUID.class),booking,e.get("aggregateVersion").asLong(),name,states,Instant.parse(at.asString()),prefix+booking);
        }catch(RuntimeException invalid){jdbc.sql("insert into lifecycle_rejected(source,event_id,reason) values(:source,:event,'INVALID_ENVELOPE') on conflict do nothing").param("source",name).param("event",event).update();return null;}}).list().stream().filter(Objects::nonNull).toList();}
    public void acknowledge(UUID id){jdbc.sql("insert into lifecycle_forwarded(source,event_id) values(:source,:id) on conflict do nothing").param("source",name).param("id",id).update();}
}
