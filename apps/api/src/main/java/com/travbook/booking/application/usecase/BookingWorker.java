package com.travbook.booking.application.usecase;
import com.travbook.booking.application.port.in.RunBookingValidation;
import com.travbook.booking.application.port.out.*;
import com.travbook.booking.domain.model.Booking;
import com.travbook.shared.domain.DomainFailure;
import java.time.*;
import static com.travbook.booking.domain.model.Booking.Decision.*;
public final class BookingWorker implements RunBookingValidation {
    private final BookingStore store;private final BookingValidationPort validation;private final Clock clock;private final Duration lease;private final int maxAttempts;
    public BookingWorker(BookingStore store,BookingValidationPort validation,Clock clock,Duration lease,int maxAttempts) {
        if(lease.compareTo(Duration.ofSeconds(3))<0||maxAttempts<1||maxAttempts>8)throw new IllegalArgumentException("Invalid worker limits");
        this.store=store;this.validation=validation;this.clock=clock;this.lease=lease;this.maxAttempts=maxAttempts;
    }
    public boolean runNext() {
        var next=store.claim(lease);if(next.isEmpty())return false;
        var claim=next.get();
        if(!claim.booking().quote().expiresAt().isAfter(clock.instant())){store.finish(claim,EXPIRED);return true;}
        int attempt=store.beginRead(claim,maxAttempts);
        if(attempt==0)return true;if(attempt<0){store.finish(claim,REVIEW);return true;}
        Booking.Decision decision;
        try {decision=claim.booking().evaluate(validation.current(claim.booking().quote()),clock.instant());}
        catch(DomainFailure failure) {
            decision=switch(failure.code()) {
                case OFFER_UNAVAILABLE -> UNAVAILABLE;
                case OFFER_EXPIRED,QUOTE_EXPIRED -> EXPIRED;
                case SUPPLIERS_UNAVAILABLE -> attempt<maxAttempts?RETRY:REVIEW;
                default -> REVIEW;
            };
        } catch(RuntimeException unavailable) {decision=attempt<maxAttempts?RETRY:REVIEW;}
        // A failed local completion deliberately leaves the leased job recoverable.
        store.finish(claim,decision);return true;
    }
}

