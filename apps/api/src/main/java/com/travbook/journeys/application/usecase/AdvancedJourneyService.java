package com.travbook.journeys.application.usecase;
import com.travbook.journeys.application.port.in.*;
import com.travbook.journeys.application.port.out.*;
import com.travbook.journeys.domain.model.*;
import com.travbook.customer.application.port.in.Customers;
import com.travbook.shared.domain.*;
import java.time.*;
import java.util.*;
public final class AdvancedJourneyService implements AdvancedJourneys {
 private final Customers customers;private final JourneyStore base;private final JourneyCareStore care;private final JourneySelection selection;private final JourneyCabDispatch dispatch;private final Clock clock;
 public AdvancedJourneyService(Customers c,JourneyStore b,JourneyCareStore s,JourneySelection v,JourneyCabDispatch d,Clock clock){customers=c;base=b;care=s;selection=v;dispatch=d;this.clock=clock;}
 private UUID owner(CustomerIdentity i){return customers.resolve(i).id();}
 private void key(String k){if(k==null||!k.matches("[A-Za-z0-9_-]{16,100}"))Journey.invalid("A valid Idempotency-Key is required.");}
 private String hash(String s){return JourneyService.hash(s);}
 private Quotation result(UUID owner,UUID id){var q=base.quote(owner,id);return new Quotation(q,base.terms(id),care.replacement(id).map(r->new Replacement(r.bookingId(),r.version(),r.originalTotal())).orElse(null));}
 private Journey.Quote quote(UUID owner,Journey.Selection s){var now=clock.instant();var until=s.validUntil().isBefore(now.plusSeconds(300))?s.validUntil():now.plusSeconds(300);return new Journey.Quote(UUID.randomUUID(),owner,2,s,now,until,"MOCK");}
 private Journey.Quote policy(Journey.Quote q,Care.Terms t){var s=q.selection();var revised=new Journey.Selection(s.request(),s.supplier(),s.resource(),s.title(),s.departureAt(),s.timezone(),s.passengerAges(),s.details(),s.components(),s.total(),s.validUntil(),s.cancelUntil(),t.policy()+" Cancellation request cutoff: "+s.cancelUntil()+".");return new Journey.Quote(q.id(),q.owner(),2,revised,q.createdAt(),q.expiresAt(),q.mode());}
 public Quotation quote(CustomerIdentity i,Journey.Request r,Care.Plan plan,String k,UUID c){key(k);if(plan==null)Journey.invalid("Choose a funding plan.");var o=owner(i);var f=hash(r+":"+plan);var old=base.replay(o,"QUOTE",k,"V2:"+f);if(old.isPresent())return result(o,old.get());var q=quote(o,selection.select(i,r));var t=Care.Terms.of(q,plan);var saved=base.createAdvancedQuote(policy(q,t),t,k,f,c);return result(o,saved.id());}
 public Quotation quote(CustomerIdentity i,UUID id){return result(owner(i),id);}
 public Quotation accept(CustomerIdentity i,UUID id,int version,boolean consent,String k,UUID c){key(k);if(!consent)Journey.invalid("Accept the exact versioned terms.");var o=owner(i);base.accept(o,id,version,k,hash(id+":"+version),c);return result(o,id);}
 public Care.Summary book(CustomerIdentity i,UUID q,List<Journey.Passenger> p,String email,Journey.Scenario scenario,String k,UUID c){key(k);var o=owner(i);base.quote(o,q);if(care.replacementQuote(q))Journey.conflict("Use the linked replacement confirmation for this quote.");if(p==null||email==null||scenario==null)throw new DomainFailure(DomainFailure.Code.VALIDATION_FAILED,"Passenger/contact details and scenario required.");var f=new StringBuilder(q.toString());for(var person:p)f.append(':').append(person.name().length()).append(':').append(person.name()).append(':').append(person.age());f.append(':').append(email.length()).append(':').append(email).append(':').append(scenario);var b=base.book(o,q,p,email,scenario,k,hash(f.toString()),c);return care.summary(o,b.id());}
 public Care.Summary get(CustomerIdentity i,UUID id){return care.summary(owner(i),id);}
 public Page list(CustomerIdentity i,UUID after){var o=owner(i);var page=base.listAll(o,after);return new Page(page.items().stream().map(b->care.summary(o,b.id())).toList(),page.next());}
 public Care.Summary pay(CustomerIdentity i,UUID id,String k,UUID c){key(k);var o=owner(i);base.pay(o,id,k,c);return care.summary(o,id);}
 public Care.CancellationQuote cancellationQuote(CustomerIdentity i,UUID id,List<String> ids,String k,UUID c){key(k);return care.quoteCancellation(owner(i),id,ids,k,c);}
 public Care.Action cancel(CustomerIdentity i,UUID id,UUID q,boolean consent,boolean delay,boolean loss,String k,UUID c){key(k);if(!consent)Journey.invalid("Accept cancellation and the displayed collected-funds refund.");return care.cancel(owner(i),id,q,delay,loss,k,c);}
 public Care.Action balance(CustomerIdentity i,UUID id,boolean decline,boolean loss,String k,UUID c){key(k);return care.balance(owner(i),id,decline,loss,k,c);}
 public Quotation rescheduleQuote(CustomerIdentity i,UUID id,Journey.Request request,String k,UUID c){key(k);var o=owner(i);var b=base.booking(o,id);String f=hash("reschedule:"+id+":"+request);var old=base.replay(o,"QUOTE",k,"V2:"+f);if(old.isPresent())return result(o,old.get());var q=quote(o,selection.select(i,request));var terms=Care.Terms.of(q,Care.Plan.FULL);var saved=care.replacementQuote(o,id,b.version(),policy(q,terms),terms,k,f,c);return result(o,saved.id());}
 public Care.Action reschedule(CustomerIdentity i,UUID id,UUID q,long version,boolean consent,String k,UUID c){key(k);if(!consent)Journey.invalid("Accept the replacement payment and conditional original cancellation/refund.");return care.replace(owner(i),id,q,version,k,c);}
 public Journey.Document document(CustomerIdentity i,UUID id){return care.document(owner(i),id);}
 public Care.Dispatch dispatch(CustomerIdentity i,UUID id){var b=base.booking(owner(i),id);if(b.quote().selection().request().kind()!=Journey.Kind.CAB)Journey.invalid("Dispatch is only available for cabs.");if(!base.terms(b.quote().id()).dispatch()||b.status()!=Journey.Status.CONFIRMED)return Care.Dispatch.unavailable(id,clock.instant(),b.status()==Journey.Status.CANCELLED);return dispatch.get(id);}
 public Care.Dispatch advanceDemo(CustomerIdentity i,UUID id,long version,boolean consent,String k,UUID c){key(k);if(!consent)Journey.invalid("Explicit consent to advance the synthetic demo is required.");var o=owner(i);var b=base.booking(o,id);if(!base.terms(b.quote().id()).dispatch())Journey.conflict("These accepted terms do not include a cab dispatch demo.");return dispatch.advance(o,id,version,k);}
}
