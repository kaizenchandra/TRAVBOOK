package com.travbook.railtickets.application.port.out;
import com.travbook.railtickets.domain.model.RailTicket.*;
import java.util.*;
public interface RailReservations {
 record Receipt(UUID operationId,UUID reservationId,Reservation status,boolean accepted,String reference,String mode){}
 record Current(UUID reservationId,String status,String reference,int activeUnits,String mode){}
 default Optional<Current> current(UUID id){return Optional.empty();}
 Optional<Receipt> lookup(UUID operation);
 Receipt reserve(UUID operation,UUID id,Quote quote,boolean reject);
 Receipt cancel(UUID operation,UUID id);
 record PassengerReceipt(UUID operationId,UUID reservationId,List<Integer> passengerIndexes,int remaining,String reference,String mode){public PassengerReceipt{passengerIndexes=List.copyOf(passengerIndexes);}}
 default Optional<PassengerReceipt> lookupPassengers(UUID operation){return Optional.empty();}
 default PassengerReceipt cancelPassengers(UUID operation,UUID id,List<Integer> indexes){throw new UnsupportedOperationException("Passenger cancellation unsupported");}
}
