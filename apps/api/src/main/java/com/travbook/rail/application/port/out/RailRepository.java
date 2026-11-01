package com.travbook.rail.application.port.out;
import com.travbook.rail.domain.model.Rail.*;
import java.util.*;
public interface RailRepository {
    List<Station> stations(String query);
    Optional<Station> station(String code);
    void save(Snapshot snapshot);
    Optional<Snapshot> find(UUID id);
    Optional<Snapshot> forJourney(UUID id);
}
