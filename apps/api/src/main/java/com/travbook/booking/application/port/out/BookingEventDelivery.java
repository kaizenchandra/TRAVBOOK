package com.travbook.booking.application.port.out;
import java.util.UUID;
public interface BookingEventDelivery {
    /** Return only after durable consumer commit. Safe to redeliver an identical message. */
    void deliver(UUID eventId,UUID aggregateId,long version,String envelope);
}
