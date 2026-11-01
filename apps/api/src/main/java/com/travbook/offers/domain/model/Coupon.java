package com.travbook.offers.domain.model;
import com.travbook.shared.domain.*;
import java.time.Instant;
import java.math.*;
import java.util.*;
public record Coupon(String code,int version,String scope,int percent,TravelMoney maximumDiscount,TravelMoney minimumBase,
    Instant startsAt,Instant endsAt,int globalLimit,int customerLimit,UUID eligibleCustomer,boolean enabled) {
    public Coupon {if(code==null||!code.matches("[A-Z0-9]{3,24}")||version<1||!"FLIGHT_EXTRAS".equals(scope)||percent<1||percent>100||globalLimit<1||customerLimit<1||customerLimit>globalLimit||!endsAt.isAfter(startsAt))throw new IllegalArgumentException("Invalid coupon policy");}
    public String terms(){return "MOCK flight seats-and-extras bookings only. "+percent+"% off base fare, maximum INR "+maximumDiscount.amount()+"; minimum base INR "+minimumBase.amount()+". Taxes and extras are unchanged. "+customerLimit+" reservation(s) per customer; "+globalLimit+" total. Usage is consumed at reservation and is not restored by cancellation or payment failure.";}
    public TravelMoney discount(UUID owner,TravelMoney base,Instant now){
        if(!enabled||now.isBefore(startsAt)||!now.isBefore(endsAt))throw invalid("This coupon is not currently active.");
        if(eligibleCustomer!=null&&!eligibleCustomer.equals(owner))throw invalid("This coupon is not available for your account.");
        if(base.decimal().compareTo(minimumBase.decimal())<0)throw invalid("The base fare does not meet this coupon's minimum.");
        return TravelMoney.inr(base.decimal().multiply(BigDecimal.valueOf(percent)).divide(BigDecimal.valueOf(100),2,RoundingMode.HALF_UP).min(maximumDiscount.decimal()).min(base.decimal()));
    }
    public void available(long globalUses,long customerUses){if(globalUses>=globalLimit||customerUses>=customerLimit)throw new DomainFailure(DomainFailure.Code.BOOKING_CONFLICT,"This coupon's usage limit has been reached. Request a new quote without it.");}
    public static DomainFailure invalid(String message){return new DomainFailure(DomainFailure.Code.VALIDATION_FAILED,message);}
}
