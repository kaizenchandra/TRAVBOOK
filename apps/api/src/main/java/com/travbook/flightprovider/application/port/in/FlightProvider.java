package com.travbook.flightprovider.application.port.in;
import com.travbook.flightprovider.domain.model.*;
import com.travbook.shared.domain.*;
import java.time.LocalDate;
import java.util.*;

public interface FlightProvider {
    List<FlightFixture.Flight> flights(LocalDate date);
    FlightFixture.SeatMap seats(String flightId);
    FlightFixture.Catalog ancillaries(String flightId);
    ProviderQuote quote(CustomerIdentity identity,String flightId,List<FlightFixture.Selection> selections,String key,UUID correlation);
    ProviderQuote quote(CustomerIdentity identity,UUID id);
    ProviderQuote promotionalQuote(CustomerIdentity identity,UUID expectedCustomerId,String flightId,List<FlightFixture.Selection> selections,String couponCode,String key,UUID correlation);
    record PromotionView(UUID owner,FlightPromotion promotion){}
    PromotionView promotion(CustomerIdentity identity,UUID quoteId);
    ProviderReservation reserve(CustomerIdentity identity,UUID quoteId,int version,TravelMoney acceptedTotal,boolean consent,String key,UUID correlation);
    ProviderReservation reservation(CustomerIdentity identity,UUID id);
    record Page(UUID owner,List<ProviderReservation> items,UUID next) {}
    Page reservations(CustomerIdentity identity,UUID after);
    ProviderReservation act(CustomerIdentity identity,UUID id,ProviderReservation.Action action,List<String> passengerIds,boolean consent,String key,UUID correlation);
    List<ProviderReservation.Document> documents(CustomerIdentity identity,UUID id);
}
