package com.travbook.pricing.domain.model;
import java.time.*;
import java.util.*;
public record FlightSelection(UUID offerId,String supplier,String supplierReference,String airline,List<Leg> legs,
        int passengers,String cabin,String baggage,String cancellationPolicy,Price base,Price taxes,Price previousTotal,Instant validUntil) {
    public FlightSelection {
        legs=List.copyOf(legs);
        if(legs.isEmpty() || passengers<1 || supplier==null || supplierReference==null) throw new IllegalArgumentException("Invalid priced selection");
    }
    public record Leg(String origin,String destination,OffsetDateTime departure,OffsetDateTime arrival,String timezone,int durationMinutes,int stops,String flightNumber) {}
    public Price total() { return base.plus(taxes); }
}

