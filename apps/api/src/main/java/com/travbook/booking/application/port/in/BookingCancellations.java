package com.travbook.booking.application.port.in;
import com.travbook.booking.domain.model.*;
import com.travbook.shared.domain.CustomerIdentity;
import java.util.*;
public interface BookingCancellations {
    record View(Cancellation cancellation,int attempts) {}
    CancellationQuote quote(CustomerIdentity identity,UUID bookingId,String key,UUID correlationId);
    Cancellation request(CustomerIdentity identity,UUID bookingId,UUID quoteId,int version,boolean confirmed,Cancellation.Scenario scenario,String key,UUID correlationId);
    View get(CustomerIdentity identity,UUID bookingId);
    List<View> refunds(CustomerIdentity identity,UUID bookingId);
    void requireLegacyReadable(CustomerIdentity identity,UUID bookingId);
}
