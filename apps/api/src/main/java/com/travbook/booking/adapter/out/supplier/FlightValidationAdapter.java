package com.travbook.booking.adapter.out.supplier;
import com.travbook.booking.application.port.out.BookingValidationPort;
import com.travbook.booking.domain.model.*;
import com.travbook.search.application.port.in.DiscoverFlights;
public final class FlightValidationAdapter implements BookingValidationPort {
    private final DiscoverFlights flights;
    public FlightValidationAdapter(DiscoverFlights flights){this.flights=flights;}
    public FlightTerms current(BookingQuote quote) {
        var o=flights.revalidateForBooking(quote.offerId());
        return new FlightTerms(o.supplier(),o.supplierReference(),o.airline(),o.legs().stream().map(l->new FlightTerms.Leg(l.origin(),l.destination(),l.departure(),l.arrival(),l.timezone(),l.durationMinutes(),l.stops(),l.flightNumber())).toList(),
            o.passengers(),o.cabin(),o.baggage(),o.cancellationPolicy(),new FlightTerms.Money(o.base().amount(),o.base().currency()),new FlightTerms.Money(o.taxes().amount(),o.taxes().currency()));
    }
}

