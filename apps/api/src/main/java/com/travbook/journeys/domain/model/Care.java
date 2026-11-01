package com.travbook.journeys.domain.model;
import com.travbook.journeys.domain.model.Journey.*;
import com.travbook.shared.domain.TravelMoney;
import java.time.*;
import java.util.*;
import java.math.*;
import java.nio.charset.StandardCharsets;
/** Provider terms and independent aftercare/funding operations; all money is exact INR. */
public final class Care {
 private Care(){}
 public enum Plan {FULL,DEPOSIT}
 public enum Type {CANCEL,BALANCE,RESCHEDULE}
 public enum State {PENDING,WAITING,SUCCEEDED,FAILED,REVIEW}
 public enum Phase {VALIDATE,WAIT_REPLACEMENT,AUTHORIZE,CAPTURE,VOID,CANCEL,REFUND,REFUND_STATUS,DONE}
 public enum RefundState {NOT_STARTED,PENDING,SUCCEEDED}
 public record Terms(UUID quoteId,Plan fundingPlan,TravelMoney firstAmount,TravelMoney balanceAmount,Instant balanceDue,boolean partialCancellation,boolean rescheduling,boolean dispatch,String policy){
  public static Terms of(Quote q,Plan plan){boolean modern=q.version()==2;var total=q.selection().total();var first=total;Instant due=null;if(plan==Plan.DEPOSIT){if(!modern||q.selection().request().kind()!=Kind.HOLIDAY)Journey.invalid("Deposit milestones are only available for new holiday terms.");due=q.selection().departureAt().minusSeconds(172800);if(!due.isAfter(q.createdAt().plusSeconds(86400)))Journey.invalid("Deposit plans require departure more than 72 hours ahead.");first=TravelMoney.inr(total.decimal().multiply(new BigDecimal("0.30")));}return new Terms(q.id(),plan,first,TravelMoney.inr(total.decimal().subtract(first.decimal())),due,modern&&q.selection().request().kind()!=Kind.CAB,modern&&q.selection().request().kind()==Kind.BUS,modern&&q.selection().request().kind()==Kind.CAB,modern?"MOCK v2: refundable bus seats or optional holiday activities before the quoted cutoff. At least one adult must remain with bus minors. Partial holiday cancellation requires full funding. Cab reservations cancel as one vehicle. Rescheduling is a separately paid bus replacement; the original is cancelled/refunded only after replacement confirmation. Conditional refund entitlement is locked at acceptance. Holiday deposit is 30%, balance due 48h before departure; missed balance triggers cancellation and collected-funds refund. Final vouchers require full payment. No real travel or charge.":q.selection().cancellationPolicy());}
  public Map<String,TravelMoney> firstAllocations(Quote q){var result=new LinkedHashMap<String,TravelMoney>();BigDecimal used=BigDecimal.ZERO;var components=q.selection().components();for(int i=0;i<components.size();i++){var c=components.get(i);var amount=i==components.size()-1?firstAmount.decimal().subtract(used):c.amount().decimal().multiply(firstAmount.decimal()).divide(q.selection().total().decimal(),2,RoundingMode.HALF_UP);result.put(c.id(),TravelMoney.inr(amount));used=used.add(amount);}return Map.copyOf(result);}
 }
 public record RefundPart(UUID paymentId,TravelMoney amount,String reference,RefundState status){public RefundPart {if(amount.decimal().signum()<=0)Journey.invalid("Refund parts must be positive.");}public RefundPart receipt(String ref,boolean done){return new RefundPart(paymentId,amount,ref,done?RefundState.SUCCEEDED:RefundState.PENDING);}}
 public record CancellationQuote(UUID id,UUID bookingId,long bookingVersion,List<String> componentIds,List<RefundPart> refunds,TravelMoney refundAmount,TravelMoney unpaidAmountCancelled,Instant expiresAt,String mode){public CancellationQuote {componentIds=List.copyOf(componentIds);refunds=List.copyOf(refunds);}}
 public record Action(UUID id,long version,Booking booking,Type type,State status,Phase step,List<String> componentIds,List<RefundPart> refunds,TravelMoney amount,Instant deadline,UUID replacementId,boolean supplierApplied,boolean simulateDelay,boolean simulateDecline,boolean responseLoss,int refundIndex,String failure,Instant createdAt,Instant updatedAt,UUID correlationId){
  public Action {componentIds=List.copyOf(componentIds);refunds=List.copyOf(refunds);}
  public UUID operation(String name){return UUID.nameUUIDFromBytes((id+":"+name).getBytes(StandardCharsets.UTF_8));}
  public boolean terminal(){return status==State.SUCCEEDED||status==State.FAILED||status==State.REVIEW;}
  public RefundPart currentRefund(){return refunds.get(refundIndex);}
  public Action next(State state,Phase phase,boolean applied,List<RefundPart> parts,int index,String reason,Instant now){return new Action(id,version+1,booking,type,state,phase,componentIds,parts,amount,deadline,replacementId,applied,simulateDelay,simulateDecline,responseLoss,index,reason,createdAt,now,correlationId);}
  public Action resumeFrom(Action previous,Instant now){if(previous==null)throw new com.travbook.shared.domain.DomainFailure(com.travbook.shared.domain.DomainFailure.Code.BOOKING_CONFLICT,"Missing care checkpoint.");if(status!=State.REVIEW||!previous.review(updatedAt).equals(this))Journey.conflict("Invalid care checkpoint.");return new Action(id,version+1,booking,type,previous.status(),step,componentIds,refunds,amount,deadline,replacementId,supplierApplied,simulateDelay,simulateDecline,responseLoss,refundIndex,previous.failure(),createdAt,now,correlationId);}
  public Action review(Instant now){return next(State.REVIEW,step,supplierApplied,refunds,refundIndex,"OUTCOME_REQUIRES_REVIEW",now);}
  public Action result(boolean accepted,boolean completed,String reference,Instant now){return switch(step){
   case AUTHORIZE->next(accepted?State.PENDING:State.FAILED,accepted?Phase.CAPTURE:Phase.DONE,false,refunds,refundIndex,accepted?null:"BALANCE_DECLINED",now);
   case CAPTURE->next(accepted?State.SUCCEEDED:State.PENDING,accepted?Phase.DONE:Phase.VOID,false,refunds,refundIndex,accepted?null:"BALANCE_CAPTURE_DECLINED",now);
   case VOID->{if(!accepted)throw new IllegalStateException("Void outcome unknown");yield next(State.FAILED,Phase.DONE,false,refunds,refundIndex,"BALANCE_DECLINED",now);}
   case CANCEL->next(accepted?(refunds.isEmpty()?State.SUCCEEDED:State.PENDING):State.FAILED,accepted&&!refunds.isEmpty()?Phase.REFUND:Phase.DONE,accepted,refunds,0,accepted?null:"SUPPLIER_REJECTED",now);
   case REFUND,REFUND_STATUS->{var parts=new ArrayList<>(refunds);parts.set(refundIndex,currentRefund().receipt(reference,completed));int n=completed?refundIndex+1:refundIndex;yield next(completed&&n==parts.size()?State.SUCCEEDED:State.PENDING,completed?(n==parts.size()?Phase.DONE:Phase.REFUND):Phase.REFUND_STATUS,supplierApplied,parts,n,failure,now);}
   default->throw new IllegalStateException("No effect at this phase");};}
 }
 public record Funding(Plan plan,TravelMoney paid,TravelMoney refunded,TravelMoney refundPending,TravelMoney outstanding,Instant balanceDue,String status){}
 public record Summary(Booking booking,Terms terms,Funding funding,List<String> activeComponentIds,List<Action> actions,boolean documentAvailable,boolean legacy,UUID rescheduledFrom,UUID rescheduledTo){}
 public enum Stage {UNAVAILABLE,ASSIGNED,EN_ROUTE,ARRIVED,IN_PROGRESS,COMPLETED,CANCELLED}
 public record Dispatch(UUID bookingId,long version,Stage stage,String driverLabel,String vehicleLabel,String locationLabel,int progressPercent,Instant updatedAt,Instant staleAt,String mode,String disclaimer){
  public static Dispatch unavailable(UUID id,Instant now,boolean cancelled){return new Dispatch(id,0,cancelled?Stage.CANCELLED:Stage.UNAVAILABLE,null,null,null,0,now,now.plusSeconds(60),"MOCK","No live driver or GPS data. Simulation only.");}
  public static Dispatch assigned(UUID id,Instant now){return new Dispatch(id,1,Stage.ASSIGNED,"Sample driver — not a real person","MOCK VEHICLE "+id.toString().substring(0,8),"Synthetic depot",0,now,now.plusSeconds(120),"MOCK","Illustrative supplier demo, not live GPS, an actual driver, or a valid vehicle registration. Booking pickup time is unchanged.");}
  public Dispatch advance(long expected,Instant now){if(version!=expected)Journey.conflict("Dispatch changed; refresh before advancing the demo.");if(stage==Stage.COMPLETED||stage==Stage.CANCELLED||stage==Stage.UNAVAILABLE)Journey.conflict("This dispatch is terminal or unavailable.");Stage next=Stage.values()[stage.ordinal()+1];return new Dispatch(bookingId,version+1,next,driverLabel,vehicleLabel,switch(next){case EN_ROUTE->"Synthetic route to pickup";case ARRIVED->"Synthetic pickup area";case IN_PROGRESS->"Synthetic journey segment";default->"Synthetic drop-off";},switch(next){case EN_ROUTE->20;case ARRIVED->40;case IN_PROGRESS->70;default->100;},now,now.plusSeconds(120),mode,disclaimer);}
 }
 public static List<String> validateCancellation(Booking b,Terms terms,List<String> requested,List<String> active,boolean fullyPaid,Instant now){
  if(b.status()!=Status.CONFIRMED||b.step()!=Step.DONE||!now.isBefore(b.quote().selection().cancelUntil()))Journey.conflict("Cancellation is not eligible at the current booking state or deadline.");
  if(requested==null)throw new com.travbook.shared.domain.DomainFailure(com.travbook.shared.domain.DomainFailure.Code.VALIDATION_FAILED,"Choose components.");if(requested.isEmpty()||requested.size()!=new HashSet<>(requested).size()||!active.containsAll(requested))Journey.invalid("Choose distinct currently active components.");var chosen=b.quote().selection().components().stream().map(c->c.id()).filter(requested::contains).toList();boolean full=chosen.size()==active.size();
  if(!full){if(!terms.partialCancellation())Journey.conflict("These accepted terms only support full cancellation.");if(b.quote().selection().request().kind()==Kind.HOLIDAY&&(!fullyPaid||!chosen.equals(List.of("activities"))))Journey.conflict("Only optional activities may be cancelled separately after full holiday payment.");if(b.quote().selection().request().kind()==Kind.BUS){boolean adult=false;for(int i=0;i<b.passengers().size();i++)if(active.contains(b.quote().selection().request().seats().get(i))&&!chosen.contains(b.quote().selection().request().seats().get(i))&&b.passengers().get(i).age()>=18)adult=true;if(!adult)Journey.conflict("At least one adult must remain on a partially cancelled bus booking.");}}
  return chosen;
 }
}
