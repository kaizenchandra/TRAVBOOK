package com.travbook.journeys.application.usecase;
import com.travbook.journeys.application.port.in.ProcessJourneyCare;
import com.travbook.journeys.application.port.out.*;
import com.travbook.journeys.domain.model.Care.*;
import com.travbook.journeys.domain.model.Journey;
import com.travbook.shared.domain.DomainFailure;
import java.time.Clock;
public final class JourneyCareWorker implements ProcessJourneyCare {
 private final JourneyCareStore store;private final JourneyStore bookings;private final JourneyCareEffects effects;private final Clock clock;
 public JourneyCareWorker(JourneyCareStore s,JourneyStore b,JourneyCareEffects e,Clock c){store=s;bookings=b;effects=e;clock=c;}
 public boolean tick(){if(store.expireBalance())return true;var found=store.claim();if(found.isEmpty())return false;var claim=found.get();var a=claim.action();try{Action next;if(a.step()==Phase.VALIDATE){if(!clock.instant().isBefore(a.deadline()))next=a.next(State.FAILED,Phase.DONE,false,a.refunds(),0,"BALANCE_EXPIRED",clock.instant());else{effects.validate(a);next=a.next(State.PENDING,Phase.AUTHORIZE,false,a.refunds(),0,null,clock.instant());}}else if(a.step()==Phase.WAIT_REPLACEMENT){var child=bookings.booking(a.booking().quote().owner(),a.replacementId());if(child.status()==Journey.Status.CONFIRMED)next=a.next(State.PENDING,Phase.CANCEL,false,a.refunds(),0,null,clock.instant());else if(child.status()==Journey.Status.FAILED&&child.step()==Journey.Step.DONE)next=a.next(State.FAILED,Phase.DONE,false,a.refunds(),0,"REPLACEMENT_FAILED_OR_EXPIRED",clock.instant());else if(!clock.instant().isBefore(a.deadline()))next=a.review(clock.instant());else next=a.next(State.WAITING,Phase.WAIT_REPLACEMENT,false,a.refunds(),0,null,clock.instant());}else{var result=effects.lookup(a).orElseGet(()->effects.execute(a));if(a.step()==Phase.REFUND_STATUS&&!result.completed()){store.retry(claim);return true;}next=a.result(result.accepted(),result.completed(),result.reference(),clock.instant());}store.complete(claim,next);}catch(DomainFailure known){if(a.step()==Phase.AUTHORIZE&&known.code()==DomainFailure.Code.QUOTE_EXPIRED)store.complete(claim,a.result(false,true,null,clock.instant()));else store.retry(claim);}catch(RuntimeException unknown){store.retry(claim);}return true;}
}
