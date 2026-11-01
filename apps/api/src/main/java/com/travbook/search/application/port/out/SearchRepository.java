package com.travbook.search.application.port.out;
import com.travbook.search.domain.model.*;
import java.util.*;
public interface SearchRepository {
    void save(SearchSnapshot snapshot);
    Optional<SearchSnapshot> find(UUID id);
    Optional<FlightOffer> findOffer(UUID id);
    Optional<FlightQuery> criteriaForOffer(UUID id);
}

