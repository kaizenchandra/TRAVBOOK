package com.travbook.search.adapter.out.persistence;
import com.travbook.search.application.port.out.SearchRepository;
import com.travbook.search.domain.model.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
public final class JdbcSearchRepository implements SearchRepository {
    private final JdbcClient jdbc;
    private final TransactionTemplate transactions;
    private final ObjectMapper json;
    public JdbcSearchRepository(JdbcClient jdbc,TransactionTemplate transactions,ObjectMapper json) { this.jdbc=jdbc; this.transactions=transactions; this.json=json; }
    public void save(SearchSnapshot s) {
        transactions.executeWithoutResult(status -> {
            jdbc.sql("insert into search_snapshot(id,mode,created_at,expires_at,payload) values (:id,'MOCK',:created,:expires,cast(:payload as jsonb))")
                .param("id",s.id()).param("created",java.sql.Timestamp.from(s.createdAt())).param("expires",java.sql.Timestamp.from(s.expiresAt()))
                .param("payload",json.writeValueAsString(s)).update();
            for(var o:s.offers()) jdbc.sql("insert into search_offer(id,search_id,total,currency,payload) values(:id,:search,:total,:currency,cast(:payload as jsonb))")
                .param("id",o.id()).param("search",s.id()).param("total",o.total().amount()).param("currency",o.total().currency())
                .param("payload",json.writeValueAsString(o)).update();
        });
    }
    public Optional<SearchSnapshot> find(UUID id) {
        return jdbc.sql("select payload::text from search_snapshot where id=:id").param("id",id).query(String.class).optional()
            .map(value -> json.readValue(value,SearchSnapshot.class));
    }
    public Optional<FlightOffer> findOffer(UUID id) {
        return jdbc.sql("select payload::text from search_offer where id=:id").param("id",id).query(String.class).optional()
            .map(value -> json.readValue(value,FlightOffer.class));
    }
    public Optional<FlightQuery> criteriaForOffer(UUID id) {
        return jdbc.sql("select s.payload::text from search_snapshot s join search_offer o on o.search_id=s.id where o.id=:id")
            .param("id",id).query(String.class).optional().map(value->json.readValue(value,SearchSnapshot.class).criteria());
    }
}

