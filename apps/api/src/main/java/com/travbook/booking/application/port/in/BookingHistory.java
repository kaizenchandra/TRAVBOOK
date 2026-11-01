package com.travbook.booking.application.port.in;
import com.travbook.booking.domain.model.Booking;
import com.travbook.shared.domain.CustomerIdentity;
import java.time.Instant;
import java.util.*;
public interface BookingHistory {
    record Entry(UUID eventId,long version,String eventType,Booking.Status status,String failureCode,Instant occurredAt,Instant recordedAt) {}
    enum DeliveryState { CAUGHT_UP, PENDING, ATTENTION_REQUIRED }
    record Page(UUID bookingId,String mode,long bookingVersion,long deliveredVersion,DeliveryState deliveryState,List<Entry> items,Long nextAfterVersion) {}
    Page get(CustomerIdentity identity,UUID id,long afterVersion,int size);
}
