package com.travbook.journeys.domain.model;

import com.travbook.shared.domain.*;
import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import static com.travbook.shared.domain.DomainFailure.Code.*;

/** Independent state machines for prepaid, single-provider travel orders. */
public final class Journey {
 private Journey(){}
 public enum Kind {BUS,HOLIDAY,CAB}
 public enum Status {HOLD_PENDING,AWAITING_PAYMENT,PAYMENT_PENDING,RESERVATION_PENDING,CONFIRMED,CANCELLATION_PENDING,CANCELLED,FAILED,REVIEW}
 public enum Payment {NONE,AUTHORIZED,CAPTURED,DECLINED,VOIDED}
 public enum Reservation {NONE,HELD,CONFIRMED,REJECTED,RELEASED,CANCELLED}
 public enum Refund {NONE,PENDING,SUCCEEDED}
 public enum Step {HOLD,WAIT_PAYMENT,VALIDATE_PAYMENT,AUTHORIZE,CAPTURE,RESERVE,VOID,RELEASE,CANCEL,REFUND,REFUND_STATUS,DONE}
 public enum Scenario {NORMAL,AUTH_DECLINED,CAPTURE_DECLINED,RESERVATION_REJECTED,RESPONSE_LOST}
 public record Request(Kind kind,UUID sourceId,UUID offerId,List<String> seats,String boardingId,String droppingId){
  public Request {if(kind==null||sourceId==null||seats==null)invalid("Select a source and service.");seats=List.copyOf(seats);if(kind==Kind.BUS){if(offerId==null||seats.isEmpty()||seats.size()>6||new HashSet<>(seats).size()!=seats.size()||seats.stream().anyMatch(s->!s.matches("[LU][1-8]"))||boardingId==null||droppingId==null||!boardingId.matches("[A-Z]+-(CENTRE|TERMINAL)")||!droppingId.matches("[A-Z]+-(CENTRE|TERMINAL)"))invalid("Select valid seats and boarding points.");}else if(!seats.isEmpty()||boardingId!=null||droppingId!=null||kind==Kind.CAB&&offerId==null||kind==Kind.HOLIDAY&&offerId!=null)invalid("Selection fields do not match the service.");}
 }
 public record Component(String id,String description,TravelMoney amount){}
 public record Selection(Request request,String supplier,String resource,String title,Instant departureAt,String timezone,List<Integer> passengerAges,List<String> details,List<Component> components,TravelMoney total,Instant validUntil,Instant cancelUntil,String cancellationPolicy){
  public Selection {passengerAges=List.copyOf(passengerAges);details=List.copyOf(details);components=List.copyOf(components);var sum=java.math.BigDecimal.ZERO;for(var c:components)sum=sum.add(c.amount().decimal());if(components.isEmpty()||sum.compareTo(total.decimal())!=0)invalid("Component amounts must match the total.");}
 }
 public record Quote(UUID id,UUID owner,int version,Selection selection,Instant createdAt,Instant expiresAt,String mode){
  public Quote {if((version!=1&&version!=2)||!"MOCK".equals(mode)||!expiresAt.isAfter(createdAt))invalid("Invalid immutable quote.");}
  public void valid(Instant now){if(!now.isBefore(expiresAt))throw new DomainFailure(QUOTE_EXPIRED,"This quote or hold expired. Obtain fresh terms.");}
 }
 public record Passenger(String name,int age){public Passenger {if(name==null)throw new DomainFailure(VALIDATION_FAILED,"Passenger name required.");name=name.strip();if(name.isEmpty()||name.length()>80||name.chars().anyMatch(Character::isISOControl)||age<0||age>120)invalid("Invalid passenger details.");}}
 public record CancellationQuote(UUID id,UUID bookingId,long bookingVersion,TravelMoney amount,List<Component> components,Instant expiresAt,String mode){}
 public record Booking(UUID id,long version,Quote quote,Instant acceptedAt,List<Passenger> passengers,String email,Scenario scenario,Status status,Payment payment,Reservation reservation,Refund refund,Step step,String supplierReference,String refundReference,String failure,CancellationQuote cancellation,boolean refundDelay,UUID correlationId,Instant createdAt,Instant updatedAt,String mode){
  public Booking {passengers=List.copyOf(passengers);if(!"MOCK".equals(mode)||!quote.mode().equals(mode))invalid("Integration mode mismatch.");}
  public static Booking create(UUID id,Quote q,Instant acceptedAt,List<Passenger> passengers,String email,Scenario scenario,UUID correlation,Instant now){
   q.valid(now);if(acceptedAt==null||acceptedAt.isAfter(now))invalid("Accepted quotation required.");if(passengers==null)throw new DomainFailure(VALIDATION_FAILED,"Passengers required.");if(passengers.size()!=q.selection().passengerAges().size()||passengers.stream().noneMatch(p->p.age()>=18))invalid("Provide every passenger and at least one adult aged 18 or older.");
   for(int i=0;i<passengers.size();i++){int expected=q.selection().passengerAges().get(i);if(expected>=0&&expected!=passengers.get(i).age()||expected==-1&&passengers.get(i).age()<12)invalid("Passenger ages must match the quoted party in order.");}
   if(email==null)throw new DomainFailure(VALIDATION_FAILED,"Contact email required.");if(email.length()>160||!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")||scenario==null)invalid("Valid contact email and simulation scenario required.");
   boolean bus=q.selection().request().kind()==Kind.BUS;
   return new Booking(id,1,q,acceptedAt,passengers,email.strip().toLowerCase(Locale.ROOT),scenario,bus?Status.HOLD_PENDING:Status.AWAITING_PAYMENT,Payment.NONE,Reservation.NONE,Refund.NONE,bus?Step.HOLD:Step.WAIT_PAYMENT,null,null,null,null,false,correlation,now,now,"MOCK");
  }
  public String supplierFingerprint(){var value=new StringBuilder(quote.toString());for(var p:passengers)value.append(':').append(p.name().length()).append(':').append(p.name()).append(':').append(p.age());try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.toString().getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
  public UUID operation(Step step){return UUID.nameUUIDFromBytes((id+":"+step).getBytes(StandardCharsets.UTF_8));}
  private Booking next(Status s,Payment p,Reservation r,Refund f,Step st,String ref,String rr,String fail,CancellationQuote c,boolean delay,Instant now){return new Booking(id,version+1,quote,acceptedAt,passengers,email,scenario,s,p,r,f,st,ref,rr,fail,c,delay,correlationId,createdAt,now,mode);}
  public Booking pay(Instant now){if(status!=Status.AWAITING_PAYMENT||step!=Step.WAIT_PAYMENT)conflict("Booking is not awaiting payment.");quote.valid(now);return next(Status.PAYMENT_PENDING,payment,reservation,refund,Step.VALIDATE_PAYMENT,supplierReference,refundReference,null,cancellation,false,now);}
  public Booking validated(Instant now){quote.valid(now);return next(status,payment,reservation,refund,Step.AUTHORIZE,supplierReference,refundReference,failure,cancellation,refundDelay,now);}
  public Booking failedBeforePayment(String reason,Instant now){return next(Status.FAILED,payment,reservation,refund,payment==Payment.AUTHORIZED?Step.VOID:Step.RELEASE,supplierReference,refundReference,reason,cancellation,refundDelay,now);}
  public Booking result(boolean accepted,boolean completed,String reference,Instant now){return switch(step){
   case HOLD -> next(accepted?Status.AWAITING_PAYMENT:Status.FAILED,payment,accepted?Reservation.HELD:Reservation.REJECTED,refund,accepted?Step.WAIT_PAYMENT:Step.DONE,accepted?reference:null,null,accepted?null:"SEAT_UNAVAILABLE",cancellation,false,now);
   case AUTHORIZE -> next(accepted?status:Status.FAILED,accepted?Payment.AUTHORIZED:Payment.DECLINED,reservation,refund,accepted?Step.CAPTURE:Step.RELEASE,supplierReference,null,accepted?null:"PAYMENT_DECLINED",cancellation,false,now);
   case CAPTURE -> next(accepted?Status.RESERVATION_PENDING:Status.FAILED,accepted?Payment.CAPTURED:payment,reservation,refund,accepted?Step.RESERVE:Step.VOID,supplierReference,null,accepted?null:"CAPTURE_DECLINED",cancellation,false,now);
   case RESERVE -> next(accepted?Status.CONFIRMED:Status.FAILED,payment,accepted?Reservation.CONFIRMED:Reservation.REJECTED,refund,accepted?Step.DONE:Step.REFUND,accepted?reference:null,null,accepted?null:"RESERVATION_REJECTED",cancellation,false,now);
   case VOID -> {if(!accepted)throw new IllegalStateException("Unknown void");yield next(Status.FAILED,Payment.VOIDED,reservation,refund,Step.RELEASE,supplierReference,null,failure,cancellation,false,now);}
   case RELEASE -> {if(!accepted)throw new IllegalStateException("Unknown release");yield next(Status.FAILED,payment,Reservation.RELEASED,refund,Step.DONE,supplierReference,refundReference,failure,cancellation,false,now);}
   case CANCEL -> {if(!accepted)throw new IllegalStateException("Unknown cancellation");yield next(Status.CANCELLED,payment,Reservation.CANCELLED,refund,Step.REFUND,supplierReference,null,null,cancellation,refundDelay,now);}
   case REFUND,REFUND_STATUS -> next(status,payment,reservation,completed?Refund.SUCCEEDED:Refund.PENDING,completed?(status==Status.FAILED?Step.RELEASE:Step.DONE):Step.REFUND_STATUS,supplierReference,reference,failure,cancellation,refundDelay,now);
   default -> throw new IllegalStateException("Unexpected effect");};}
  public CancellationQuote cancellationQuote(UUID id,Instant now){if(status!=Status.CONFIRMED||step!=Step.DONE||payment!=Payment.CAPTURED||reservation!=Reservation.CONFIRMED)conflict("Only a confirmed paid booking can be cancelled.");if(!now.isBefore(quote.selection().cancelUntil()))conflict("The sample supplier cancellation deadline has passed.");Instant expires=now.plusSeconds(120).isBefore(quote.selection().cancelUntil())?now.plusSeconds(120):quote.selection().cancelUntil();return new CancellationQuote(id,this.id,version,quote.selection().total(),quote.selection().components(),expires,"MOCK");}
  public Booking cancel(CancellationQuote c,boolean delay,Instant now){cancellationQuote(c.id(),now);if(!c.bookingId().equals(id)||c.bookingVersion()!=version||!now.isBefore(c.expiresAt())||!c.amount().equals(quote.selection().total()))conflict("Cancellation quote expired or booking changed.");return next(Status.CANCELLATION_PENDING,payment,reservation,refund,Step.CANCEL,supplierReference,null,null,c,delay,now);}
  public Booking careState(Status state,Refund refundState,Instant now){if(step!=Step.DONE||payment!=Payment.CAPTURED||!Set.of(Status.CONFIRMED,Status.CANCELLATION_PENDING,Status.CANCELLED).contains(status)||!Set.of(Status.CONFIRMED,Status.CANCELLATION_PENDING,Status.CANCELLED).contains(state)||status==Status.CANCELLED&&state!=Status.CANCELLED)conflict("Illegal aftercare booking transition.");return next(state,payment,state==Status.CANCELLED?Reservation.CANCELLED:reservation,refundState,step,supplierReference,refundReference,failure,cancellation,refundDelay,now);}
  public Booking review(Instant now){return next(Status.REVIEW,payment,reservation,refund,step,supplierReference,refundReference,"OUTCOME_REQUIRES_REVIEW",cancellation,refundDelay,now);}
  public Booking resumeCare(Booking previous,Instant now){if(previous==null)throw new DomainFailure(BOOKING_CONFLICT,"Missing care checkpoint.");if(status!=Status.REVIEW||!previous.review(updatedAt).equals(this))conflict("Care checkpoint does not match the review.");return next(previous.status(),payment,reservation,refund,step,supplierReference,refundReference,previous.failure(),cancellation,refundDelay,now);}
  public Booking resumeFrom(Booking previous,Instant now){
   if(previous==null)throw new DomainFailure(BOOKING_CONFLICT,"Recovery requires an immutable checkpoint.");
   if(status!=Status.REVIEW||previous.status()==Status.REVIEW||previous.step()==Step.DONE||!previous.review(updatedAt).equals(this))conflict("Recovery requires the exact saved pre-review state.");
   return new Booking(id,version+1,quote,acceptedAt,passengers,email,scenario,previous.status(),payment,reservation,refund,step,supplierReference,refundReference,previous.failure(),cancellation,refundDelay,correlationId,createdAt,now,mode);
  }
  public void documentAllowed(){if(status!=Status.CONFIRMED||reservation!=Reservation.CONFIRMED||payment!=Payment.CAPTURED||supplierReference==null||!supplierReference.equals("MOCK-JOURNEY-"+id))conflict("A document requires a currently confirmed supplier reservation.");}
 }
 public record Document(UUID id,UUID bookingId,Instant createdAt,String sha256,String content,String mode){}
 public static void invalid(String message){throw new DomainFailure(VALIDATION_FAILED,message);}
 public static void conflict(String message){throw new DomainFailure(BOOKING_CONFLICT,message);}
}
