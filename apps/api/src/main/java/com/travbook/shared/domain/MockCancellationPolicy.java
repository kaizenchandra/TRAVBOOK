package com.travbook.shared.domain;
import java.math.BigDecimal;
/** Closed, explicit fixture policies; never infer financial entitlement from arbitrary prose. */
public enum MockCancellationPolicy {
    FULL("MOCK-FULL-V1: Full refund of the quoted total, including simulated taxes, when cancelled before first departure. No cancellation fee."),
    NONE("MOCK-NONE-V1: No refund of the quoted total, including simulated taxes. Cancellation is allowed before first departure.");
    private final String text;MockCancellationPolicy(String text){this.text=text;}public String text(){return text;}
    public BigDecimal refund(BigDecimal total){return this==FULL?total:BigDecimal.ZERO.setScale(2);}
    public static MockCancellationPolicy parse(String text){for(var p:values())if(p.text.equals(text))return p;throw new DomainFailure(DomainFailure.Code.BOOKING_CONFLICT,"The accepted policy requires review; no automatic cancellation quote is available.");}
}
