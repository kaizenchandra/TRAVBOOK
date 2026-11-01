package com.travbook.search.domain.model;
import java.math.BigDecimal;
import java.math.RoundingMode;
public record Money(BigDecimal amount, String currency) {
    public Money {
        if(amount == null || amount.signum() < 0 || !"INR".equals(currency)) throw new IllegalArgumentException("Invalid money");
        amount = amount.setScale(2, RoundingMode.UNNECESSARY);
    }
    public static Money inr(BigDecimal amount) { return new Money(amount,"INR"); }
    public Money plus(Money other) {
        if(!currency.equals(other.currency)) throw new IllegalArgumentException("Currency mismatch");
        return new Money(amount.add(other.amount),currency);
    }
}

