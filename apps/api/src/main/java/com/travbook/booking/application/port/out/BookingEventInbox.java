package com.travbook.booking.application.port.out;
import com.travbook.booking.application.port.in.BookingHistory;
import com.travbook.booking.domain.event.BookingLifecycleEvent;
import java.util.UUID;
public interface BookingEventInbox {
    void record(BookingLifecycleEvent event);
    BookingHistory.Page ownedHistory(UUID owner,UUID bookingId,long afterVersion,int size);
}
