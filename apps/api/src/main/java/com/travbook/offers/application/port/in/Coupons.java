package com.travbook.offers.application.port.in;
import com.travbook.offers.domain.model.*;
import com.travbook.shared.domain.TravelMoney;
import java.time.Instant;
import java.util.*;
/** Trusted internal pricing contract; owner and prices must come from canonical application use cases. */
public interface Coupons {
    List<Coupon> catalog(UUID owner);
    CouponAward issue(UUID owner,UUID quoteId,String code,String scope,TravelMoney base,TravelMoney total,Instant expiry);
    void redeem(UUID owner,UUID awardId,UUID reservationId,UUID correlation);
}
