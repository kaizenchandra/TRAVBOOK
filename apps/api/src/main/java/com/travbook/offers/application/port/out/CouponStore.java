package com.travbook.offers.application.port.out;
import com.travbook.offers.domain.model.*;
import java.time.Instant;
import java.util.*;
public interface CouponStore {
    List<Coupon> catalog(UUID owner,Instant now);
    Coupon find(String code);
    long count(String code,UUID owner);
    CouponAward save(CouponAward award);
    void redeem(UUID owner,UUID awardId,UUID reservationId,UUID correlation,Instant now);
}
