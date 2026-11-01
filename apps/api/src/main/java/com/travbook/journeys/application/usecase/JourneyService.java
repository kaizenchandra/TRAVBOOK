package com.travbook.journeys.application.usecase;
import com.travbook.journeys.application.port.in.Journeys;
import com.travbook.journeys.application.port.out.*;
import com.travbook.journeys.domain.model.Journey.*;
import com.travbook.journeys.domain.model.Journey;
import com.travbook.shared.domain.*;
import com.travbook.customer.application.port.in.Customers;
import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
public final class JourneyService implements Journeys {
 private final Customers customers;private final JourneySelection selection;private final JourneyStore store;private final Clock clock;
 public JourneyService(Customers c,JourneySelection s,JourneyStore store,Clock clock){customers=c;selection=s;this.store=store;this.clock=clock;}
 private void legacy(Quote q){if(q.version()!=1)throw new DomainFailure(DomainFailure.Code.BOOKING_CONFLICT,"Use the current journey API for these versioned terms.");}
 private UUID owner(CustomerIdentity i){return customers.resolve(i).id();}
 private void key(String k){if(k==null||!k.matches("[A-Za-z0-9_-]{16,100}"))Journey.invalid("A valid Idempotency-Key is required.");}
 public static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 public QuoteDetails quote(CustomerIdentity i,Request r,String k,UUID correlation){key(k);var o=owner(i);var f=hash(r.toString());var prior=store.replay(o,"QUOTE",k,f);if(prior.isPresent())return store.quote(o,prior.get());var s=selection.select(i,r);var now=clock.instant();var expires=s.validUntil().isBefore(now.plusSeconds(300))?s.validUntil():now.plusSeconds(300);var q=store.createQuote(new Quote(UUID.randomUUID(),o,1,s,now,expires,"MOCK"),k,f,correlation);return store.quote(o,q.id());}
 public QuoteDetails quote(CustomerIdentity i,UUID id){var q=store.quote(owner(i),id);legacy(q.quote());return q;}
 public QuoteDetails accept(CustomerIdentity i,UUID id,int version,boolean consent,String k,UUID c){key(k);if(!consent)Journey.invalid("Accept the exact quote and policies.");var o=owner(i);legacy(store.quote(o,id).quote());store.accept(o,id,version,k,hash(id+":"+version),c);return store.quote(o,id);}
 private String bookingFingerprint(UUID q,List<Passenger> p,String email,Scenario scenario){if(p==null||email==null||scenario==null)throw new DomainFailure(DomainFailure.Code.VALIDATION_FAILED,"Passenger, contact and scenario fields required.");var value=new StringBuilder(q.toString()).append(':').append(p.size());for(var person:p)value.append(':').append(person.name().length()).append(':').append(person.name()).append(':').append(person.age());value.append(':').append(email.length()).append(':').append(email).append(':').append(scenario);return hash(value.toString());}
 public Booking book(CustomerIdentity i,UUID q,List<Passenger> p,String email,Scenario scenario,String k,UUID c){key(k);legacy(store.quote(owner(i),q).quote());return store.book(owner(i),q,p,email,scenario,k,bookingFingerprint(q,p,email,scenario),c);}
 public Booking booking(CustomerIdentity i,UUID id){var b=store.booking(owner(i),id);legacy(b.quote());return b;}
 public Page list(CustomerIdentity i,UUID after){return store.list(owner(i),after);}
 public Booking pay(CustomerIdentity i,UUID id,String k,UUID c){key(k);legacy(store.booking(owner(i),id).quote());return store.pay(owner(i),id,k,c);}
 public CancellationQuote cancellationQuote(CustomerIdentity i,UUID id,String k,UUID c){key(k);legacy(store.booking(owner(i),id).quote());return store.cancellationQuote(owner(i),id,k,c);}
 public Booking cancel(CustomerIdentity i,UUID id,UUID q,boolean consent,boolean delay,String k,UUID c){key(k);legacy(store.booking(owner(i),id).quote());if(!consent)Journey.invalid("Accept the cancellation refund amount.");return store.cancel(owner(i),id,q,delay,k,hash(id+":"+q+":"+delay),c);}
 public Document document(CustomerIdentity i,UUID id){legacy(store.booking(owner(i),id).quote());return store.document(owner(i),id);}
}
