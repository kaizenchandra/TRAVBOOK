package com.travbook.booking.application.usecase;
import com.travbook.booking.application.port.in.*;
import com.travbook.booking.application.port.out.CheckoutStore;
import com.travbook.booking.domain.model.*;
import com.travbook.shared.domain.*;
import java.time.Clock;
import java.util.*;
import static com.travbook.shared.domain.DomainFailure.Code.*;
public final class CheckoutService implements BookingCheckouts {
    private final BookingIntents bookings;private final CheckoutStore store;private final Clock clock;
    public CheckoutService(BookingIntents bookings,CheckoutStore store,Clock clock){this.bookings=bookings;this.store=store;this.clock=clock;}
    public Checkout start(CustomerIdentity identity,UUID bookingId,Checkout.Scenario scenario,boolean consent,String key,UUID correlationId){
        if(!consent||scenario==null||key==null||!key.matches("[A-Za-z0-9_-]{16,100}"))throw new DomainFailure(VALIDATION_FAILED,"Explicit MOCK consent and a valid idempotency key are required.");
        var b=bookings.get(identity,bookingId).booking();String fingerprint=bookingId+":"+scenario;UUID owner=b.quote().customerId();
        var replay=store.replay(owner,key,fingerprint);if(replay.isPresent())return replay.get();
        var existing=store.find(owner,bookingId);
        if(existing.isPresent()){
            if(existing.get().checkout().scenario()!=scenario)throw new DomainFailure(BOOKING_CONFLICT,"This intent already has a checkout with a different scenario.");
            return store.create(existing.get().checkout(),key,fingerprint);
        }
        if(b.status()!=Booking.Status.AWAITING_PAYMENT)throw new DomainFailure(BOOKING_CONFLICT,"Complete offer validation before starting checkout.");
        b.quote().requireValid(clock.instant());
        var now=clock.instant();return store.create(new Checkout(UUID.randomUUID(),bookingId,b.quote(),scenario,now,now,1,Checkout.Status.RUNNING,Checkout.Step.REVALIDATE,
            Checkout.Payment.NOT_STARTED,Checkout.Reservation.NOT_REQUESTED,null,null,null,correlationId,UUID.randomUUID()),key,fingerprint);
    }
    public View get(CustomerIdentity identity,UUID bookingId){var b=bookings.get(identity,bookingId).booking();return store.find(b.quote().customerId(),bookingId).orElse(null);}
    public void requireLegacyReadable(CustomerIdentity identity,UUID bookingId){if(get(identity,bookingId)!=null)throw new DomainFailure(BOOKING_CONFLICT,"Checkout has started. Read /api/v2/bookings/"+bookingId+" for payment and supplier status.");}
}
