package com.travbook.booking.domain.event;
import com.travbook.booking.domain.model.Booking.Status;
import java.time.Instant;
import java.util.*;
import static com.travbook.booking.domain.event.EventRejected.Code.*;
/** Version 1 envelope. No transport, persistence, supplier or traveller types enter this contract. */
public record BookingLifecycleEvent(UUID eventId,String eventType,int schemaVersion,UUID aggregateId,long aggregateVersion,
                                    Instant occurredAt,UUID correlationId,UUID causationId,String producer,String mode,Payload payload) {
    public record Payload(UUID bookingId,Status status,String failureCode) {}
    public BookingLifecycleEvent {
        if(eventId==null||aggregateId==null||occurredAt==null||correlationId==null||causationId==null||payload==null||
            !aggregateId.equals(payload.bookingId())||schemaVersion!=1||aggregateVersion<1||aggregateVersion>Integer.MAX_VALUE||
            !"booking".equals(producer)||!"MOCK".equals(mode)||eventType==null||payload.status()==null)throw new EventRejected(INVALID_ENVELOPE);
        String failure=payload.failureCode();Status status=payload.status();
        boolean valid=switch(eventType) {
            case "BookingCreated" -> aggregateVersion==1&&status==Status.CREATED&&failure==null;
            case "BookingValidationStarted" -> aggregateVersion==2&&status==Status.VALIDATING&&failure==null;
            case "BookingAwaitingPayment" -> status==Status.AWAITING_PAYMENT&&failure==null;
            case "BookingValidationRetryScheduled" -> status==Status.VALIDATING&&"SUPPLIER_RETRY_PENDING".equals(failure);
            case "BookingManualReviewRequired" -> status==Status.MANUAL_REVIEW&&"VALIDATION_UNRESOLVED".equals(failure);
            case "BookingValidationRejected" -> (status==Status.REQUIRES_REQUOTE&&Set.of("PRICE_CHANGED","TERMS_CHANGED").contains(Objects.toString(failure,"")))||
                (status==Status.INVENTORY_UNAVAILABLE&&"OFFER_UNAVAILABLE".equals(failure))||(status==Status.EXPIRED&&"QUOTE_EXPIRED".equals(failure));
            default -> false;
        };
        if(!valid)throw new EventRejected(INVALID_ENVELOPE);
    }
    public void requireSuccessorOf(BookingLifecycleEvent previous) {
        long expected=previous==null?1:previous.aggregateVersion()+1;
        if(aggregateVersion!=expected)throw new EventRejected(ORDER_GAP);
        if(previous==null){if(!eventType.equals("BookingCreated"))throw new EventRejected(INVALID_TRANSITION);return;}
        if(!aggregateId.equals(previous.aggregateId())||!correlationId.equals(previous.correlationId())||!causationId.equals(previous.causationId()))throw new EventRejected(EVENT_CONFLICT);
        boolean legal=previous.payload().status()==Status.CREATED?eventType.equals("BookingValidationStarted"):
            previous.payload().status()==Status.VALIDATING&&!Set.of("BookingCreated","BookingValidationStarted").contains(eventType);
        if(!legal)throw new EventRejected(INVALID_TRANSITION);
    }
}
