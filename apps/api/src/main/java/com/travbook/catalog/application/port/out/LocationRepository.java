package com.travbook.catalog.application.port.out;
import com.travbook.catalog.domain.model.Location;
import java.util.List;
import java.util.Optional;
public interface LocationRepository {
    List<Location> search(String query);
    Optional<Location> find(String id);
}

