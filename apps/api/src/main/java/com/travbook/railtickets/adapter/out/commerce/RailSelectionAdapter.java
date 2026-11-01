package com.travbook.railtickets.adapter.out.commerce;
import com.travbook.railtickets.application.port.out.RailTicketSelection;
import com.travbook.railtickets.domain.model.RailTicket;
import com.travbook.railtickets.domain.model.RailTicket.*;
import com.travbook.rail.domain.model.Rail.*;
import com.travbook.rail.application.port.in.DiscoverTrains;
import com.travbook.shared.domain.DomainFailure;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
public final class RailSelectionAdapter implements RailTicketSelection {
 private final DiscoverTrains trains;public RailSelectionAdapter(DiscoverTrains trains){this.trains=trains;}
 public Selection reprice(UUID id,TravelClass travelClass,Quota quota,String boarding,List<Passenger> passengers){var j=trains.journey(id).journey();RailTicket.eligible(j,travelClass,quota,boarding,passengers);var check=trains.availability(id,travelClass,quota);var current=check.current();Instant expiry=current.validUntil().isBefore(j.departure().toInstant())?current.validUntil():j.departure().toInstant();var count=BigDecimal.valueOf(passengers.size());return new Selection(j,current,boarding,passengers,Money.inr(check.previous().total().amount().multiply(count)),Money.inr(current.total().amount().multiply(count)),current.observedAt(),expiry,RailTicket.POLICY);}
 public void validate(Quote q){var s=q.selection();var fresh=reprice(s.journey().id(),s.availability().travelClass(),s.availability().quota(),s.boardingStation(),s.passengers());var a=s.availability();var b=fresh.availability();if(a.differs(b)||!a.base().equals(b.base())||!a.fees().equals(b.fees())||!fresh.total().equals(s.total()))throw new DomainFailure(DomainFailure.Code.BOOKING_CONFLICT,"Rail fare or availability changed. Review and accept a new quote.");}
}
