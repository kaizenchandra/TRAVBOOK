package com.travbook.payments.application.port.out;
import com.travbook.payments.application.port.in.PaymentRefunds;
import java.util.*;
public interface RefundStore {
    Optional<PaymentRefunds.Receipt> lookup(UUID id);
    PaymentRefunds.Receipt submit(PaymentRefunds.Command command);
    boolean settleNext();
}
