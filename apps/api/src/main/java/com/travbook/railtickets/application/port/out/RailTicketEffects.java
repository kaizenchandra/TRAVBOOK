package com.travbook.railtickets.application.port.out;
import com.travbook.railtickets.domain.model.RailTicket.*;
import java.util.*;
public interface RailTicketEffects {
 record Result(boolean accepted,boolean completed,String reference,Reservation reservation){}
 default Optional<com.travbook.shared.domain.Recovery.Proof> verifiedLegacyOutcome(Booking booking){return Optional.empty();}
 Optional<Result> lookup(Booking booking,Step step);
 Result execute(Booking booking,Step step);
}
