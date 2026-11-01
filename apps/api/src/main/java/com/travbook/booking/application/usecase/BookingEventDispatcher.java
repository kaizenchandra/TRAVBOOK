package com.travbook.booking.application.usecase;
import com.travbook.booking.application.port.in.DispatchBookingEvents;
import com.travbook.booking.application.port.out.*;
import com.travbook.booking.domain.event.EventRejected;
import java.time.Duration;
public final class BookingEventDispatcher implements DispatchBookingEvents {
    private final BookingEventOutbox outbox;private final BookingEventDelivery delivery;private final Duration lease;
    public BookingEventDispatcher(BookingEventOutbox outbox,BookingEventDelivery delivery,Duration lease){
        if(lease.toSeconds()<1||lease.toSeconds()>60)throw new IllegalArgumentException("Event lease must be 1–60 seconds");
        this.outbox=outbox;this.delivery=delivery;this.lease=lease;
    }
    public Outcome runNext() {
        var optional=outbox.claim(lease);if(optional.isEmpty())return Outcome.IDLE;
        var c=optional.get();if(c.exhausted())return Outcome.DEAD;
        try{delivery.deliver(c.eventId(),c.aggregateId(),c.version(),c.envelope());}
        catch(EventRejected rejected){return outbox.fail(c,rejected.code().name(),true)?Outcome.DEAD:Outcome.STALE;}
        catch(RuntimeException unavailable){return outbox.fail(c,"DELIVERY_UNAVAILABLE",false)?(c.attempt()>=BookingEventOutbox.MAX_ATTEMPTS?Outcome.DEAD:Outcome.RETRY):Outcome.STALE;}
        // Ack failure must leave a recoverable lease; never roll back the consumer's independent commit.
        return outbox.acknowledge(c)?Outcome.DELIVERED:Outcome.STALE;
    }
    public Health health(){var b=outbox.backlog();return new Health(b.pending(),b.quarantined(),b.oldestSeconds());}
}
