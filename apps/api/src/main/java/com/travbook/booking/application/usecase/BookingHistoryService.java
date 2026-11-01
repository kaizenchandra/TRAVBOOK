package com.travbook.booking.application.usecase;
import com.travbook.booking.application.port.in.*;
import com.travbook.booking.application.port.out.BookingEventInbox;
import com.travbook.booking.domain.event.BookingLifecycleEvent;
import com.travbook.customer.application.port.in.Customers;
import com.travbook.shared.domain.*;
import java.util.UUID;
public final class BookingHistoryService implements BookingHistory,RecordBookingEvent {
    private final BookingEventInbox inbox;private final Customers customers;
    public BookingHistoryService(BookingEventInbox inbox,Customers customers){this.inbox=inbox;this.customers=customers;}
    public void accept(BookingLifecycleEvent event){inbox.record(event);}
    public Page get(CustomerIdentity identity,UUID id,long afterVersion,int size){
        if(afterVersion<0||afterVersion>Integer.MAX_VALUE||size<1||size>50)throw new DomainFailure(DomainFailure.Code.VALIDATION_FAILED,"Use a nonnegative version cursor and a page size of 1–50.");
        return inbox.ownedHistory(customers.resolve(identity).id(),id,afterVersion,size);
    }
}
