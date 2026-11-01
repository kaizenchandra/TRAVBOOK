package com.travbook.booking.application.port.out;
import com.travbook.booking.application.port.in.BookingIntents;
import com.travbook.booking.domain.model.*;
import java.util.*;
import java.time.Duration;
public interface BookingStore {
    record Claim(Booking booking,UUID token) {}
    Optional<Booking> replay(UUID owner,String key,String fingerprint);
    Optional<Booking> forQuote(UUID owner,UUID quoteId);
    Booking create(Booking booking,String key);
    Optional<BookingIntents.Details> findOwned(UUID owner,UUID id);
    BookingIntents.Page listOwned(UUID owner,UUID after,int size);
    Optional<Claim> claim(Duration lease);
    /** >0: persisted attempt number; 0: stale/already started; -1: exhausted. */
    int beginRead(Claim claim,int maxAttempts);
    boolean finish(Claim claim,Booking.Decision decision);
}

