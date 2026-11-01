package com.travbook.payments.application.port.out;
import com.travbook.payments.application.port.in.PaymentOperations;
import java.util.*;
public interface PaymentStore {
    default Optional<PaymentOperations.Current> current(UUID id){return Optional.empty();}
    Optional<PaymentOperations.Receipt> lookup(UUID operationId);
    PaymentOperations.Receipt apply(PaymentOperations.Command command);
}
