package com.travbook.offers.application.usecase;
import com.travbook.offers.application.port.in.Coupons;
import com.travbook.offers.application.port.out.CouponStore;
import com.travbook.offers.domain.model.*;
import com.travbook.shared.domain.TravelMoney;
import java.time.*;
import java.util.*;
public final class CouponService implements Coupons {
    private final CouponStore store;private final Clock clock;
    public CouponService(CouponStore store,Clock clock){this.store=store;this.clock=clock;}
    public List<Coupon> catalog(UUID owner){return store.catalog(owner,clock.instant());}
    public CouponAward issue(UUID owner,UUID quote,String code,TravelMoney base,TravelMoney total,Instant expiry){var c=store.find(code);c.available(store.count(code,null),store.count(code,owner));return store.save(CouponAward.issue(owner,quote,c,base,total,expiry,clock.instant()));}
    public void redeem(UUID owner,UUID award,UUID reservation,UUID correlation){store.redeem(owner,award,reservation,correlation,clock.instant());}
}
