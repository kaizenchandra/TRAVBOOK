package com.travbook.pricing.domain.model;
import java.math.*;
public record Price(BigDecimal amount,String currency) {
    public Price {
        if(amount==null || amount.signum()<0 || !"INR".equals(currency)) throw new IllegalArgumentException("Invalid quote price");
        amount=amount.setScale(2,RoundingMode.UNNECESSARY);
    }
    public Price plus(Price other) {
        if(!currency.equals(other.currency)) throw new IllegalArgumentException("Currency mismatch");
        return new Price(amount.add(other.amount),currency);
    }
}

