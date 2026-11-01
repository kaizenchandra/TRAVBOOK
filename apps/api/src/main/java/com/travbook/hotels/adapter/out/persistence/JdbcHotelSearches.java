package com.travbook.hotels.adapter.out.persistence;
import com.travbook.hotels.application.port.out.HotelSearchRepository;
import com.travbook.hotels.domain.model.HotelSnapshot;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.util.*;
public final class JdbcHotelSearches implements HotelSearchRepository {
    private final JdbcClient jdbc;private final TransactionTemplate tx;private final ObjectMapper json;
    public JdbcHotelSearches(JdbcClient jdbc,TransactionTemplate tx,ObjectMapper json){this.jdbc=jdbc;this.tx=tx;this.json=json;}
    public void save(HotelSnapshot s){tx.executeWithoutResult(status->{
        jdbc.sql("insert into hotel_search(id,mode,created_at,expires_at,payload) values(:id,'MOCK',:created,:expires,cast(:payload as jsonb))")
            .param("id",s.id()).param("created",Timestamp.from(s.createdAt())).param("expires",Timestamp.from(s.expiresAt())).param("payload",json.writeValueAsString(s)).update();
        for(var o:s.offers())jdbc.sql("insert into hotel_offer_index(id,search_id,property_id,room_index) values(:id,:search,:property,:room)")
            .param("id",o.id()).param("search",s.id()).param("property",o.propertyId()).param("room",o.roomIndex()).update();
    });}
    public Optional<HotelSnapshot> find(UUID id){return jdbc.sql("select payload::text from hotel_search where id=:id").param("id",id).query(String.class).optional().map(v->json.readValue(v,HotelSnapshot.class));}
    public Optional<HotelSnapshot> findByOffer(UUID id){return jdbc.sql("select s.payload::text from hotel_search s join hotel_offer_index o on o.search_id=s.id where o.id=:id").param("id",id).query(String.class).optional().map(v->json.readValue(v,HotelSnapshot.class));}
}
