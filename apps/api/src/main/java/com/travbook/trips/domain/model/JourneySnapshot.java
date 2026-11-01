package com.travbook.trips.domain.model;
import com.travbook.shared.domain.DomainFailure;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
public record JourneySnapshot(UUID bookingId,UUID customerId,UUID checkoutId,long checkoutVersion,String mode,boolean confirmed,boolean captured,boolean reserved,
                              Instant confirmedAt,String paymentReference,String supplierReference,List<String> travellers,List<Leg> legs,
                              BigDecimal base,BigDecimal taxes,String currency,String baggage,String cancellationPolicy) {
    public record Leg(String origin,String destination,OffsetDateTime departure,OffsetDateTime arrival,String timezone,String airline,String flightNumber) {}
    public JourneySnapshot {travellers=List.copyOf(travellers);legs=List.copyOf(legs);}
    public void requireEligible(){
        if(!confirmed||!captured||!reserved||!"MOCK".equals(mode)||checkoutId==null||checkoutVersion<1||confirmedAt==null||paymentReference==null||supplierReference==null||
            !paymentReference.startsWith("MOCK-")||!supplierReference.startsWith("MOCK-"))throw new DomainFailure(DomainFailure.Code.BOOKING_CONFLICT,"A confirmed MOCK checkout with captured payment and supplier confirmation is required for this summary.");
    }
}
