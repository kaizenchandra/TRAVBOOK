package com.travbook.pricing.domain.model;
import com.travbook.shared.domain.DomainFailure;
import java.time.*;
import java.util.UUID;
import static com.travbook.shared.domain.DomainFailure.Code.*;
public record Quotation(UUID id,UUID customerId,int version,String mode,FlightSelection selection,Instant createdAt,Instant expiresAt) {
    public Quotation {
        if(id==null || customerId==null || version!=1 || !"MOCK".equals(mode) || selection==null ||
            !expiresAt.isAfter(createdAt) || expiresAt.isAfter(selection.validUntil()) ||
            expiresAt.isAfter(selection.legs().getFirst().departure().toInstant())) throw new IllegalArgumentException("Invalid immutable quote");
    }
    public boolean expired(Instant now) { return !expiresAt.isAfter(now); }
    public void requireValid(Instant now) {
        if(expired(now)) throw new DomainFailure(QUOTE_EXPIRED,"This quotation expired. Request a fresh quote and review the current price.");
    }
    public void requireAcceptance(int acceptedVersion,Instant now) {
        if(version!=acceptedVersion) throw new DomainFailure(QUOTE_VERSION_CONFLICT,"The selected quote version does not match.");
        requireValid(now);
    }
    public boolean priceChanged() { return selection.total().amount().compareTo(selection.previousTotal().amount())!=0; }
}

