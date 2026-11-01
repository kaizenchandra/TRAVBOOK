package com.travbook.payments.application.usecase;
import com.travbook.payments.application.port.in.PaymentOperations;
import com.travbook.payments.application.port.out.PaymentStore;
import java.util.*;
public final class MockPaymentService implements PaymentOperations {
    private final PaymentStore store;
    public MockPaymentService(PaymentStore store){this.store=store;}
    public Optional<Current> current(UUID id){return store.current(id);}
    public Optional<Receipt> lookup(UUID operationId){return store.lookup(operationId);}
    public Receipt execute(Command c){
        if(c.operationId()==null||c.paymentId()==null||c.customerId()==null||c.operation()==null||c.amount()==null||c.amount().signum()<=0||!"INR".equals(c.currency())||c.validUntil()==null)throw new IllegalArgumentException("Invalid payment command");
        return store.apply(c);
    }
}
