package com.travbook.payments.domain.model;
import java.math.*;
import java.time.*;
import java.util.*;
public record Payment(UUID id,UUID customerId,BigDecimal amount,String currency,State state,Instant validUntil,String reference,long version) {
    public enum State { AUTHORIZED, CAPTURED, DECLINED, VOIDED }
    public enum Operation { AUTHORIZE, CAPTURE, VOID }
    public Payment {
        if(id==null||customerId==null||amount==null||amount.signum()<=0||!"INR".equals(currency)||!reference.startsWith("MOCK-"))throw new IllegalArgumentException("Invalid MOCK payment");
        amount=amount.setScale(2,RoundingMode.UNNECESSARY);
    }
    public Payment capture(boolean decline,Instant now){
        if(state!=State.AUTHORIZED)throw new IllegalStateException("Capture requires authorization");
        return decline||!validUntil.isAfter(now)?this:with(State.CAPTURED);
    }
    public Payment voidAuthorization(){if(state!=State.AUTHORIZED)throw new IllegalStateException("Only authorization can be voided");return with(State.VOIDED);}
    private Payment with(State next){return new Payment(id,customerId,amount,currency,next,validUntil,reference,version+1);}
}
