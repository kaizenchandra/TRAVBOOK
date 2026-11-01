package com.travbook.booking.domain.model;
import com.travbook.shared.domain.DomainFailure;
import java.time.*;
import java.util.*;
import static com.travbook.shared.domain.DomainFailure.Code.*;
public record BookingQuote(UUID quoteId,int version,UUID customerId,UUID acceptanceId,UUID offerId,String mode,Instant expiresAt,
                           int adults,int children,int infants,FlightTerms terms) {
    public BookingQuote {
        if(quoteId==null||customerId==null||acceptanceId==null||offerId==null||version<1||!"MOCK".equals(mode)||
            adults<1||adults+children>9||children<0||infants<0||infants>adults||adults+children+infants!=terms.passengers())
            throw new IllegalArgumentException("Invalid approved booking quote");
    }
    public void requireValid(Instant now) {
        if(!expiresAt.isAfter(now)||!terms.legs().getFirst().departure().toInstant().isAfter(now))
            throw new DomainFailure(QUOTE_EXPIRED,"This quotation expired. Request and accept a new quote.");
    }
    public void validateTravellers(List<Traveller> travellers) {
        if(travellers==null||travellers.size()!=terms.passengers()||new HashSet<>(travellers).size()!=travellers.size())
            throw new DomainFailure(VALIDATION_FAILED,"Provide one distinct traveller for every quoted passenger.");
        for(var leg:terms.legs()) {
            LocalDate day=leg.departure().atZoneSameInstant(ZoneId.of(leg.timezone())).toLocalDate();
            long a=travellers.stream().filter(t->t.categoryAt(day)==Traveller.Category.ADULT).count();
            long c=travellers.stream().filter(t->t.categoryAt(day)==Traveller.Category.CHILD).count();
            long i=travellers.stream().filter(t->t.categoryAt(day)==Traveller.Category.INFANT).count();
            if(a!=adults||c!=children||i!=infants)throw new DomainFailure(VALIDATION_FAILED,"Traveller ages must match the quoted adult, child and infant counts on every departure.");
        }
    }
}

