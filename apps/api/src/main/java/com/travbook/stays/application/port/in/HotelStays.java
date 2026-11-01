package com.travbook.stays.application.port.in;
import com.travbook.stays.domain.model.Stay.*;
import com.travbook.shared.domain.CustomerIdentity;
import java.time.Instant;
import java.util.*;
public interface HotelStays {
 record QuoteDetails(Quote quote,Instant acceptedAt,UUID bookingId,boolean expired){}
 record ChangeQuote(QuoteDetails details,long bookingVersion){public com.travbook.hotels.domain.model.HotelMoney newCharge(){return details.quote().changeCharge();}public com.travbook.hotels.domain.model.HotelMoney expectedRefund(){return details.quote().changeRefund();}public java.math.BigDecimal netDifference(){return details.quote().selection().total().amount().subtract(details.quote().selection().previousTotal().amount());}}
 ChangeQuote changeQuote(CustomerIdentity identity,UUID booking,long version,com.travbook.hotels.domain.model.HotelCriteria criteria,List<com.travbook.hotels.domain.model.HotelOffer.Plan> plans,String key,UUID correlation);
 Booking change(CustomerIdentity identity,UUID booking,UUID quote,long version,List<Guest> guests,Scenario scenario,boolean consent,boolean delay,boolean loss,String key,UUID correlation);
 record Page(List<Booking> items,UUID nextCursor){}
 record Event(UUID eventId,String eventType,long aggregateVersion,Instant occurredAt,String status,String payment,String reservation,String refund){}
 QuoteDetails quote(CustomerIdentity identity,List<UUID> offers,String key,UUID correlation);
 QuoteDetails quote(CustomerIdentity identity,List<UUID> offers,CollectionMethod method,String key,UUID correlation);
 Booking amend(CustomerIdentity identity,UUID id,UUID quoteId,long bookingVersion,boolean consent,boolean loss,String key,UUID correlation);
 QuoteDetails getQuote(CustomerIdentity identity,UUID id);
 QuoteDetails accept(CustomerIdentity identity,UUID id,int version,boolean consent,String key,UUID correlation);
 Booking book(CustomerIdentity identity,UUID quoteId,int version,List<Guest> guests,SpecialRequest request,Scenario scenario,boolean consent,String key,UUID correlation);
 Booking get(CustomerIdentity identity,UUID id);
 Page list(CustomerIdentity identity,UUID after);
 CancellationQuote cancellationQuote(CustomerIdentity identity,UUID id,String key,UUID correlation);
 Booking cancel(CustomerIdentity identity,UUID id,UUID cancellationQuoteId,boolean consent,boolean delay,boolean responseLoss,String key,UUID correlation);
 Voucher voucher(CustomerIdentity identity,UUID id,String key,UUID correlation);
 Voucher readVoucher(CustomerIdentity identity,UUID id);
 List<Event> timeline(CustomerIdentity identity,UUID id);
}
