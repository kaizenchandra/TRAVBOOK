package com.travbook.search.domain.model;
import java.time.*;
import java.util.*;
public record FlightOffer(UUID id, String supplier, String supplierReference, String airline,
                          List<Leg> legs, Money base, Money taxes, int passengers, String cabin,
                          String baggage, String cancellationPolicy, Instant observedAt, Instant expiresAt) {
    public FlightOffer {
        legs = List.copyOf(legs);
        if(legs.isEmpty() || legs.size() > 2 || !expiresAt.isAfter(observedAt)) throw new IllegalArgumentException("Invalid itinerary");
        if(legs.size()==2 && (!legs.getFirst().destination().equals(legs.getLast().origin()) ||
            !legs.getFirst().origin().equals(legs.getLast().destination()) ||
            legs.getLast().departure().isBefore(legs.getFirst().arrival())))
            throw new IllegalArgumentException("Return journey must follow the outbound arrival.");
    }
    public record Leg(String origin,String destination,OffsetDateTime departure,OffsetDateTime arrival,
                      String timezone,int durationMinutes,int stops,String flightNumber) {
        public Leg {
            if(durationMinutes<1 || Duration.between(departure,arrival).toMinutes()!=durationMinutes || stops<0 || stops>1)
                throw new IllegalArgumentException("Invalid leg timing or stops.");
            ZoneId.of(timezone);
        }
    }
    public Money total() { return base.plus(taxes); }
    public int durationMinutes() { return legs.stream().mapToInt(l -> l.durationMinutes()).sum(); }
    public boolean nonstop() { return legs.stream().allMatch(l -> l.stops() == 0); }
    public FlightOffer repriced(Money newBase, Instant now) {
        Instant validity=now.plusSeconds(120);
        if(legs.getFirst().departure().toInstant().isBefore(validity)) validity=legs.getFirst().departure().toInstant();
        return new FlightOffer(id,supplier,supplierReference,airline,legs,newBase,taxes,passengers,cabin,baggage,cancellationPolicy,now,validity);
    }
}

