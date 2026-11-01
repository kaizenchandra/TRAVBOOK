package com.travbook.booking.domain.model;
import com.travbook.shared.domain.*;
import java.time.*;
import java.util.*;
public record CancellationQuote(UUID id,UUID bookingId,Checkout checkout,Instant createdAt,Instant expiresAt,FlightTerms.Money refundAmount,FlightTerms.Money retainedAmount) {
    public CancellationQuote {
        var total=checkout.quote().terms().total();
        if(id==null||!bookingId.equals(checkout.bookingId())||checkout.status()!=Checkout.Status.CONFIRMED||!refundAmount.currency().equals(total.currency())||!retainedAmount.currency().equals(total.currency())||refundAmount.amount().add(retainedAmount.amount()).compareTo(total.amount())!=0)throw new IllegalArgumentException("Invalid cancellation entitlement");
    }
    public static CancellationQuote prepare(Checkout c,Instant now){
        Instant departure=c.quote().terms().legs().getFirst().departure().toInstant();if(!departure.isAfter(now))throw new DomainFailure(DomainFailure.Code.BOOKING_CONFLICT,"Cancellation is unavailable after first departure.");
        var policy=MockCancellationPolicy.parse(c.quote().terms().cancellationPolicy());var total=c.quote().terms().total();var refund=policy.refund(total.amount());
        return new CancellationQuote(UUID.randomUUID(),c.bookingId(),c,now,now.plusSeconds(300).isBefore(departure)?now.plusSeconds(300):departure,new FlightTerms.Money(refund,total.currency()),new FlightTerms.Money(total.amount().subtract(refund),total.currency()));
    }
    public UUID owner(){return checkout.quote().customerId();}
    public void requireValid(Instant now){if(!expiresAt.isAfter(now))throw new DomainFailure(DomainFailure.Code.QUOTE_EXPIRED,"The cancellation quote expired. Request a fresh quote.");}
}
