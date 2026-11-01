package com.travbook.journeys.application.usecase;
import com.travbook.journeys.application.port.in.ProcessJourneys;
import com.travbook.journeys.application.port.out.*;
import com.travbook.journeys.domain.model.Journey.*;
import com.travbook.shared.domain.DomainFailure;
import java.time.Clock;
public final class JourneyWorker implements ProcessJourneys {
 private final JourneyStore store;private final JourneySelection selection;private final JourneyEffects effects;private final Clock clock;
 public JourneyWorker(JourneyStore s,JourneySelection v,JourneyEffects e,Clock c){store=s;selection=v;effects=e;clock=c;}
 public boolean tick(){var found=store.claim();if(found.isEmpty())return false;var c=found.get();var b=c.booking();try{
  Booking next;
  if(b.step()==Step.WAIT_PAYMENT)next=b.failedBeforePayment("HOLD_EXPIRED",clock.instant());
  else if(b.step()==Step.VALIDATE_PAYMENT){b.quote().valid(clock.instant());if(b.quote().selection().request().kind()==Kind.BUS)effects.validateHold(b);else selection.validate(b.quote());next=b.validated(clock.instant());}
  else {var r=effects.lookup(b,b.step()).orElseGet(()->effects.execute(b,b.step()));if(b.step()==Step.REFUND_STATUS&&!r.completed()){store.retry(c);return true;}next=b.result(r.accepted(),r.completed(),r.reference(),clock.instant());}
  store.complete(c,next);
 }catch(DomainFailure e){boolean known=java.util.Set.of(DomainFailure.Code.QUOTE_EXPIRED,DomainFailure.Code.SEARCH_EXPIRED,DomainFailure.Code.OFFER_EXPIRED,DomainFailure.Code.OFFER_UNAVAILABLE,DomainFailure.Code.BOOKING_CONFLICT).contains(e.code());if((known||b.step()==Step.VALIDATE_PAYMENT&&e.code()==DomainFailure.Code.VALIDATION_FAILED)&&(b.step()==Step.VALIDATE_PAYMENT||b.step()==Step.AUTHORIZE||b.step()==Step.CAPTURE))store.complete(c,b.failedBeforePayment(e.code().name(),clock.instant()));else store.retry(c);}catch(RuntimeException unknown){store.retry(c);}return true;}
}
