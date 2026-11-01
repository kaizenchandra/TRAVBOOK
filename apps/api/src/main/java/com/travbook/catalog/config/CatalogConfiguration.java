package com.travbook.catalog.config;
import com.travbook.catalog.application.port.in.LookupLocations;
import com.travbook.catalog.application.port.out.LocationRepository;
import com.travbook.catalog.application.usecase.LocationService;
import com.travbook.catalog.adapter.out.persistence.JdbcLocations;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.simple.JdbcClient;
@Configuration
public class CatalogConfiguration {
    @Bean LocationRepository locationRepository(JdbcClient jdbc) { return new JdbcLocations(jdbc); }
    @Bean LookupLocations lookupLocations(LocationRepository repository) { return new LocationService(repository); }
}

