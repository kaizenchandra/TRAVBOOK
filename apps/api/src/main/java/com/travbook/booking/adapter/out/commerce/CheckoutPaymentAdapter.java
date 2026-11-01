package com.travbook.booking.adapter.out.commerce;
import com.travbook.booking.application.port.out.CheckoutPayments;
import com.travbook.booking.domain.model.*;
import com.travbook.payments.application.port.in.PaymentOperations;
import com.travbook.payments.domain.model.Payment;
import java.util.*;
public final class CheckoutPaymentAdapter implements CheckoutPayments {
    private final PaymentOperations payments;
    public CheckoutPaymentAdapter(PaymentOperations payments){this.payments=payments;}
    private Checkout.Effect map(PaymentOperations.Receipt r){return new Checkout.Effect(r.operationId(),r.paymentId(),r.state().name(),r.accepted(),r.reference(),new FlightTerms.Money(r.amount(),r.currency()),r.mode());}
    public Optional<Checkout.Effect> lookup(UUID operationId){return payments.lookup(operationId).map(this::map);}
    public Checkout.Effect execute(Checkout c){
        var total=c.quote().terms().total();boolean decline=c.step()==Checkout.Step.AUTHORIZE&&c.scenario()==Checkout.Scenario.PAYMENT_DECLINED||c.step()==Checkout.Step.CAPTURE&&c.scenario()==Checkout.Scenario.CAPTURE_DECLINED;
        var r=payments.execute(new PaymentOperations.Command(c.operationId(),c.id(),c.quote().customerId(),Payment.Operation.valueOf(c.step().name()),total.amount(),total.currency(),c.quote().expiresAt(),decline));
        if(c.step()==Checkout.Step.CAPTURE&&c.scenario()==Checkout.Scenario.CAPTURE_RESPONSE_LOST)throw new IllegalStateException("Simulated response loss after durable capture");
        return map(r);
    }
}
