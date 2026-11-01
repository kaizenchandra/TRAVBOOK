package com.travbook.rail.adapter.out.persistence;
import com.travbook.rail.application.port.out.RailRepository;
import com.travbook.rail.domain.model.Rail.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.util.*;
public final class JdbcRailSearches implements RailRepository {
    private final JdbcClient jdbc;private final TransactionTemplate tx;private final ObjectMapper json;
    public JdbcRailSearches(JdbcClient jdbc,TransactionTemplate tx,ObjectMapper json){this.jdbc=jdbc;this.tx=tx;this.json=json;}
    public List<Station> stations(String q){return jdbc.sql("select code,name,city,timezone from rail_station where strpos(lower(code||' '||name||' '||city),lower(:q))>0 order by code limit 10").param("q",q).query((r,n)->new Station(r.getString(1),r.getString(2),r.getString(3),r.getString(4))).list();}
    public Optional<Station> station(String code){return jdbc.sql("select code,name,city,timezone from rail_station where code=:code").param("code",code).query((r,n)->new Station(r.getString(1),r.getString(2),r.getString(3),r.getString(4))).optional();}
    public void save(Snapshot s){tx.executeWithoutResult(status->{jdbc.sql("insert into rail_search(id,mode,created_at,expires_at,payload) values(:id,'MOCK',:created,:expires,cast(:payload as jsonb))").param("id",s.id()).param("created",Timestamp.from(s.createdAt())).param("expires",Timestamp.from(s.expiresAt())).param("payload",json.writeValueAsString(s)).update();for(var j:s.journeys())jdbc.sql("insert into rail_journey_index(id,search_id) values(:id,:search)").param("id",j.id()).param("search",s.id()).update();});}
    public Optional<Snapshot> find(UUID id){return jdbc.sql("select payload::text from rail_search where id=:id").param("id",id).query(String.class).optional().map(v->json.readValue(v,Snapshot.class));}
    public Optional<Snapshot> forJourney(UUID id){return jdbc.sql("select s.payload::text from rail_search s join rail_journey_index j on j.search_id=s.id where j.id=:id").param("id",id).query(String.class).optional().map(v->json.readValue(v,Snapshot.class));}
}
