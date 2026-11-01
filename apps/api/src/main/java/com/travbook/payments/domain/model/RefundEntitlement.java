package com.travbook.payments.domain.model;
import java.math.*;
public final class RefundEntitlement {
    private RefundEntitlement(){}
    public static void requireWithinCapture(BigDecimal requested,BigDecimal captured,BigDecimal committed,String currency){
        if(requested==null||requested.signum()<=0||!"INR".equals(currency)||requested.add(committed).compareTo(captured)>0)throw new IllegalArgumentException("Refund exceeds captured entitlement");
        requested.setScale(2,RoundingMode.UNNECESSARY);
    }
}
