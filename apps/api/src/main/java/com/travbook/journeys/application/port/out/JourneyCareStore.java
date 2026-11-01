package com.travbook.journeys.application.port.out;
import com.travbook.journeys.domain.model.*;
import java.util.*;
public interface JourneyCareStore {
 record Claim(Care.Action action,UUID token,int attempts){}
 Care.Summary summary(UUID owner,UUID id);
 Care.CancellationQuote quoteCancellation(UUID owner,UUID id,List<String> ids,String key,UUID correlation);
 Care.Action cancel(UUID owner,UUID id,UUID quote,boolean delay,boolean loss,String key,UUID correlation);
 Care.Action balance(UUID owner,UUID id,boolean decline,boolean loss,String key,UUID correlation);
 record Replacement(UUID bookingId,long version,com.travbook.shared.domain.TravelMoney originalTotal){}
 Optional<Replacement> replacement(UUID quote);
 Journey.Quote replacementQuote(UUID owner,UUID booking,long version,Journey.Quote quote,Care.Terms terms,String key,String fingerprint,UUID correlation);
 boolean replacementQuote(UUID quote);
 Care.Action replace(UUID owner,UUID booking,UUID quote,long version,String key,UUID correlation);
 Journey.Document document(UUID owner,UUID id);
 Optional<Claim> claim();
 boolean complete(Claim claim,Care.Action next);
 void retry(Claim claim);
 boolean expireBalance();
 Optional<com.travbook.shared.domain.Recovery.Case> reviewCase(Journey.Kind kind,UUID id);
 Care.Action reviewCheckpoint(Journey.Kind kind,UUID id,long version);
 Optional<com.travbook.shared.domain.Recovery.Action> recoveryReplay(String actor,String key,String fingerprint);
 com.travbook.shared.domain.Recovery.Action resume(Journey.Kind kind,UUID id,long version,String actor,com.travbook.shared.domain.Recovery.Reason reason,String key,String fingerprint,UUID correlation);
}
