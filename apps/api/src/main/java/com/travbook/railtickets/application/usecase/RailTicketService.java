package com.travbook.railtickets.application.usecase;
import com.travbook.railtickets.application.port.in.RailTickets;
import com.travbook.railtickets.application.port.out.*;
import com.travbook.railtickets.domain.model.RailTicket.*;
import com.travbook.shared.domain.*;
import com.travbook.customer.application.port.in.Customers;
import java.time.*;
import java.util.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
public final class RailTicketService implements RailTickets {
 private final Customers customers;private final RailTicketSelection selection;private final RailTicketStore store;private final Clock clock;
 public RailTicketService(Customers customers,RailTicketSelection selection,RailTicketStore store,Clock clock){this.customers=customers;this.selection=selection;this.store=store;this.clock=clock;}
 private UUID owner(CustomerIdentity identity){return customers.resolve(identity).id();}
 private void key(String key){if(key==null||!key.matches("[A-Za-z0-9_-]{16,100}"))throw new DomainFailure(DomainFailure.Code.VALIDATION_FAILED,"Provide an Idempotency-Key of 16–100 letters, digits, hyphens or underscores.");}
 private void consent(boolean value){if(!value)throw new DomainFailure(DomainFailure.Code.VALIDATION_FAILED,"Explicit consent is required.");}
 private String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 public QuoteDetails quote(CustomerIdentity identity,UUID journey,com.travbook.rail.domain.model.Rail.TravelClass travelClass,com.travbook.rail.domain.model.Rail.Quota quota,String boarding,List<Passenger> passengers,String key,UUID correlation){key(key);if(journey==null||travelClass==null||quota==null||boarding==null||passengers==null)throw new DomainFailure(DomainFailure.Code.VALIDATION_FAILED,"Rail selection and passengers are required.");UUID owner=owner(identity);String fingerprint=hash(journey+":"+travelClass+":"+quota+":"+boarding+":"+passengers);var replay=store.replay(owner,"QUOTE",key,fingerprint);if(replay.isPresent())return store.quote(owner,replay.get());var chosen=selection.reprice(journey,travelClass,quota,boarding,passengers);var q=new Quote(UUID.randomUUID(),owner,1,chosen,clock.instant(),chosen.expiresAt());q.valid(clock.instant());q=store.createQuote(q,key,fingerprint,correlation);return store.quote(owner,q.id());}
 public Booking pnr(CustomerIdentity identity,String pnr){if(pnr==null||!pnr.matches("MOCK-PNR-[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}"))throw new DomainFailure(DomainFailure.Code.VALIDATION_FAILED,"Only owned MOCK-PNR references are supported; real PNR access is disabled.");return store.pnr(owner(identity),pnr);}
 public QuoteDetails getQuote(CustomerIdentity identity,UUID id){return store.quote(owner(identity),id);}
 public QuoteDetails accept(CustomerIdentity identity,UUID id,int version,boolean consent,String key,UUID correlation){consent(consent);key(key);UUID owner=owner(identity);store.accept(owner,id,version,key,hash(id+":"+version),correlation);return store.quote(owner,id);}
 public Booking book(CustomerIdentity identity,UUID quoteId,int version,Scenario scenario,boolean consent,String key,UUID correlation){consent(consent);key(key);if(scenario==null)throw new DomainFailure(DomainFailure.Code.VALIDATION_FAILED,"Select the request and demonstration outcome.");return store.createBooking(owner(identity),quoteId,version,scenario,key,hash(quoteId+":"+version+":"+scenario),correlation);}
 public Booking get(CustomerIdentity identity,UUID id){return store.booking(owner(identity),id);}
 public Page list(CustomerIdentity identity,UUID after){return store.list(owner(identity),after);}
 public CancellationQuote cancellationQuote(CustomerIdentity identity,UUID id,String key,UUID correlation){key(key);return store.cancellationQuote(owner(identity),id,key,correlation);}
 public Booking cancel(CustomerIdentity identity,UUID id,UUID quoteId,boolean consent,boolean delay,boolean loss,String key,UUID correlation){consent(consent);key(key);return store.cancel(owner(identity),id,quoteId,delay,loss,key,hash(id+":"+quoteId+":"+delay+":"+loss),correlation,false);}
 public Voucher voucher(CustomerIdentity identity,UUID id,String key,UUID correlation){key(key);return store.voucher(owner(identity),id,key,correlation);}
 public Voucher readVoucher(CustomerIdentity identity,UUID id){var v=store.readVoucher(owner(identity),id);if(v.content().getBytes(StandardCharsets.UTF_8).length!=v.byteLength()||!hash(v.content()).equals(v.sha256()))throw new IllegalStateException("Rail voucher integrity check failed");return v;}
 public List<Event> timeline(CustomerIdentity identity,UUID id){return store.timeline(owner(identity),id);}

 public CancellationQuote passengerCancellationQuote(CustomerIdentity identity,UUID id,List<Integer> indexes,String key,UUID correlation){key(key);if(indexes==null||indexes.isEmpty()||indexes.size()>6||indexes.stream().anyMatch(i->i==null||i<0||i>5))throw new DomainFailure(DomainFailure.Code.VALIDATION_FAILED,"Choose active passengers.");return store.passengerCancellationQuote(owner(identity),id,indexes.stream().sorted().toList(),key,correlation);}
 public Booking cancelPassengers(CustomerIdentity identity,UUID id,UUID quoteId,boolean consent,boolean delay,boolean loss,String key,UUID correlation){consent(consent);key(key);return store.cancel(owner(identity),id,quoteId,delay,loss,key,hash(id+":"+quoteId+":"+delay+":"+loss),correlation,true);}
 public PassengerDetails details(CustomerIdentity identity,UUID id){return store.details(owner(identity),id);}
 public List<CancellationResult> cancellations(CustomerIdentity identity,UUID id){return store.cancellations(owner(identity),id);}
 public Voucher passengerVoucher(CustomerIdentity identity,UUID id,String key,UUID correlation){key(key);return store.passengerVoucher(owner(identity),id,key,correlation);}
 public Voucher readPassengerVoucher(CustomerIdentity identity,UUID id,int revision){var v=store.readPassengerVoucher(owner(identity),id,revision);if(v.content().getBytes(StandardCharsets.UTF_8).length!=v.byteLength()||!hash(v.content()).equals(v.sha256()))throw new IllegalStateException("Document integrity mismatch");return v;}
}
