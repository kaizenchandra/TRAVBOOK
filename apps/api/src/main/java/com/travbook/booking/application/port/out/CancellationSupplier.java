package com.travbook.booking.application.port.out;
import com.travbook.booking.domain.model.*;
import java.util.*;
public interface CancellationSupplier {
    void verifyCancellable(Checkout checkout);
    Optional<Cancellation.SupplierReceipt> lookup(UUID operationId);
    Cancellation.SupplierReceipt cancel(Cancellation command);
}
