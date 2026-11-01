package com.travbook.payments.application.usecase;
import com.travbook.payments.application.port.in.PaymentRefunds;
import com.travbook.payments.application.port.out.RefundStore;
import java.util.*;
public final class MockRefundService implements PaymentRefunds {
    private final RefundStore store;public MockRefundService(RefundStore store){this.store=store;}
    public Optional<Receipt> lookup(UUID id){return store.lookup(id);}
    public Receipt submit(Command c){if(c.operationId()==null||c.paymentId()==null||c.customerId()==null||c.entitlementId()==null||c.correlationId()==null||c.causationId()==null)throw new IllegalArgumentException("Refund identity required");return store.submit(c);}
    public boolean settleNext(){return store.settleNext();}
}
