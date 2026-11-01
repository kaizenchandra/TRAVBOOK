package com.travbook.journeys.application.port.in;
import com.travbook.journeys.domain.model.Journey.*;
import com.travbook.shared.domain.CustomerIdentity;
import java.time.Instant;
import java.util.*;
public interface Journeys {
 record QuoteDetails(Quote quote,Instant acceptedAt,UUID bookingId,boolean expired){}
 record Page(List<Booking> items,UUID next){}
 QuoteDetails quote(CustomerIdentity identity,Request request,String key,UUID correlation);
 QuoteDetails quote(CustomerIdentity identity,UUID id);
 QuoteDetails accept(CustomerIdentity identity,UUID id,int version,boolean consent,String key,UUID correlation);
 Booking book(CustomerIdentity identity,UUID quote,List<Passenger> passengers,String email,Scenario scenario,String key,UUID correlation);
 Booking booking(CustomerIdentity identity,UUID id);
 Page list(CustomerIdentity identity,UUID after);
 Booking pay(CustomerIdentity identity,UUID id,String key,UUID correlation);
 CancellationQuote cancellationQuote(CustomerIdentity identity,UUID id,String key,UUID correlation);
 Booking cancel(CustomerIdentity identity,UUID id,UUID quote,boolean consent,boolean delay,String key,UUID correlation);
 Document document(CustomerIdentity identity,UUID id);
}
