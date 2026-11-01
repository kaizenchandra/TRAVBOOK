package com.travbook.hotels.application.port.in;
import com.travbook.hotels.domain.model.*;
import java.time.Instant;
import java.util.*;
/** Trusted provider-neutral contract; never exposed as an anonymous browser operation. */
public interface HotelReservations {
 record Command(UUID operationId,UUID reservationId,HotelCriteria criteria,List<HotelOffer> offers,Instant validUntil,boolean reject) {public Command{offers=List.copyOf(offers);}}
 record Receipt(UUID operationId,UUID reservationId,boolean accepted,String status,String reference,String mode){}
 record Current(UUID reservationId,String status,String reference,int activeUnits,String mode){}
 default Optional<Current> current(UUID id){return Optional.empty();}
 record AmendmentCommand(UUID operationId,UUID reservationId,HotelCriteria previousCriteria,List<UUID> previousOfferIds,Command replacement){}
 default Receipt amend(AmendmentCommand command){throw new UnsupportedOperationException("Amendments unavailable");}
 record Guest(int roomIndex,String givenName,String familyName,java.time.LocalDate dateOfBirth){}
 record ChangeCommand(UUID operationId,UUID reservationId,HotelCriteria previousCriteria,List<UUID> previousOfferIds,Command replacement,List<Guest> guests){public ChangeCommand{guests=List.copyOf(guests);}}
 default Receipt change(ChangeCommand command){throw new UnsupportedOperationException("Guest/funded changes unavailable");}
 Optional<Receipt> lookup(UUID operationId);
 Receipt reserve(Command command);
 Receipt cancel(UUID operationId,UUID reservationId);
}
