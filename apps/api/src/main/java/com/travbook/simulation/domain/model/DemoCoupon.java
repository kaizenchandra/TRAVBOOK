package com.travbook.simulation.domain.model;

import com.travbook.shared.domain.*;
import java.math.*;
import java.util.Locale;

public record DemoCoupon(String code,boolean eligible,String reason,TravelMoney discount,TravelMoney previewTotal) {
    public static DemoCoupon preview(String input,BigDecimal total) {
        if(input==null||input.isBlank()||input.length()>32)throw new DomainFailure(DomainFailure.Code.VALIDATION_FAILED,"Enter a coupon code of at most 32 characters.");
        String code=input.strip().toUpperCase(Locale.ROOT);
        String reason=code.equals("DEMOEXPIRED")?"EXPIRED_CODE":!code.equals("DEMO10")?"UNKNOWN_CODE":total.compareTo(new BigDecimal("1000"))<0?"MINIMUM_NOT_MET":"ELIGIBLE";
        BigDecimal discount=reason.equals("ELIGIBLE")?total.multiply(new BigDecimal("0.10")).min(new BigDecimal("500")).setScale(2,RoundingMode.HALF_UP):BigDecimal.ZERO;
        return new DemoCoupon(code,reason.equals("ELIGIBLE"),reason,TravelMoney.inr(discount),TravelMoney.inr(total.subtract(discount)));
    }
}
