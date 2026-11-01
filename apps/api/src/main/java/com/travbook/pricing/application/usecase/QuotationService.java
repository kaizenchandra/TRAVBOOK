package com.travbook.pricing.application.usecase;
import com.travbook.pricing.application.port.in.Quotations;
import com.travbook.pricing.application.port.out.*;
import com.travbook.pricing.domain.model.*;
import com.travbook.customer.application.port.in.Customers;
import com.travbook.shared.domain.*;
import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import static com.travbook.shared.domain.DomainFailure.Code.*;
public final class QuotationService implements Quotations {
    private final Customers customers; private final RevalidateSelection pricing; private final QuotationRepository repository; private final Clock clock;
    public QuotationService(Customers customers,RevalidateSelection pricing,QuotationRepository repository,Clock clock) {
        this.customers=customers; this.pricing=pricing; this.repository=repository; this.clock=clock;
    }
    private void key(String key) {
        if(key==null || !key.matches("[A-Za-z0-9_-]{16,100}")) throw new DomainFailure(VALIDATION_FAILED,"Use an Idempotency-Key of 16–100 letters, digits, underscores or hyphens.");
    }
    private String fingerprint(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    public Quotation create(CustomerIdentity identity,UUID offerId,String key,UUID correlationId) {
        key(key);
        UUID owner=customers.resolve(identity).id();
        String fingerprint=fingerprint(offerId.toString());
        var replay=repository.replayCreation(owner,key,fingerprint);
        if(replay.isPresent())return replay.get();
        // External provider read precedes the local transaction.
        var selection=pricing.revalidate(offerId);
        Instant now=clock.instant();
        Instant expiry=selection.validUntil();
        if(!expiry.isAfter(now)) throw new DomainFailure(QUOTE_EXPIRED,"The provider price expired. Request a fresh quote.");
        var quote=new Quotation(UUID.randomUUID(),owner,1,"MOCK",selection,now,expiry);
        return repository.create(quote,key,fingerprint,correlationId);
    }
    public Details get(CustomerIdentity identity,UUID id) {
        UUID owner=customers.resolve(identity).id();
        var quote=repository.findOwned(owner,id).orElseThrow(()->new DomainFailure(NOT_FOUND,"Quotation not found."));
        return new Details(quote,repository.acceptance(owner,id).orElse(null),quote.expired(clock.instant()));
    }
    public QuoteAcceptance accept(CustomerIdentity identity,UUID id,int version,String key,UUID correlationId) {
        key(key);
        UUID owner=customers.resolve(identity).id();
        return repository.accept(owner,id,version,key,fingerprint(id+":"+version),correlationId);
    }
    public Details forBooking(CustomerIdentity identity,UUID id,int version) {
        var details=get(identity,id);
        details.quote().requireAcceptance(version,clock.instant());
        if(details.acceptance()==null) throw new DomainFailure(QUOTE_NOT_ACCEPTED,"Accept the quotation before starting a booking intent.");
        if(details.acceptance().quoteVersion()!=version) throw new DomainFailure(QUOTE_VERSION_CONFLICT,"Accept the exact quotation version.");
        return details;
    }
}

