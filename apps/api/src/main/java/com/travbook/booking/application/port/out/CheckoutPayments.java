package com.travbook.booking.application.port.out;
import com.travbook.booking.domain.model.Checkout;
import java.util.*;
public interface CheckoutPayments {
    Optional<Checkout.Effect> lookup(UUID operationId);
    Checkout.Effect execute(Checkout command);
}
