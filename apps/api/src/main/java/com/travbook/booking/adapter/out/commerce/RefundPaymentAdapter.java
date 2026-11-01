package com.travbook.booking.adapter.out.commerce;
import com.travbook.booking.application.port.out.CancellationRefunds;
import com.travbook.booking.domain.model.*;
import com.travbook.payments.application.port.in.PaymentRefunds;
import java.util.*;
public final class RefundPaymentAdapter implements CancellationRefunds {
    private final PaymentRefunds refunds;public RefundPaymentAdapter(PaymentRefunds refunds){this.refunds=refunds;}
    private Cancellation.RefundReceipt map(PaymentRefunds.Receipt r){return new Cancellation.RefundReceipt(r.operationId(),r.paymentId(),r.completed(),new FlightTerms.Money(r.amount(),r.currency()),r.reference(),r.mode());}
    public Optional<Cancellation.RefundReceipt> lookup(UUID id){return refunds.lookup(id).map(this::map);}
    public Cancellation.RefundReceipt submit(Cancellation c){
        var amount=c.quote().refundAmount();var r=refunds.submit(new PaymentRefunds.Command(c.operationId(),c.quote().checkout().id(),c.quote().owner(),c.id(),amount.amount(),amount.currency(),c.scenario()==Cancellation.Scenario.REFUND_DELAYED,c.correlationId(),c.commandId()));
        if(c.scenario()==Cancellation.Scenario.REFUND_RESPONSE_LOST)throw new IllegalStateException("Simulated refund response loss after acceptance");return map(r);
    }
}
