package com.travbook.railtickets.application.port.in;
import com.travbook.railtickets.domain.model.RailTicket.*;
import com.travbook.shared.domain.CustomerIdentity;
import java.time.Instant;
import java.util.*;
public interface RailTickets {
 record QuoteDetails(Quote quote,Instant acceptedAt,UUID bookingId,boolean expired){}
 record PassengerDetails(Booking booking,List<CancellationResult> cancellations){public PassengerDetails{cancellations=List.copyOf(cancellations);}}
 PassengerDetails details(CustomerIdentity identity,UUID id);
 record Page(List<Booking> items,UUID nextCursor){}
 record Event(UUID eventId,String eventType,long aggregateVersion,Instant occurredAt,String status,String payment,String reservation,String refund){}
 QuoteDetails quote(CustomerIdentity identity,UUID journey,com.travbook.rail.domain.model.Rail.TravelClass travelClass,com.travbook.rail.domain.model.Rail.Quota quota,String boarding,List<Passenger> passengers,String key,UUID correlation);
 QuoteDetails getQuote(CustomerIdentity identity,UUID id);
 QuoteDetails accept(CustomerIdentity identity,UUID id,int version,boolean consent,String key,UUID correlation);
 Booking book(CustomerIdentity identity,UUID quoteId,int version,Scenario scenario,boolean consent,String key,UUID correlation);
 Booking pnr(CustomerIdentity identity,String pnr);
 Booking get(CustomerIdentity identity,UUID id);
 Page list(CustomerIdentity identity,UUID after);
 CancellationQuote cancellationQuote(CustomerIdentity identity,UUID id,String key,UUID correlation);
 Booking cancel(CustomerIdentity identity,UUID id,UUID cancellationQuoteId,boolean consent,boolean delay,boolean responseLoss,String key,UUID correlation);
 Voucher voucher(CustomerIdentity identity,UUID id,String key,UUID correlation);
 Voucher readVoucher(CustomerIdentity identity,UUID id);
 CancellationQuote passengerCancellationQuote(CustomerIdentity identity,UUID id,List<Integer> indexes,String key,UUID correlation);
 Booking cancelPassengers(CustomerIdentity identity,UUID id,UUID cancellationQuoteId,boolean consent,boolean delay,boolean responseLoss,String key,UUID correlation);
 List<CancellationResult> cancellations(CustomerIdentity identity,UUID id);
 Voucher passengerVoucher(CustomerIdentity identity,UUID id,String key,UUID correlation);
 Voucher readPassengerVoucher(CustomerIdentity identity,UUID id,int revision);
 List<Event> timeline(CustomerIdentity identity,UUID id);
}
