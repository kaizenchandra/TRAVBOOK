package com.travbook.journeys.application.port.out;
import com.travbook.journeys.domain.model.Care.*;
import java.util.*;
public interface JourneyCareSupplier {
 record Receipt(UUID operationId,UUID bookingId,List<String> components,String fingerprint,boolean accepted,String reference,String mode){}
 Optional<Receipt> lookup(UUID operationId);
 Receipt cancel(Action action);
}
