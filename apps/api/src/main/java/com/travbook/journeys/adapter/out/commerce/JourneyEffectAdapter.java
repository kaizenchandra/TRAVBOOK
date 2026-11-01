package com.travbook.journeys.adapter.out.commerce;
import com.travbook.journeys.application.port.out.*;
import com.travbook.journeys.domain.model.Journey.*;
import com.travbook.payments.application.port.in.*;
import com.travbook.payments.domain.model.Payment;
import java.util.*;
public final class JourneyEffectAdapter implements JourneyEffects {
 private final PaymentOperations payments;private final PaymentRefunds refunds;private final JourneySupplier supplier;private final JourneyStore store;
 public JourneyEffectAdapter(PaymentOperations p,PaymentRefunds r,JourneySupplier s,JourneyStore store){payments=p;refunds=r;supplier=s;this.store=store;}
 private java.math.BigDecimal first(Booking b){return store.terms(b.quote().id()).firstAmount().decimal();}
 public boolean recoveryEvidence(Booking b){var receipt=lookup(b,b.step()).orElse(null);if(receipt==null)return false;var step=b.step();if(java.util.Set.of(Step.HOLD,Step.RESERVE,Step.CANCEL,Step.RELEASE).contains(step)){var c=supplier.current(b.id()).orElse(null);if(c==null)return step==Step.RELEASE&&receipt.accepted();if(!c.bookingId().equals(b.id())||!c.mode().equals("MOCK")||!c.resource().equals(b.quote().selection().supplier()+":"+b.quote().selection().resource()))return false;String expected=switch(step){case HOLD->receipt.accepted()?"HELD":"REJECTED";case RESERVE->receipt.accepted()?"CONFIRMED":"REJECTED";case CANCEL->"CANCELLED";case RELEASE->"RELEASED";default->throw new IllegalStateException();};if(!c.status().equals(expected))return false;if(step==Step.RESERVE&&receipt.accepted())return c.confirmedComponents()==b.quote().selection().components().size()&&(b.quote().selection().request().kind()!=Kind.BUS||c.activeSeats()==b.passengers().size());return step!=Step.CANCEL&&step!=Step.RELEASE||c.confirmedComponents()==0&&c.activeSeats()==0;}
  var p=payments.current(b.id()).orElse(null);if(p==null||!p.owner().equals(b.quote().owner())||!p.paymentId().equals(b.id())||!p.mode().equals("MOCK")||!p.currency().equals("INR")||p.amount().compareTo(first(b))!=0)return false;
  return switch(step){case AUTHORIZE->p.state()==(receipt.accepted()?Payment.State.AUTHORIZED:Payment.State.DECLINED)&&p.committedRefunds().signum()==0;case CAPTURE->p.state()==(receipt.accepted()?Payment.State.CAPTURED:Payment.State.AUTHORIZED)&&p.committedRefunds().signum()==0;case VOID->p.state()==Payment.State.VOIDED&&p.committedRefunds().signum()==0;case REFUND,REFUND_STATUS->p.state()==Payment.State.CAPTURED&&p.committedRefunds().compareTo(p.amount())==0&&(!receipt.completed()||p.settledRefunds().compareTo(p.amount())==0);default->false;};
 }
 public void validateHold(Booking b){supplier.validateHold(b);}
 public Optional<Result> lookup(Booking b,Step step){var actual=step==Step.REFUND_STATUS?Step.REFUND:step;UUID op=b.operation(actual);return switch(step){
  case HOLD,RESERVE,RELEASE,CANCEL->supplier.lookup(op).map(r->{if(!r.operationId().equals(op)||!r.bookingId().equals(b.id())||r.step()!=actual||!r.fingerprint().equals(b.supplierFingerprint())||!r.mode().equals("MOCK")||!r.reference().equals("MOCK-JOURNEY-"+b.id()))throw new IllegalStateException("Supplier receipt binding mismatch");return new Result(r.accepted(),true,r.reference());});
  case AUTHORIZE,CAPTURE,VOID->payments.lookup(op).map(r->{String state=step==Step.AUTHORIZE?"AUTHORIZED":step==Step.CAPTURE?"CAPTURED":"VOIDED";if(!r.operationId().equals(op)||!r.paymentId().equals(b.id())||!r.mode().equals("MOCK")||!r.currency().equals("INR")||r.amount().compareTo(first(b))!=0||r.accepted()!=r.state().name().equals(state)||!r.accepted()&&!(step==Step.AUTHORIZE&&r.state()==Payment.State.DECLINED||step==Step.CAPTURE&&r.state()==Payment.State.AUTHORIZED)||!r.reference().equals("MOCK-PAY-"+b.id()))throw new IllegalStateException("Payment receipt binding mismatch");return new Result(r.accepted(),true,r.reference());});
  case REFUND,REFUND_STATUS->refunds.lookup(op).map(r->{if(!r.operationId().equals(op)||!r.paymentId().equals(b.id())||!r.mode().equals("MOCK")||!r.currency().equals("INR")||r.amount().compareTo(first(b))!=0||r.reference()==null||!r.reference().startsWith("MOCK-REFUND-"))throw new IllegalStateException("Refund receipt binding mismatch");return new Result(true,r.completed(),r.reference());});
  default->Optional.empty();};}
 public Result execute(Booking b,Step step){UUID op=b.operation(step);switch(step){
  case HOLD,RESERVE,RELEASE,CANCEL->supplier.execute(b,step);
  case AUTHORIZE,CAPTURE,VOID->payments.execute(new PaymentOperations.Command(op,b.id(),b.quote().owner(),Payment.Operation.valueOf(step.name()),first(b),"INR",b.quote().expiresAt(),step==Step.AUTHORIZE&&b.scenario()==Scenario.AUTH_DECLINED||step==Step.CAPTURE&&b.scenario()==Scenario.CAPTURE_DECLINED));
  case REFUND->refunds.submit(new PaymentRefunds.Command(op,b.id(),b.quote().owner(),b.cancellation()==null?b.id():b.cancellation().id(),first(b),"INR",b.refundDelay(),b.correlationId(),op));
  default->throw new IllegalStateException("Pending effect receipt unavailable");}
  if(b.scenario()==Scenario.RESPONSE_LOST)throw new IllegalStateException("Simulated lost acknowledgement after durable provider commit");
  return lookup(b,step).orElseThrow();}
}
