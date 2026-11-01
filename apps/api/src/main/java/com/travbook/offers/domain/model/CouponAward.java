package com.travbook.offers.domain.model;
import com.travbook.shared.domain.*;
import java.time.Instant;
import java.util.UUID;
public record CouponAward(UUID id,UUID owner,UUID quoteId,Coupon policy,TravelMoney eligibleBase,TravelMoney originalTotal,TravelMoney discount,TravelMoney total,Instant issuedAt,Instant validUntil) {
    public static CouponAward issue(UUID owner,UUID quote,Coupon policy,TravelMoney base,TravelMoney total,Instant expiry,Instant now){
        var discount=policy.discount(owner,base,now);if(discount.decimal().signum()<=0)throw Coupon.invalid("This coupon does not reduce the selected fare.");
        return new CouponAward(UUID.randomUUID(),owner,quote,policy,base,total,discount,TravelMoney.inr(total.decimal().subtract(discount.decimal())),now,expiry.isBefore(policy.endsAt())?expiry:policy.endsAt());
    }
    public void redeem(Coupon current,Instant now){
        if(!current.equals(policy))throw new DomainFailure(DomainFailure.Code.BOOKING_CONFLICT,"Coupon rules changed; request and accept a new quotation.");
        if(!now.isBefore(validUntil))throw new DomainFailure(DomainFailure.Code.QUOTE_EXPIRED,"The coupon quotation expired.");
        if(!current.discount(owner,eligibleBase,now).equals(discount))throw new DomainFailure(DomainFailure.Code.BOOKING_CONFLICT,"Coupon price changed.");
    }
}
