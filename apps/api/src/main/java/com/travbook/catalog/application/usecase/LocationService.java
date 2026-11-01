package com.travbook.catalog.application.usecase;
import com.travbook.catalog.application.port.in.LookupLocations;
import com.travbook.catalog.application.port.out.LocationRepository;
import com.travbook.catalog.domain.model.Location;
import com.travbook.shared.domain.DomainFailure;
import java.util.List;
import static com.travbook.shared.domain.DomainFailure.Code.*;
public final class LocationService implements LookupLocations {
    private final LocationRepository repository;
    public LocationService(LocationRepository repository) { this.repository = repository; }
    public List<Location> search(String query) {
        if(query == null || query.length() > 80) throw new DomainFailure(VALIDATION_FAILED,"Location query must be at most 80 characters.");
        return repository.search(query.strip());
    }
    public Location get(String id) {
        if(id == null || !id.matches("[A-Z]{3}")) throw new DomainFailure(VALIDATION_FAILED,"Use a canonical airport code.");
        return repository.find(id).orElseThrow(() -> new DomainFailure(NOT_FOUND,"Airport not found."));
    }
}

