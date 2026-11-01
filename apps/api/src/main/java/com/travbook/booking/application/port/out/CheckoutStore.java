package com.travbook.booking.application.port.out;
import com.travbook.booking.domain.model.Checkout;
import com.travbook.booking.application.port.in.BookingCheckouts;
import java.time.Duration;
import java.util.*;
public interface CheckoutStore {
    record Claim(Checkout checkout,UUID token,int attempt) {}
    Optional<Checkout> replay(UUID owner,String key,String fingerprint);
    Checkout create(Checkout checkout,String key,String fingerprint);
    Optional<BookingCheckouts.View> find(UUID owner,UUID bookingId);
    Optional<Claim> claim(Duration lease);
    boolean finish(Claim claim,Checkout next);
}
