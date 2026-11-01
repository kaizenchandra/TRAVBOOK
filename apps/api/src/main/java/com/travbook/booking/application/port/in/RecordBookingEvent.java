package com.travbook.booking.application.port.in;
import com.travbook.booking.domain.event.BookingLifecycleEvent;
public interface RecordBookingEvent { void accept(BookingLifecycleEvent event); }
