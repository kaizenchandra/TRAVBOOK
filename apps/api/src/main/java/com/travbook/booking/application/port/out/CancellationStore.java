package com.travbook.booking.application.port.out;
import com.travbook.booking.domain.model.*;
import com.travbook.booking.application.port.in.BookingCancellations;
import java.time.Duration;
import java.util.*;
public interface CancellationStore {
    Optional<CancellationQuote> replayQuote(UUID owner,String key,UUID bookingId);
    CancellationQuote saveQuote(CancellationQuote quote,String key,UUID correlationId);
    Optional<CancellationQuote> quote(UUID owner,UUID id);
    Optional<Cancellation> replay(UUID owner,String key,String fingerprint);
    Optional<BookingCancellations.View> find(UUID owner,UUID bookingId);
    Cancellation create(Cancellation cancellation,String key,String fingerprint);
    record Claim(Cancellation cancellation,UUID token,int attempt) {}
    Optional<Claim> claim(Duration lease);
    boolean finish(Claim claim,Cancellation next);
}
