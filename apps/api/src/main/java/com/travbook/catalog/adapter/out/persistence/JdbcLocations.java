package com.travbook.catalog.adapter.out.persistence;
import com.travbook.catalog.application.port.out.LocationRepository;
import com.travbook.catalog.domain.model.Location;
import org.springframework.jdbc.core.simple.JdbcClient;
import java.util.*;
public final class JdbcLocations implements LocationRepository {
    private final JdbcClient jdbc;
    public JdbcLocations(JdbcClient jdbc) { this.jdbc = jdbc; }
    public List<Location> search(String query) {
        return jdbc.sql("select id, name, city, country, timezone from catalog_location where strpos(lower(id || ' ' || name || ' ' || city), lower(:q)) > 0 order by id limit 10")
            .param("q",query).query(Location.class).list();
    }
    public Optional<Location> find(String id) {
        return jdbc.sql("select id, name, city, country, timezone from catalog_location where id=:id").param("id",id).query(Location.class).optional();
    }
}

