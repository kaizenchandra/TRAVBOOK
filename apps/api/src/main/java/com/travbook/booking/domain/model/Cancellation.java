package com.travbook.booking.domain.model;
import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
public record Cancellation(UUID id,CancellationQuote quote,Scenario scenario,Instant createdAt,Instant updatedAt,long version,State cancellationStatus,RefundState refundStatus,Step step,String supplierReference,String refundReference,String failureCode,UUID correlationId,UUID commandId) {
    public enum Scenario { SUCCESS,CANCEL_RESPONSE_LOST,REFUND_RESPONSE_LOST,REFUND_DELAYED }
    public enum State { PENDING,CANCELLED,REJECTED,REVIEW }
    public enum RefundState { NOT_REQUESTED,PENDING,SUCCEEDED,NOT_DUE,REVIEW }
    public enum Step { CANCEL_SUPPLIER,REFUND,DONE }
    public record SupplierReceipt(UUID operationId,UUID checkoutId,boolean cancelled,FlightTerms.Money refundAmount,FlightTerms.Money retainedAmount,String reference,String mode) {}
    public record RefundReceipt(UUID operationId,UUID paymentId,boolean completed,FlightTerms.Money amount,String reference,String mode) {}
    public Cancellation {
        if(id==null||quote==null||scenario==null||version<1)throw new IllegalArgumentException("Invalid cancellation");
        if(refundStatus!=RefundState.NOT_REQUESTED&&cancellationStatus!=State.CANCELLED)throw new IllegalArgumentException("Refund requires verified cancellation");
        if(step==Step.REFUND&&(cancellationStatus!=State.CANCELLED||refundStatus==RefundState.NOT_REQUESTED||quote.refundAmount().amount().signum()<=0))throw new IllegalArgumentException("Refund step requires positive cancelled entitlement");
        if(refundStatus==RefundState.SUCCEEDED&&(quote.refundAmount().amount().signum()<=0||refundReference==null))throw new IllegalArgumentException("Missing refund effect");
        if(refundStatus==RefundState.SUCCEEDED&&step!=Step.DONE||refundStatus==RefundState.NOT_DUE&&(step!=Step.DONE||quote.refundAmount().amount().signum()!=0))throw new IllegalArgumentException("Invalid terminal refund state");
        if(cancellationStatus==State.CANCELLED&&(supplierReference==null||!supplierReference.startsWith("MOCK-")))throw new IllegalArgumentException("Verified cancellation reference required");
        if(cancellationStatus==State.REJECTED&&step!=Step.DONE||cancellationStatus==State.PENDING&&step!=Step.CANCEL_SUPPLIER)throw new IllegalArgumentException("Invalid cancellation step");
    }
    public UUID operationId(){return UUID.nameUUIDFromBytes((id+":"+step).getBytes(StandardCharsets.UTF_8));}
    public boolean terminal(){return step==Step.DONE||cancellationStatus==State.REVIEW||refundStatus==RefundState.REVIEW;}
    public boolean blocksDocuments(){return cancellationStatus!=State.REJECTED;}
    public Cancellation supplier(SupplierReceipt r,Instant now){
        requireStep(Step.CANCEL_SUPPLIER);
        if(!r.operationId().equals(operationId())||!r.checkoutId().equals(quote.checkout().id())||!"MOCK".equals(r.mode())||!r.refundAmount().equals(quote.refundAmount())||!r.retainedAmount().equals(quote.retainedAmount())||r.reference()==null||!r.reference().startsWith("MOCK-"))throw new IllegalArgumentException("Cancellation receipt mismatch");
        if(!r.cancelled())return reject("CANCELLATION_REJECTED",now);
        boolean due=quote.refundAmount().amount().signum()>0;
        return next(State.CANCELLED,due?RefundState.PENDING:RefundState.NOT_DUE,due?Step.REFUND:Step.DONE,r.reference(),null,null,now);
    }
    public Cancellation refund(RefundReceipt r,boolean reviewIfPending,Instant now){
        requireStep(Step.REFUND);
        if(!r.operationId().equals(operationId())||!r.paymentId().equals(quote.checkout().id())||!"MOCK".equals(r.mode())||!r.amount().equals(quote.refundAmount())||r.reference()==null||!r.reference().startsWith("MOCK-"))throw new IllegalArgumentException("Refund receipt mismatch");
        return next(State.CANCELLED,r.completed()?RefundState.SUCCEEDED:reviewIfPending?RefundState.REVIEW:RefundState.PENDING,r.completed()?Step.DONE:Step.REFUND,supplierReference,r.reference(),!r.completed()&&reviewIfPending?"OUTCOME_UNRESOLVED":null,now);
    }
    public Cancellation reject(String code,Instant now){requireStep(Step.CANCEL_SUPPLIER);return next(State.REJECTED,RefundState.NOT_REQUESTED,Step.DONE,null,null,code,now);}
    public Cancellation uncertain(boolean exhausted,Instant now){
        if(terminal())throw new IllegalStateException("Terminal cancellation");
        return next(step==Step.CANCEL_SUPPLIER&&exhausted?State.REVIEW:cancellationStatus,step==Step.REFUND&&exhausted?RefundState.REVIEW:refundStatus,step,supplierReference,refundReference,exhausted?"OUTCOME_UNRESOLVED":"OUTCOME_PENDING",now);
    }
    private void requireStep(Step expected){if(terminal()||step!=expected)throw new IllegalStateException("Illegal cancellation transition");}
    private Cancellation next(State c,RefundState r,Step s,String sr,String rr,String code,Instant now){return new Cancellation(id,quote,scenario,createdAt,now,version+1,c,r,s,sr,rr,code,correlationId,commandId);}
}
