package com.travbook.railtickets.application.usecase;
import com.travbook.railtickets.application.port.out.*;
import com.travbook.railtickets.domain.model.RailTicket.*;
import com.travbook.shared.domain.DomainFailure;
import java.time.Clock;
public final class RailTicketWorker implements com.travbook.railtickets.application.port.in.ProcessRailTickets {
 private final RailTicketStore store;private final RailTicketSelection selection;private final RailTicketEffects effects;private final Clock clock;
 public RailTicketWorker(RailTicketStore store,RailTicketSelection selection,RailTicketEffects effects,Clock clock){this.store=store;this.selection=selection;this.effects=effects;this.clock=clock;}
 public boolean tick(){var claim=store.claim();if(claim.isEmpty())return false;var c=claim.get();var b=c.booking();if(c.attempts()>(b.step()==Step.REFUND_STATUS?20:5)){store.complete(c,b.review(clock.instant()));return true;}try{
  Booking next;if(b.step()==Step.VALIDATE){b.quote().valid(clock.instant());selection.validate(b.quote());next=b.validated(clock.instant());}
  else{var result=effects.lookup(b,b.step()).orElseGet(()->effects.execute(b,b.step()));var now=clock.instant();next=switch(b.step()){
   case AUTHORIZE->b.authorized(result.accepted(),result.reference(),now);
   case RESERVE->b.reserved(result.accepted(),result.reservation(),result.reference(),now);
   case CAPTURE->b.captured(result.accepted(),result.reference(),now);
   case CANCEL->{if(!result.accepted())throw new IllegalStateException("Supplier cancellation unknown");yield b.cancelled(now);}
   case VOID->{if(!result.accepted())throw new IllegalStateException("Payment void unknown");yield b.voided(now);}
   case REFUND,REFUND_STATUS->{if(!result.completed()&&b.step()==Step.REFUND_STATUS){store.retry(c);yield null;}yield b.refundSubmitted(result.reference(),result.completed(),now);}
   default->throw new IllegalStateException("Unexpected durable step");};}
  if(next!=null)store.complete(c,next);
 }catch(DomainFailure e){if(b.step()==Step.VALIDATE&&(e.code()==DomainFailure.Code.QUOTE_EXPIRED||e.code()==DomainFailure.Code.OFFER_UNAVAILABLE||e.code()==DomainFailure.Code.BOOKING_CONFLICT)||e.code()==DomainFailure.Code.QUOTE_EXPIRED&&(b.step()==Step.AUTHORIZE||b.step()==Step.CAPTURE))store.complete(c,b.failBeforeEffect(e.code().name(),clock.instant()));else store.retry(c);}
 catch(RuntimeException unknown){store.retry(c);}return true;}
}
