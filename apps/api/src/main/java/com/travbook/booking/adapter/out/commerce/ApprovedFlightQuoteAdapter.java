package com.travbook.booking.adapter.out.commerce;
import com.travbook.booking.application.port.out.BookingQuotePort;
import com.travbook.booking.domain.model.*;
import com.travbook.pricing.application.port.in.Quotations;
import com.travbook.search.application.port.in.DiscoverFlights;
import com.travbook.shared.domain.CustomerIdentity;
import java.util.UUID;
public final class ApprovedFlightQuoteAdapter implements BookingQuotePort {
    private final Quotations quotes;private final DiscoverFlights flights;
    public ApprovedFlightQuoteAdapter(Quotations quotes,DiscoverFlights flights){this.quotes=quotes;this.flights=flights;}
    public BookingQuote load(CustomerIdentity identity,UUID id,int version) {
        var approved=quotes.forBooking(identity,id,version);var q=approved.quote();var s=q.selection();var criteria=flights.criteriaForOffer(s.offerId());
        var terms=new FlightTerms(s.supplier(),s.supplierReference(),s.airline(),s.legs().stream().map(l->new FlightTerms.Leg(l.origin(),l.destination(),l.departure(),l.arrival(),l.timezone(),l.durationMinutes(),l.stops(),l.flightNumber())).toList(),
            s.passengers(),s.cabin(),s.baggage(),s.cancellationPolicy(),new FlightTerms.Money(s.base().amount(),s.base().currency()),new FlightTerms.Money(s.taxes().amount(),s.taxes().currency()));
        return new BookingQuote(id,q.version(),q.customerId(),approved.acceptance().id(),s.offerId(),q.mode(),q.expiresAt(),criteria.adults(),criteria.children(),criteria.infants(),terms);
    }
}

