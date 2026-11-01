package com.travbook.booking.domain.model;
import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
/** Independent orchestration, payment and supplier observations; validation history is unchanged. */
public record Checkout(UUID id,UUID bookingId,BookingQuote quote,Scenario scenario,Instant createdAt,Instant updatedAt,long version,
                       Status status,Step step,Payment payment,Reservation reservation,String paymentReference,String supplierReference,
                       String failureCode,UUID correlationId,UUID commandId) {
    public enum Scenario { SUCCESS, PAYMENT_DECLINED, RESERVATION_REJECTED, RESERVATION_RESPONSE_LOST, CAPTURE_DECLINED, CAPTURE_RESPONSE_LOST }
    public enum Status { RUNNING, CONFIRMED, FAILED, REVIEW }
    public enum Step { REVALIDATE, AUTHORIZE, RESERVE, CAPTURE, CANCEL, VOID, DONE }
    public enum Payment { NOT_STARTED, AUTHORIZED, CAPTURED, DECLINED, VOIDED, UNKNOWN }
    public enum Reservation { NOT_REQUESTED, CONFIRMED, REJECTED, CANCELLED, UNKNOWN }
    public record Effect(UUID operationId,UUID checkoutId,String state,boolean accepted,String reference,FlightTerms.Money total,String mode) {}
    public Checkout {
        if(id==null||bookingId==null||scenario==null||version<1||!"MOCK".equals(quote.mode()))throw new IllegalArgumentException("Invalid checkout");
        if(status==Status.CONFIRMED&&(payment!=Payment.CAPTURED||reservation!=Reservation.CONFIRMED||step!=Step.DONE))throw new IllegalArgumentException("Confirmation requires both effects");
        if(status==Status.CONFIRMED&&(paymentReference==null||supplierReference==null||!paymentReference.startsWith("MOCK-")||!supplierReference.startsWith("MOCK-")))throw new IllegalArgumentException("Confirmation requires provider references");
        if(status==Status.FAILED&&(payment==Payment.CAPTURED||payment==Payment.UNKNOWN||reservation==Reservation.CONFIRMED||reservation==Reservation.UNKNOWN))throw new IllegalArgumentException("Uncompensated or unknown effects require review");
    }
    public UUID operationId(){return UUID.nameUUIDFromBytes((id+":"+step).getBytes(StandardCharsets.UTF_8));}
    public boolean terminal(){return status!=Status.RUNNING;}
    public Checkout verified(Instant now){requireStep(Step.REVALIDATE);return move(Step.AUTHORIZE,payment,reservation,paymentReference,supplierReference,null,now);}
    public Checkout rejected(String reason,Instant now){
        if(terminal())throw new IllegalStateException("Terminal checkout");
        if(step!=Step.REVALIDATE&&step!=Step.AUTHORIZE&&step!=Step.RESERVE)throw new IllegalStateException("Cannot reject an uncertain financial effect");
        Payment p=step==Step.AUTHORIZE?Payment.NOT_STARTED:payment;
        Reservation r=step==Step.RESERVE?Reservation.NOT_REQUESTED:reservation;
        return move(p==Payment.AUTHORIZED?Step.VOID:Step.DONE,p,r,paymentReference,supplierReference,reason,now);
    }
    public Checkout effect(Effect e,Instant now){
        if(terminal()||!e.operationId().equals(operationId())||!e.checkoutId().equals(id)||!"MOCK".equals(e.mode())||!quote.terms().total().equals(e.total())||e.reference()==null||!e.reference().startsWith("MOCK-"))throw new IllegalArgumentException("Effect does not match the command");
        return switch(step){
            case AUTHORIZE -> {
                if(e.accepted()&&!e.state().equals("AUTHORIZED")||!e.accepted()&&!e.state().equals("DECLINED"))throw new IllegalArgumentException("Unexpected authorization");
                yield move(e.accepted()?Step.RESERVE:Step.DONE,Payment.valueOf(e.state()),reservation,e.reference(),supplierReference,e.accepted()?null:"PAYMENT_DECLINED",now);
            }
            case RESERVE -> {
                if(e.accepted()&&!e.state().equals("CONFIRMED")||!e.accepted()&&!e.state().equals("REJECTED"))throw new IllegalArgumentException("Unexpected reservation");
                yield move(e.accepted()?Step.CAPTURE:Step.VOID,payment,Reservation.valueOf(e.state()),paymentReference,e.reference(),e.accepted()?null:"RESERVATION_REJECTED",now);
            }
            case CAPTURE -> {
                if(e.accepted()&&!e.state().equals("CAPTURED")||!e.accepted()&&!e.state().equals("AUTHORIZED"))throw new IllegalArgumentException("Unexpected capture");
                yield move(e.accepted()?Step.DONE:Step.CANCEL,Payment.valueOf(e.state()),reservation,e.reference(),supplierReference,e.accepted()?null:"CAPTURE_REJECTED",now);
            }
            case CANCEL -> {
                if(!e.accepted()||!e.state().equals("CANCELLED"))throw new IllegalArgumentException("Cancellation unresolved");
                yield move(Step.VOID,payment,Reservation.CANCELLED,paymentReference,e.reference(),failureCode,now);
            }
            case VOID -> {
                if(!e.accepted()||!e.state().equals("VOIDED"))throw new IllegalArgumentException("Void unresolved");
                yield move(Step.DONE,Payment.VOIDED,reservation,e.reference(),supplierReference,failureCode,now);
            }
            default -> throw new IllegalStateException("No effect in this step");
        };
    }
    public Checkout uncertain(boolean exhausted,Instant now){
        if(terminal())throw new IllegalStateException("Terminal checkout cannot be restarted");
        var p=Set.of(Step.AUTHORIZE,Step.CAPTURE,Step.VOID).contains(step)?Payment.UNKNOWN:payment;
        var r=Set.of(Step.RESERVE,Step.CANCEL).contains(step)?Reservation.UNKNOWN:reservation;
        return new Checkout(id,bookingId,quote,scenario,createdAt,now,version+1,exhausted?Status.REVIEW:Status.RUNNING,step,p,r,paymentReference,supplierReference,exhausted?"OUTCOME_UNRESOLVED":failureCode,correlationId,commandId);
    }
    public Checkout review(Instant now){return uncertain(true,now);}
    private void requireStep(Step expected){if(terminal()||step!=expected)throw new IllegalStateException("Illegal checkout transition");}
    private Checkout move(Step next,Payment p,Reservation r,String paymentRef,String supplierRef,String failure,Instant now){
        Status s=next==Step.DONE?(failure==null?Status.CONFIRMED:Status.FAILED):Status.RUNNING;
        return new Checkout(id,bookingId,quote,scenario,createdAt,now,version+1,s,next,p,r,paymentRef,supplierRef,failure,correlationId,commandId);
    }
}
