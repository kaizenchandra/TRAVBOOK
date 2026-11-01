package com.travbook.payments.application.port.in;
import java.math.BigDecimal;
import java.util.*;
public interface PaymentRefunds {
    record Command(UUID operationId,UUID paymentId,UUID customerId,UUID entitlementId,BigDecimal amount,String currency,boolean simulateDelay,UUID correlationId,UUID causationId) {
        public Command {if(amount==null)throw new IllegalArgumentException("Refund amount required");amount=amount.setScale(2,java.math.RoundingMode.UNNECESSARY);}
    }
    record Receipt(UUID operationId,UUID paymentId,BigDecimal amount,String currency,String reference,boolean completed,String mode) {}
    Optional<Receipt> lookup(UUID operationId);
    Receipt submit(Command command);
    boolean settleNext();
}
