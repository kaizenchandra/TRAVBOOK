package com.travbook.booking.domain.model;
import java.time.*;
import java.util.*;
public record Booking(UUID id,BookingQuote quote,List<Traveller> travellers,String fingerprint,Instant createdAt,Instant updatedAt,
                      long version,Status status,String failureCode,UUID correlationId,UUID commandId) {
    public enum Status { CREATED, VALIDATING, AWAITING_PAYMENT, REQUIRES_REQUOTE, INVENTORY_UNAVAILABLE, EXPIRED, MANUAL_REVIEW }
    public enum Decision { VALIDATED, PRICE_CHANGED, TERMS_CHANGED, UNAVAILABLE, EXPIRED, RETRY, REVIEW }
    public Booking {travellers=List.copyOf(travellers);if(version<1||!"MOCK".equals(quote.mode()))throw new IllegalArgumentException("Invalid booking intent");}
    public Booking start(Instant now) {
        if(status==Status.VALIDATING)return this;
        if(status!=Status.CREATED)throw new IllegalStateException("Cannot restart terminal validation");
        return transition(Status.VALIDATING,null,now);
    }
    public Decision evaluate(FlightTerms current,Instant now) {
        if(!quote.expiresAt().isAfter(now))return Decision.EXPIRED;
        if(!quote.terms().samePrice(current))return Decision.PRICE_CHANGED;
        if(!quote.terms().sameTerms(current))return Decision.TERMS_CHANGED;
        return Decision.VALIDATED;
    }
    public Booking complete(Decision decision,Instant now) {
        if(status!=Status.VALIDATING)throw new IllegalStateException("Only a validating booking may complete its validation");
        return switch(decision) {
            case VALIDATED -> transition(Status.AWAITING_PAYMENT,null,now);
            case PRICE_CHANGED -> transition(Status.REQUIRES_REQUOTE,"PRICE_CHANGED",now);
            case TERMS_CHANGED -> transition(Status.REQUIRES_REQUOTE,"TERMS_CHANGED",now);
            case UNAVAILABLE -> transition(Status.INVENTORY_UNAVAILABLE,"OFFER_UNAVAILABLE",now);
            case EXPIRED -> transition(Status.EXPIRED,"QUOTE_EXPIRED",now);
            case REVIEW -> transition(Status.MANUAL_REVIEW,"VALIDATION_UNRESOLVED",now);
            case RETRY -> transition(Status.VALIDATING,"SUPPLIER_RETRY_PENDING",now);
        };
    }
    private Booking transition(Status next,String failure,Instant now) {
        return new Booking(id,quote,travellers,fingerprint,createdAt,now,version+1,next,failure,correlationId,commandId);
    }
    public Duration retryDelay(int attempt) {
        if(attempt<1)throw new IllegalArgumentException("Attempt must be positive");
        // Deterministic per-intent jitter keeps MOCK tests reproducible and spreads competing retries.
        return Duration.ofMillis((1L<<Math.min(attempt-1,3))*1000+Math.floorMod(id.hashCode()+31*attempt,251));
    }
}

