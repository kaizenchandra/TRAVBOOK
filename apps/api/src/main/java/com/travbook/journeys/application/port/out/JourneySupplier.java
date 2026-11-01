package com.travbook.journeys.application.port.out;
import com.travbook.journeys.domain.model.Journey.*;
import java.util.*;
public interface JourneySupplier {
 record Receipt(UUID operationId,UUID bookingId,Step step,String fingerprint,boolean accepted,String reference,String mode){}
 record Current(UUID bookingId,String status,String resource,int confirmedComponents,int activeSeats,String mode){}
 Optional<Current> current(UUID bookingId);
 void validateHold(Booking booking);
 Optional<Receipt> lookup(UUID operation);
 Receipt execute(Booking booking,Step step);
}
