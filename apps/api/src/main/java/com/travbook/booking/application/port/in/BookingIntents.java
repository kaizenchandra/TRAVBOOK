package com.travbook.booking.application.port.in;
import com.travbook.booking.domain.model.*;
import com.travbook.shared.domain.CustomerIdentity;
import java.util.*;
public interface BookingIntents {
    record Requirements(BookingQuote quote,UUID existingBookingId) {}
    record WorkflowView(int definitionVersion,String status,int attempts,java.time.Instant nextAttemptAt,boolean terminal) {}
    record Details(Booking booking,WorkflowView workflow,String reservationStatus,String inventoryStatus) {}
    record Page(List<Booking> items,UUID nextCursor) {}
    Requirements requirements(CustomerIdentity identity,UUID quoteId,int version);
    Booking create(CustomerIdentity identity,UUID quoteId,int quoteVersion,List<Traveller> travellers,String key,UUID correlationId);
    Details get(CustomerIdentity identity,UUID id);
    Page list(CustomerIdentity identity,UUID after,int size);
}

