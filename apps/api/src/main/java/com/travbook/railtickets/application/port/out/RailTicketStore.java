package com.travbook.railtickets.application.port.out;
import com.travbook.railtickets.domain.model.RailTicket.*;
import com.travbook.railtickets.application.port.in.RailTickets.*;
import java.util.*;
public interface RailTicketStore {
 record Claim(Booking booking,UUID token,int attempts){}
 Optional<UUID> replay(UUID owner,String operation,String key,String fingerprint);
 Quote createQuote(Quote quote,String key,String fingerprint,UUID correlation);
 QuoteDetails quote(UUID owner,UUID id);
 void accept(UUID owner,UUID id,int version,String key,String fingerprint,UUID correlation);
 Booking createBooking(UUID owner,UUID quoteId,int version,Scenario scenario,String key,String fingerprint,UUID correlation);
 Booking pnr(UUID owner,String pnr);
 Booking booking(UUID owner,UUID id);
 Page list(UUID owner,UUID after);
 CancellationQuote cancellationQuote(UUID owner,UUID id,String key,UUID correlation);
 Booking cancel(UUID owner,UUID id,UUID quoteId,boolean delay,boolean loss,String key,String fingerprint,UUID correlation,boolean passengerAware);
 Voucher voucher(UUID owner,UUID id,String key,UUID correlation);
 Voucher readVoucher(UUID owner,UUID id);
 List<Event> timeline(UUID owner,UUID id);
 Optional<Claim> claim();
 boolean complete(Claim claim,Booking next);
 void retry(Claim claim);
 Booking legacyReview(UUID id,long version);
 com.travbook.shared.domain.Recovery.Action reconcileLegacy(UUID id,long version,String actor,com.travbook.shared.domain.Recovery.Reason reason,String key,String fingerprint,UUID correlation,com.travbook.shared.domain.Recovery.Proof proof);

 CancellationQuote passengerCancellationQuote(UUID owner,UUID id,List<Integer> indexes,String key,UUID correlation);
 PassengerDetails details(UUID owner,UUID id);
 List<CancellationResult> cancellations(UUID owner,UUID id);
 Voucher passengerVoucher(UUID owner,UUID id,String key,UUID correlation);
 Voucher readPassengerVoucher(UUID owner,UUID id,int revision);

 java.util.List<com.travbook.shared.domain.Recovery.Case> reviews(int page);
 com.travbook.shared.domain.Recovery.Case review(UUID id);
 Booking checkpoint(UUID id,long version);
 java.util.Optional<com.travbook.shared.domain.Recovery.Action> recoveryReplay(String actor,String key,String fingerprint);
 com.travbook.shared.domain.Recovery.Action resume(UUID id,long version,String actor,com.travbook.shared.domain.Recovery.Reason reason,String key,String fingerprint,UUID correlation);

}
