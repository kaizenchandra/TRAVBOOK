package com.travbook.booking.application.usecase;
import com.travbook.booking.application.port.in.*;
import com.travbook.booking.application.port.out.*;
import com.travbook.booking.domain.model.*;
import com.travbook.shared.domain.*;
import java.time.Clock;
import java.util.*;
import static com.travbook.shared.domain.DomainFailure.Code.*;
public final class CancellationService implements BookingCancellations {
    private final BookingIntents bookings;private final BookingCheckouts checkouts;private final CancellationStore store;private final CancellationSupplier supplier;private final Clock clock;
    public CancellationService(BookingIntents bookings,BookingCheckouts checkouts,CancellationStore store,CancellationSupplier supplier,Clock clock){this.bookings=bookings;this.checkouts=checkouts;this.store=store;this.supplier=supplier;this.clock=clock;}
    private void key(String key){if(key==null||!key.matches("[A-Za-z0-9_-]{16,100}"))throw new DomainFailure(VALIDATION_FAILED,"Use a valid 16–100 character idempotency key.");}
    private Checkout confirmed(CustomerIdentity identity,UUID bookingId){var view=checkouts.get(identity,bookingId);if(view==null||view.checkout().status()!=Checkout.Status.CONFIRMED)throw new DomainFailure(BOOKING_CONFLICT,"A confirmed MOCK checkout is required.");return view.checkout();}
    public CancellationQuote quote(CustomerIdentity identity,UUID id,String key,UUID correlation){
        key(key);var c=confirmed(identity,id);UUID owner=c.quote().customerId();var replay=store.replayQuote(owner,key,id);if(replay.isPresent())return replay.get();
        if(store.find(owner,id).isPresent())throw new DomainFailure(BOOKING_CONFLICT,"This booking already has a cancellation case.");
        var q=CancellationQuote.prepare(c,clock.instant());supplier.verifyCancellable(c);return store.saveQuote(q,key,correlation);
    }
    public Cancellation request(CustomerIdentity identity,UUID bookingId,UUID quoteId,int version,boolean consent,Cancellation.Scenario scenario,String key,UUID correlation){
        key(key);if(!consent||scenario==null||version!=1||quoteId==null)throw new DomainFailure(VALIDATION_FAILED,"Accept the exact cancellation quote and its amounts.");
        var c=confirmed(identity,bookingId);UUID owner=c.quote().customerId();String fingerprint=quoteId+":"+scenario;
        var replay=store.replay(owner,key,fingerprint);if(replay.isPresent()){if(!replay.get().quote().bookingId().equals(bookingId))throw new DomainFailure(IDEMPOTENCY_CONFLICT,"Key belongs to another booking.");return replay.get();}
        var q=store.quote(owner,quoteId).filter(x->x.bookingId().equals(bookingId)).orElseThrow(()->new DomainFailure(NOT_FOUND,"Cancellation quote not found."));
        var existing=store.find(owner,bookingId);if(existing.isPresent()){
            var previous=existing.get().cancellation();if(!previous.quote().id().equals(quoteId)||previous.scenario()!=scenario)throw new DomainFailure(BOOKING_CONFLICT,"This booking has a different cancellation request.");return store.create(previous,key,fingerprint);
        }
        q.requireValid(clock.instant());var now=clock.instant();return store.create(new Cancellation(UUID.randomUUID(),q,scenario,now,now,1,Cancellation.State.PENDING,Cancellation.RefundState.NOT_REQUESTED,Cancellation.Step.CANCEL_SUPPLIER,null,null,null,correlation,UUID.randomUUID()),key,fingerprint);
    }
    public View get(CustomerIdentity identity,UUID bookingId){var b=bookings.get(identity,bookingId).booking();return store.find(b.quote().customerId(),bookingId).orElse(null);}
    public List<View> refunds(CustomerIdentity identity,UUID id){var v=get(identity,id);if(v==null||v.cancellation().quote().refundAmount().amount().signum()==0||v.cancellation().refundStatus()==Cancellation.RefundState.NOT_REQUESTED)return List.of();return List.of(v);}
    public void requireLegacyReadable(CustomerIdentity identity,UUID id){if(get(identity,id)!=null)throw new DomainFailure(BOOKING_CONFLICT,"Cancellation exists. Read /api/v3/bookings/"+id+" or /api/v3/me/bookings for current status.");}
}
