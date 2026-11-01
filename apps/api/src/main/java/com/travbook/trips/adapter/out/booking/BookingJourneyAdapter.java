package com.travbook.trips.adapter.out.booking;
import com.travbook.trips.application.port.out.ConfirmedJourneyPort;
import com.travbook.trips.domain.model.JourneySnapshot;
import com.travbook.booking.application.port.in.*;
import com.travbook.booking.domain.model.Checkout;
import com.travbook.shared.domain.CustomerIdentity;
import java.util.UUID;
public final class BookingJourneyAdapter implements ConfirmedJourneyPort {
    private final BookingIntents bookings;private final BookingCheckouts checkouts;private final BookingCancellations cancellations;
    public BookingJourneyAdapter(BookingIntents bookings,BookingCheckouts checkouts,BookingCancellations cancellations){this.bookings=bookings;this.checkouts=checkouts;this.cancellations=cancellations;}
    public JourneySnapshot load(CustomerIdentity identity,UUID id){
        var b=bookings.get(identity,id).booking();var view=checkouts.get(identity,id);var c=view==null?null:view.checkout();var t=b.quote().terms();
        var aftercare=cancellations.get(identity,id);boolean blocked=aftercare!=null&&aftercare.cancellation().blocksDocuments();
        return new JourneySnapshot(id,b.quote().customerId(),c==null?null:c.id(),c==null?0:c.version(),b.quote().mode(),!blocked&&c!=null&&c.status()==Checkout.Status.CONFIRMED,c!=null&&c.payment()==Checkout.Payment.CAPTURED,c!=null&&c.reservation()==Checkout.Reservation.CONFIRMED,
            c==null?null:c.updatedAt(),c==null?null:c.paymentReference(),c==null?null:c.supplierReference(),b.travellers().stream().map(x->x.givenName()+" "+x.familyName()).toList(),
            t.legs().stream().map(l->new JourneySnapshot.Leg(l.origin(),l.destination(),l.departure(),l.arrival(),l.timezone(),t.airline(),l.flightNumber())).toList(),t.base().amount(),t.taxes().amount(),t.base().currency(),t.baggage(),t.cancellationPolicy());
    }
}
