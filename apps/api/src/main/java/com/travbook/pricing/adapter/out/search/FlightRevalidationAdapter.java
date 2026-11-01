package com.travbook.pricing.adapter.out.search;
import com.travbook.pricing.application.port.out.RevalidateSelection;
import com.travbook.pricing.domain.model.*;
import com.travbook.search.application.port.in.DiscoverFlights;
import java.util.UUID;
public final class FlightRevalidationAdapter implements RevalidateSelection {
    private final DiscoverFlights flights;
    public FlightRevalidationAdapter(DiscoverFlights flights) { this.flights=flights; }
    public FlightSelection revalidate(UUID offerId) {
        var r=flights.reprice(offerId); var o=r.offer();
        return new FlightSelection(o.id(),o.supplier(),o.supplierReference(),o.airline(),
            o.legs().stream().map(l->new FlightSelection.Leg(l.origin(),l.destination(),l.departure(),l.arrival(),l.timezone(),l.durationMinutes(),l.stops(),l.flightNumber())).toList(),
            o.passengers(),o.cabin(),o.baggage(),o.cancellationPolicy(),new Price(o.base().amount(),o.base().currency()),new Price(o.taxes().amount(),o.taxes().currency()),
            new Price(r.previousTotal().amount(),r.previousTotal().currency()),r.validUntil());
    }
}

