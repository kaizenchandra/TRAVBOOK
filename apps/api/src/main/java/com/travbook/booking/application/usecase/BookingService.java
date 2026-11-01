package com.travbook.booking.application.usecase;
import com.travbook.booking.application.port.in.BookingIntents;
import com.travbook.booking.application.port.out.*;
import com.travbook.booking.domain.model.*;
import com.travbook.customer.application.port.in.Customers;
import com.travbook.shared.domain.*;
import java.time.Clock;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import static com.travbook.shared.domain.DomainFailure.Code.*;
public final class BookingService implements BookingIntents {
    private final Customers customers;private final BookingQuotePort quotes;private final BookingStore store;private final Clock clock;
    public BookingService(Customers customers,BookingQuotePort quotes,BookingStore store,Clock clock){this.customers=customers;this.quotes=quotes;this.store=store;this.clock=clock;}
    public Requirements requirements(CustomerIdentity identity,UUID id,int version) {
        UUID owner=customers.resolve(identity).id();
        var existing=store.forQuote(owner,id);
        if(existing.isPresent())return new Requirements(existing.get().quote(),existing.get().id());
        return new Requirements(quotes.load(identity,id,version),null);
    }
    public Booking create(CustomerIdentity identity,UUID quoteId,int version,List<Traveller> travellers,String key,UUID correlationId) {
        if(key==null||!key.matches("[A-Za-z0-9_-]{16,100}"))throw new DomainFailure(VALIDATION_FAILED,"Use an Idempotency-Key of 16–100 letters, digits, underscores or hyphens.");
        if(quoteId==null||version<1||travellers==null||travellers.isEmpty()||travellers.size()>18)throw new DomainFailure(VALIDATION_FAILED,"Provide the quote version and 1–18 travellers.");
        UUID owner=customers.resolve(identity).id();String hash=fingerprint(quoteId,version,travellers);
        var replay=store.replay(owner,key,hash);if(replay.isPresent())return replay.get();
        var existing=store.forQuote(owner,quoteId);
        if(existing.isPresent()) {
            if(!existing.get().fingerprint().equals(hash))throw new DomainFailure(BOOKING_CONFLICT,"This quote already has a booking intent with different traveller details.");
            return store.create(existing.get(),key);
        }
        var quote=quotes.load(identity,quoteId,version);
        if(!owner.equals(quote.customerId()))throw new DomainFailure(NOT_FOUND,"Quotation not found.");
        quote.requireValid(clock.instant());quote.validateTravellers(travellers);
        var now=clock.instant();
        return store.create(new Booking(UUID.randomUUID(),quote,travellers,hash,now,now,1,Booking.Status.CREATED,null,correlationId,UUID.randomUUID()),key);
    }
    private String fingerprint(UUID quote,int version,List<Traveller> travellers) {
        // Length-prefix strings so delimiters inside legitimate names cannot create collisions.
        var text=new StringBuilder(quote+":"+version);
        for(var t:travellers)for(var value:List.of(t.givenName(),t.familyName(),t.dateOfBirth().toString()))text.append('|').append(value.length()).append(':').append(value);
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    public Details get(CustomerIdentity identity,UUID id) {
        return store.findOwned(customers.resolve(identity).id(),id).orElseThrow(()->new DomainFailure(NOT_FOUND,"Booking intent not found."));
    }
    public Page list(CustomerIdentity identity,UUID after,int size) {
        if(size<1||size>20)throw new DomainFailure(VALIDATION_FAILED,"Page size must be 1–20.");
        return store.listOwned(customers.resolve(identity).id(),after,size);
    }
}

