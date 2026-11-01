package com.travbook.journeys.application.port.out;
import com.travbook.journeys.domain.model.Journey.*;
import java.util.*;
public interface JourneyEffects {
 record Result(boolean accepted,boolean completed,String reference){}
 default boolean recoveryEvidence(Booking booking){return false;}
 void validateHold(Booking booking);
 Optional<Result> lookup(Booking booking,Step step);
 Result execute(Booking booking,Step step);
}
