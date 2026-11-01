package com.travbook.catalog.application.port.in;
import com.travbook.catalog.domain.model.Location;
import java.util.List;
public interface LookupLocations {
    List<Location> search(String query);
    Location get(String id);
}

