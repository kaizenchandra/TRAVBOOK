package com.travbook.booking.application.port.out;
import com.travbook.booking.domain.model.Cancellation;
import java.util.*;
public interface CancellationRefunds {
    Optional<Cancellation.RefundReceipt> lookup(UUID operationId);
    Cancellation.RefundReceipt submit(Cancellation command);
}
