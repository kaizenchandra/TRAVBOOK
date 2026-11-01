package com.travbook.shared.domain;
import java.math.*;
/** Decimal-string transport, exact BigDecimal arithmetic; never a browser-authoritative total. */
public record TravelMoney(String amount,String currency) {
 public TravelMoney{var value=new BigDecimal(amount).setScale(2,RoundingMode.UNNECESSARY);if(value.signum()<0||value.compareTo(new BigDecimal("99999999"))>0||!"INR".equals(currency))throw new IllegalArgumentException("Invalid money");amount=value.toPlainString();}
 public static TravelMoney inr(BigDecimal value){return new TravelMoney(value.setScale(2,RoundingMode.HALF_UP).toPlainString(),"INR");}
 public static TravelMoney inr(long value){return inr(BigDecimal.valueOf(value));}
 public BigDecimal decimal(){return new BigDecimal(amount);}
}
