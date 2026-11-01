package com.travbook.search.application.port.in;
import com.travbook.search.domain.model.*;
import java.time.Instant;
import java.util.*;
public interface DiscoverFlights {
    enum Sort { PRICE, DURATION, DEPARTURE }
    record Page(SearchSnapshot snapshot,List<FlightOffer> offers,int total,int page,int size) {}
    record Reprice(FlightOffer offer, Money previousTotal, boolean changed, Instant checkedAt, Instant validUntil) {}
    Page search(FlightQuery query);
    Page page(UUID id,int page,int size,boolean nonstop,Sort sort,String airline);
    FlightOffer offer(UUID id);
    Reprice reprice(UUID id);
    FlightQuery criteriaForOffer(UUID id);
    FlightOffer revalidateForBooking(UUID id);
}

