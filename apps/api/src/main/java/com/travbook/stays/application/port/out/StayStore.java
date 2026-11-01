package com.travbook.stays.application.port.out;
import com.travbook.stays.domain.model.Stay.*;
import com.travbook.stays.application.port.in.HotelStays.*;
import java.util.*;
public interface StayStore {
 record Claim(Booking booking,UUID token,int attempts){}
 Optional<UUID> replay(UUID owner,String operation,String key,String fingerprint);
 Quote createQuote(Quote quote,String key,String fingerprint,UUID correlation);
 QuoteDetails quote(UUID owner,UUID id);
 void accept(UUID owner,UUID id,int version,String key,String fingerprint,UUID correlation);
 Booking createBooking(UUID owner,UUID quoteId,int version,List<Guest> guests,SpecialRequest request,Scenario scenario,String key,String fingerprint,UUID correlation);
 Booking amend(UUID owner,UUID id,UUID quoteId,long version,boolean loss,String key,String fingerprint,UUID correlation);
 ChangeQuote createChangeQuote(UUID owner,UUID booking,long version,Quote quote,String key,String fingerprint,UUID correlation);
 ChangeQuote changeQuote(UUID owner,UUID booking,UUID quote);
 Booking change(UUID owner,UUID booking,UUID quote,long version,List<Guest> guests,Scenario scenario,boolean delay,boolean loss,String key,String fingerprint,UUID correlation);
 Booking booking(UUID owner,UUID id);
 Page list(UUID owner,UUID after);
 CancellationQuote cancellationQuote(UUID owner,UUID id,String key,UUID correlation);
 Booking cancel(UUID owner,UUID id,UUID quoteId,boolean delay,boolean loss,String key,String fingerprint,UUID correlation);
 Voucher voucher(UUID owner,UUID id,String key,UUID correlation);
 Voucher readVoucher(UUID owner,UUID id);
 List<Event> timeline(UUID owner,UUID id);
 Optional<Claim> claim();
 boolean complete(Claim claim,Booking next);
 void retry(Claim claim);
 Booking legacyReview(UUID id,long version);
 com.travbook.shared.domain.Recovery.Action reconcileLegacy(UUID id,long version,String actor,com.travbook.shared.domain.Recovery.Reason reason,String key,String fingerprint,UUID correlation,com.travbook.shared.domain.Recovery.Proof proof);

 java.util.List<com.travbook.shared.domain.Recovery.Case> reviews(int page);
 com.travbook.shared.domain.Recovery.Case review(UUID id);
 Booking checkpoint(UUID id,long version);
 java.util.Optional<com.travbook.shared.domain.Recovery.Action> recoveryReplay(String actor,String key,String fingerprint);
 com.travbook.shared.domain.Recovery.Action resume(UUID id,long version,String actor,com.travbook.shared.domain.Recovery.Reason reason,String key,String fingerprint,UUID correlation);

}
