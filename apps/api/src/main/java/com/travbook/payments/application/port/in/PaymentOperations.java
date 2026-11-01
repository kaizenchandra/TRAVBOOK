package com.travbook.payments.application.port.in;
import com.travbook.payments.domain.model.Payment;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
/** Trusted internal application contract. No browser-supplied owner or amount enters this port. */
public interface PaymentOperations {
    record Command(UUID operationId,UUID paymentId,UUID customerId,Payment.Operation operation,BigDecimal amount,String currency,Instant validUntil,boolean simulateDecline) {}
    record Receipt(UUID operationId,UUID paymentId,Payment.State state,boolean accepted,String reference,BigDecimal amount,String currency,String mode) {}
    record Current(UUID paymentId,UUID owner,Payment.State state,BigDecimal amount,String currency,String mode,String reference,BigDecimal committedRefunds,BigDecimal settledRefunds){}
    default Optional<Current> current(UUID id){return Optional.empty();}
    Optional<Receipt> lookup(UUID operationId);
    Receipt execute(Command command);
}
