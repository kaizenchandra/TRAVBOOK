package com.travbook.flightprovider.application.port.out;
import com.travbook.flightprovider.domain.model.*;
import java.util.*;
import java.util.function.*;

/** The adapter supplies atomicity; core callbacks decide state transitions after acquiring locks. */
public interface ProviderStore {
    Set<String> occupied(String flightId);
    ProviderQuote quote(UUID owner,UUID id);
    ProviderQuote createQuote(UUID owner,String key,String fingerprint,Supplier<ProviderQuote> factory,UUID correlation);
    ProviderQuote promotionalQuote(UUID owner,String key,String fingerprint,Supplier<ProviderQuote> factory,String coupon,UUID correlation);
    FlightPromotion promotion(UUID owner,UUID quoteId);
    ProviderReservation reservation(UUID owner,UUID id);
    com.travbook.flightprovider.application.port.in.FlightProvider.Page reservations(UUID owner,UUID after);
    ProviderReservation reserve(UUID owner,UUID quoteId,String key,String fingerprint,Function<ProviderQuote,ProviderReservation> factory,UUID correlation);
    ProviderReservation change(UUID owner,UUID id,String operation,String key,String fingerprint,UnaryOperator<ProviderReservation> mutation,UUID correlation);
}
