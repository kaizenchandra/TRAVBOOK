package com.travbook.shared.adapter.out.persistence;
import com.travbook.shared.application.port.out.SnapshotStore;
import com.travbook.shared.domain.ObservedSnapshot;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import java.sql.Timestamp;
public final class JdbcSnapshots<T extends ObservedSnapshot> implements SnapshotStore<T>{
 private final JdbcClient jdbc;private final ObjectMapper json;private final String table;private final Class<T> type;
 public JdbcSnapshots(JdbcClient jdbc,ObjectMapper json,String table,Class<T> type){if(!Set.of("bus_search","cab_search").contains(table))throw new IllegalArgumentException("Unknown snapshot owner");this.jdbc=jdbc;this.json=json;this.table=table;this.type=type;}
 public void save(T s){jdbc.sql("insert into "+table+"(id,created_at,expires_at,payload) values(:id,:created,:expires,cast(:payload as jsonb))").param("id",s.id()).param("created",Timestamp.from(s.createdAt())).param("expires",Timestamp.from(s.expiresAt())).param("payload",json.writeValueAsString(s)).update();}
 public Optional<T> find(UUID id){return jdbc.sql("select payload::text from "+table+" where id=:id").param("id",id).query(String.class).optional().map(v->json.readValue(v,type));}
}
