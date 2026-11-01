package com.travbook.journeys.application.port.out;
import com.travbook.journeys.domain.model.Journey.*;
import com.travbook.journeys.application.port.in.Journeys.*;
import java.util.*;
public interface JourneyStore {
 java.util.List<com.travbook.shared.domain.Recovery.Case> reviews(Kind kind,int page);
 com.travbook.shared.domain.Recovery.Case review(Kind kind,UUID id);
 Booking checkpoint(Kind kind,UUID id,long version);
 Optional<com.travbook.shared.domain.Recovery.Action> recoveryReplay(String actor,String key,String fingerprint);
 com.travbook.shared.domain.Recovery.Action resume(Kind kind,UUID id,long version,String actor,com.travbook.shared.domain.Recovery.Reason reason,String key,String fingerprint,UUID correlation);
 com.travbook.journeys.domain.model.Care.Terms terms(UUID quote);
 Quote createAdvancedQuote(Quote quote,com.travbook.journeys.domain.model.Care.Terms terms,String key,String fingerprint,UUID correlation);
 Page listAll(UUID owner,UUID after);
 record Claim(Booking booking,UUID token,int attempts){}
 Optional<UUID> replay(UUID owner,String operation,String key,String fingerprint);
 QuoteDetails quote(UUID owner,UUID id);
 Quote createQuote(Quote quote,String key,String fingerprint,UUID correlation);
 void accept(UUID owner,UUID id,int version,String key,String fingerprint,UUID correlation);
 Booking book(UUID owner,UUID quote,List<Passenger> passengers,String email,Scenario scenario,String key,String fingerprint,UUID correlation);
 Booking booking(UUID owner,UUID id);
 Page list(UUID owner,UUID after);
 Booking pay(UUID owner,UUID id,String key,UUID correlation);
 CancellationQuote cancellationQuote(UUID owner,UUID id,String key,UUID correlation);
 Booking cancel(UUID owner,UUID id,UUID quote,boolean delay,String key,String fingerprint,UUID correlation);
 Document document(UUID owner,UUID id);
 Optional<Claim> claim();
 boolean complete(Claim claim,Booking next);
 void retry(Claim claim);
}
