package com.travbook.railtickets.application.port.out;
import com.travbook.railtickets.domain.model.RailTicket.*;
import com.travbook.rail.domain.model.Rail.*;
import java.util.*;
public interface RailTicketSelection {Selection reprice(UUID journey,TravelClass travelClass,Quota quota,String boarding,List<Passenger> passengers);void validate(Quote quote);}
