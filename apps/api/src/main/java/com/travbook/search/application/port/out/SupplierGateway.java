package com.travbook.search.application.port.out;
import com.travbook.search.domain.model.*;
import java.time.Instant;
import java.util.List;
public interface SupplierGateway {
    record Results(List<FlightOffer> offers, List<SearchSnapshot.Coverage> coverage) {}
    Results search(FlightQuery query,Instant now);
    FlightOffer reprice(FlightOffer offer,Instant now);
}

