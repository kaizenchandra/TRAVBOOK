package com.travbook.booking.domain.model;
import java.math.*;
import java.time.*;
import java.util.*;
public record FlightTerms(String supplier,String supplierReference,String airline,List<Leg> legs,int passengers,String cabin,
                          String baggage,String cancellationPolicy,Money base,Money taxes) {
    public FlightTerms { legs=List.copyOf(legs);if(legs.isEmpty()||passengers<1)throw new IllegalArgumentException("Invalid flight terms"); }
    public record Leg(String origin,String destination,OffsetDateTime departure,OffsetDateTime arrival,String timezone,int durationMinutes,int stops,String flightNumber) {}
    public record Money(BigDecimal amount,String currency) {
        public Money {if(amount==null||amount.signum()<0||!"INR".equals(currency))throw new IllegalArgumentException("Invalid money");amount=amount.setScale(2,RoundingMode.UNNECESSARY);}
    }
    public Money total(){return new Money(base.amount().add(taxes.amount()),base.currency());}
    public boolean samePrice(FlightTerms current) {return base.equals(current.base)&&taxes.equals(current.taxes);}
    public boolean sameTerms(FlightTerms current) {
        return supplier.equals(current.supplier)&&supplierReference.equals(current.supplierReference)&&airline.equals(current.airline)&&legs.equals(current.legs)
            &&passengers==current.passengers&&cabin.equals(current.cabin)&&baggage.equals(current.baggage)&&cancellationPolicy.equals(current.cancellationPolicy);
    }
}

