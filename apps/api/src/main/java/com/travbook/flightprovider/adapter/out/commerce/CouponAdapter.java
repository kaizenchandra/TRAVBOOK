package com.travbook.flightprovider.adapter.out.commerce;
import com.travbook.flightprovider.application.port.out.CouponClaims;
import com.travbook.flightprovider.domain.model.*;
import com.travbook.offers.application.port.in.Coupons;
import com.travbook.shared.domain.TravelMoney;
import java.math.BigDecimal;
import java.util.UUID;
public final class CouponAdapter implements CouponClaims {
    private final Coupons coupons;
    public CouponAdapter(Coupons coupons){this.coupons=coupons;}
    public FlightPromotion issue(ProviderQuote q,String code){var base=q.lines().stream().map(l->l.base().decimal()).reduce(BigDecimal.ZERO,(a,b)->a.add(b));var a=coupons.issue(q.owner(),q.id(),code,TravelMoney.inr(base),q.total(),q.expiresAt());return new FlightPromotion(a.id(),a.owner(),a.quoteId(),a.policy().code(),a.policy().version(),a.originalTotal(),a.discount(),a.total(),a.validUntil(),a.policy().terms());}
    public void redeem(FlightPromotion p,UUID reservation,UUID correlation){coupons.redeem(p.owner(),p.awardId(),reservation,correlation);}
}
