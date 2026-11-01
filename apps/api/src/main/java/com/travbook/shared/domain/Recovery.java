package com.travbook.shared.domain;
import java.time.Instant;
import java.util.*;
public final class Recovery {
 private Recovery(){}
 public enum Outcome {FULFILLED,CANCELLED}
 public record Proof(UUID bookingId,Outcome outcome,String mode,String paymentReference,String supplierReference,String refundReference,String reservationState,String refundState,java.math.BigDecimal capturedAmount,java.math.BigDecimal committedRefunds,java.math.BigDecimal settledRefunds,String currency,int activeUnits,UUID captureOperation,UUID reserveOperation,UUID cancellationOperation,UUID refundOperation){public Proof{if(!"MOCK".equals(mode)||capturedAmount.signum()<=0||committedRefunds.signum()<0||settledRefunds.signum()<0||settledRefunds.compareTo(committedRefunds)>0||committedRefunds.compareTo(capturedAmount)>0||!"INR".equals(currency)||activeUnits<0)throw new IllegalArgumentException("Invalid canonical recovery proof");if(outcome==Outcome.FULFILLED&&(!java.util.Set.of("CONFIRMED","RAC","WAITLIST").contains(reservationState)||!"NONE".equals(refundState)||committedRefunds.signum()!=0||activeUnits==0)||outcome==Outcome.CANCELLED&&(!"CANCELLED".equals(reservationState)||activeUnits!=0||settledRefunds.compareTo(committedRefunds)!=0||!refundState.equals(committedRefunds.signum()==0?"NOT_DUE":"SUCCEEDED"))||"SUCCEEDED".equals(refundState)&&(refundReference==null||refundOperation==null))throw new IllegalArgumentException("Contradictory terminal proof");}}
 public enum Kind {HOTEL,RAIL,BUS,HOLIDAY,CAB}
 public enum Reason {PROVIDER_STATUS_AVAILABLE,RETRY_AFTER_OUTAGE}
 public record Action(UUID id,UUID bookingId,long version,String actor,Reason reason,Instant createdAt){}
 public record Case(UUID id,long version,String status,String payment,String reservation,String refund,String step,boolean recoverable,Instant updatedAt,List<Action> actions){public Case{actions=List.copyOf(actions);}}
}
