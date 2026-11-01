package com.travbook.hotels.domain.model;
import java.math.*;
public record HotelMoney(BigDecimal amount,String currency) {
    public HotelMoney {
        if(amount==null||amount.signum()<0||!"INR".equals(currency))throw new IllegalArgumentException("Invalid hotel money");
        amount=amount.setScale(2,RoundingMode.UNNECESSARY);
    }
    public static HotelMoney inr(BigDecimal amount){return new HotelMoney(amount,"INR");}
}
