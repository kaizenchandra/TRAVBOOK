package com.travbook.stays.application.port.out;
import com.travbook.stays.domain.model.Stay.*;
import java.util.*;
public interface StayEffects {
 record Result(boolean accepted,boolean completed,String reference){}
 default Optional<com.travbook.shared.domain.Recovery.Proof> verifiedLegacyOutcome(Booking booking){return Optional.empty();}
 Optional<Result> lookup(Booking booking,Step step);
 Result execute(Booking booking,Step step);
}
