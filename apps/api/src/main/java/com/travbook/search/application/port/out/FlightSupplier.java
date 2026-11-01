package com.travbook.search.application.port.out;
import com.travbook.search.domain.model.*;
import java.time.Instant;
import java.util.List;
public interface FlightSupplier {
    String id();
    List<FlightOffer> search(FlightQuery query, Instant now);
    FlightOffer reprice(FlightOffer offer, Instant now);
}

