package com.travbook;
import com.travbook.booking.domain.event.*;
import com.travbook.booking.domain.model.Booking.Status;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
class BookingEventDomainTest {
    final UUID aggregate=UUID.randomUUID(),correlation=UUID.randomUUID(),cause=UUID.randomUUID();
    BookingLifecycleEvent event(long version,String type,Status status,String failure){return new BookingLifecycleEvent(UUID.randomUUID(),type,1,aggregate,version,Instant.now(),correlation,cause,"booking","MOCK",new BookingLifecycleEvent.Payload(aggregate,status,failure));}
    @Test void legalHistoryIncludesRetriesAndSeparateValidationCompletion(){
        var first=event(1,"BookingCreated",Status.CREATED,null);first.requireSuccessorOf(null);
        var second=event(2,"BookingValidationStarted",Status.VALIDATING,null);second.requireSuccessorOf(first);
        var retry=event(3,"BookingValidationRetryScheduled",Status.VALIDATING,"SUPPLIER_RETRY_PENDING");retry.requireSuccessorOf(second);
        var last=event(4,"BookingAwaitingPayment",Status.AWAITING_PAYMENT,null);last.requireSuccessorOf(retry);
        assertThatThrownBy(()->event(5,"BookingAwaitingPayment",Status.AWAITING_PAYMENT,null).requireSuccessorOf(last)).hasMessage("INVALID_TRANSITION");
    }
    @Test void gapsAndEventStatusMismatchCannotAdvanceHistory(){
        var first=event(1,"BookingCreated",Status.CREATED,null);
        assertThatThrownBy(()->event(3,"BookingAwaitingPayment",Status.AWAITING_PAYMENT,null).requireSuccessorOf(first)).hasMessage("ORDER_GAP");
        assertThatThrownBy(()->event(2,"BookingAwaitingPayment",Status.AWAITING_PAYMENT,null).requireSuccessorOf(first)).hasMessage("INVALID_TRANSITION");
        assertThatThrownBy(()->event(3,"BookingAwaitingPayment",Status.MANUAL_REVIEW,null)).hasMessage("INVALID_ENVELOPE");
        assertThatThrownBy(()->event(3,"BookingValidationRejected",Status.EXPIRED,"OFFER_UNAVAILABLE")).hasMessage("INVALID_ENVELOPE");
    }
}
