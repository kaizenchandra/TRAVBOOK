package com.travbook.stays.domain.model;
import com.travbook.stays.domain.model.Stay.*;
import com.travbook.hotels.domain.model.HotelMoney;
import java.time.Instant;
import java.util.*;
/** Immutable per-operation financial and supplier progress; original booking input is never rewritten. */
public record StayChange(UUID id,Quote quote,Quote previousQuote,List<Guest> guests,Instant acceptedAt,Instant createdAt,
                         UUID sourcePaymentId,HotelMoney sourceAmount,State state,Payment payment,Refund refund,
                         Scenario scenario,boolean refundDelay,boolean responseLoss,String paymentReference,String refundReference) {
 public enum State {PENDING,RESERVED,COMPLETED,REJECTED,COMPENSATED}
 public StayChange{guests=List.copyOf(guests);if(id==null||acceptedAt==null||createdAt==null)throw new IllegalArgumentException("Incomplete change");if((state==State.RESERVED||state==State.COMPLETED)&&!(quote.collectionMethod()==CollectionMethod.PAY_NOW&&payment==Payment.CAPTURED||quote.collectionMethod()==CollectionMethod.PAY_AT_PROPERTY&&payment==Payment.DEFERRED)||state==State.COMPENSATED&&refund!=Refund.SUCCEEDED||state==State.COMPLETED&&!(refund==Refund.SUCCEEDED||refund==Refund.NOT_DUE))throw new IllegalArgumentException("Unproven change states");}
 public boolean applied(){return state==State.RESERVED||state==State.COMPLETED;}
 public StayChange progress(State state,Payment payment,Refund refund,String paymentRef,String refundRef){return new StayChange(id,quote,previousQuote,guests,acceptedAt,createdAt,sourcePaymentId,sourceAmount,state,payment,refund,scenario,refundDelay,responseLoss,paymentRef,refundRef);}
 public UUID operation(Step step){return UUID.nameUUIDFromBytes((id+":"+(step==Step.CHANGE_REFUND_STATUS?Step.CHANGE_REFUND:step)).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
 public UUID refundPaymentId(){return applied()?sourcePaymentId:id;}
 public HotelMoney refundAmount(){return applied()?sourceAmount:quote.selection().total();}
}
