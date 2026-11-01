package com.travbook.booking.application.port.in;
import com.travbook.booking.domain.model.*;
import com.travbook.shared.domain.CustomerIdentity;
import java.util.*;
public interface BookingCheckouts {
    record View(Checkout checkout,int attempts) {}
    Checkout start(CustomerIdentity identity,UUID bookingId,Checkout.Scenario scenario,boolean consent,String key,UUID correlationId);
    View get(CustomerIdentity identity,UUID bookingId);
    void requireLegacyReadable(CustomerIdentity identity,UUID bookingId);
}
