package com.travbook.flightprovider.domain.model;

import java.time.Instant;
import java.util.*;
import static com.travbook.flightprovider.domain.model.FlightFixture.*;

/** Independent provider reservation, payment, ticket and refund states; no platform booking is changed. */
public record ProviderReservation(UUID id,UUID owner,ProviderQuote quote,long version,ReservationState reservation,
    PaymentState payment,TicketState ticket,RefundState refund,List<String> checkedIn,int paymentAttempts,String reference,
    String paymentReference,String ticketReference,String refundReference,Instant createdAt,Instant updatedAt) {
    public enum ReservationState { RESERVED, CANCELLED }
    public enum PaymentState { UNPAID, DECLINED, CAPTURED, REFUNDED }
    public enum TicketState { NOT_ISSUED, ISSUED, REJECTED, VOID }
    public enum RefundState { NONE, PENDING, COMPLETED }
    public enum Action { PAY_SUCCESS, PAY_DECLINE, TICKET_ISSUE, TICKET_REJECT, CHECK_IN, CANCEL, REFUND }
    public ProviderReservation {checkedIn=List.copyOf(checkedIn);}
    public static ProviderReservation reserve(ProviderQuote quote,Instant now) {
        var id=UUID.randomUUID();return new ProviderReservation(id,quote.owner(),quote,1,ReservationState.RESERVED,PaymentState.UNPAID,TicketState.NOT_ISSUED,RefundState.NONE,List.of(),0,"MOCK-RES-"+id,null,null,null,now,now);
    }
    public ProviderReservation apply(Action action,List<String> passengers,boolean consent,Instant now) {
        consent(consent);var rs=reservation;var ps=payment;var ts=ticket;var rf=refund;var checked=checkedIn;var attempts=paymentAttempts;
        String payRef=paymentReference,ticketRef=ticketReference,refundRef=refundReference;
        if(action!=Action.REFUND) {if(reservation!=ReservationState.RESERVED)throw conflict("Reservation is cancelled.");bookable(quote.flight(),now);}
        switch(action) {
            case PAY_SUCCESS,PAY_DECLINE -> {
                if(payment!=PaymentState.UNPAID && payment!=PaymentState.DECLINED)throw conflict("This reservation already has a successful simulated capture.");
                if(attempts>=10)throw conflict("Ten payment attempts exhausted; cancel this reservation.");
                attempts++;ps=action==Action.PAY_SUCCESS?PaymentState.CAPTURED:PaymentState.DECLINED;
                if(ps==PaymentState.CAPTURED)payRef="MOCK-PAY-"+id;
            }
            case TICKET_ISSUE,TICKET_REJECT -> {
                if(payment!=PaymentState.CAPTURED || ticket!=TicketState.NOT_ISSUED)throw conflict("Ticketing requires captured payment and no prior ticket outcome.");
                ts=action==Action.TICKET_ISSUE?TicketState.ISSUED:TicketState.REJECTED;
                if(ts==TicketState.ISSUED)ticketRef="MOCK-TICKET-"+id;
            }
            case CHECK_IN -> {
                if(ticket!=TicketState.ISSUED || payment!=PaymentState.CAPTURED)throw conflict("Check-in requires an issued fictional ticket and captured payment.");
                if(now.isBefore(quote.flight().checkInOpens()))throw conflict("The fictional check-in window opens 24 hours before departure.");
                if(passengers==null || passengers.isEmpty() || passengers.size()>6 || new HashSet<>(passengers).size()!=passengers.size())throw invalid("Select unique passenger slots for check-in.");
                var allowed=quote.selections().stream().map(s->s.passengerId()).toList();
                if(!allowed.containsAll(passengers))throw invalid("Passenger slot is not in this reservation.");
                if(passengers.stream().anyMatch(checkedIn::contains))throw conflict("Passenger is already checked in; replay the original key or retrieve the document.");
                var next=new TreeSet<>(checkedIn);next.addAll(passengers);checked=List.copyOf(next);
            }
            case CANCEL -> {
                if(!ProviderQuote.POLICY.equals(quote.cancellationPolicy()))throw conflict("The stored cancellation policy requires review.");
                if(!checkedIn.isEmpty())throw conflict("This fixture does not support cancellation after check-in.");
                rs=ReservationState.CANCELLED;ts=TicketState.VOID;
                if(payment==PaymentState.CAPTURED)rf=RefundState.PENDING;
            }
            case REFUND -> {
                if(reservation!=ReservationState.CANCELLED || refund!=RefundState.PENDING || payment!=PaymentState.CAPTURED)throw conflict("A cancelled, captured reservation with a pending refund is required.");
                ps=PaymentState.REFUNDED;rf=RefundState.COMPLETED;refundRef="MOCK-REFUND-"+id;
            }
        }
        return new ProviderReservation(id,owner,quote,version+1,rs,ps,ts,rf,checked,attempts,reference,payRef,ticketRef,refundRef,createdAt,now);
    }
    public record Document(String passengerId,String type,String reference,String seat,boolean validForTravel,String notice) {}
    public List<Document> documents() {
        if(reservation!=ReservationState.RESERVED || ticket!=TicketState.ISSUED || payment!=PaymentState.CAPTURED)throw conflict("No issued fictional travel document is available.");
        var result=new ArrayList<Document>();
        for(var s:quote.selections()) {
            result.add(new Document(s.passengerId(),"MOCK_TICKET",ticketReference+"-"+s.passengerId(),s.seat(),false,NOTICE));
            if(checkedIn.contains(s.passengerId()))result.add(new Document(s.passengerId(),"MOCK_BOARDING_PASS","MOCK-BP-"+id+"-"+s.passengerId(),s.seat(),false,NOTICE));
        }
        return List.copyOf(result);
    }
}
