package com.travbook.flightprovider.application.usecase;
import com.travbook.flightprovider.application.port.in.FlightProvider;
import com.travbook.flightprovider.application.port.out.ProviderStore;
import com.travbook.flightprovider.domain.model.*;
import com.travbook.customer.application.port.in.Customers;
import com.travbook.shared.domain.*;
import java.time.*;
import java.util.*;
import java.security.*;
import java.nio.charset.StandardCharsets;

public final class FlightProviderService implements FlightProvider {
    private final ProviderStore store;private final Customers customers;private final Clock clock;
    public FlightProviderService(ProviderStore store,Customers customers,Clock clock){this.store=store;this.customers=customers;this.clock=clock;}
    private UUID owner(CustomerIdentity identity){return customers.resolve(identity).id();}
    private void key(String key){if(key==null || !key.matches("[A-Za-z0-9._:-]{16,128}"))throw FlightFixture.invalid("Use an idempotency key of 16–128 supported characters.");}
    private String fingerprint(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    public List<FlightFixture.Flight> flights(LocalDate date){if(date==null)throw FlightFixture.invalid("A fixture date is required.");var f=FlightFixture.flight("MOCK-FA-"+date);FlightFixture.bookable(f,clock.instant());return List.of(f);}
    public FlightFixture.SeatMap seats(String id){var f=FlightFixture.flight(id);FlightFixture.bookable(f,clock.instant());return FlightFixture.seats(f,store.occupied(id),clock.instant());}
    public FlightFixture.Catalog ancillaries(String id){var f=FlightFixture.flight(id);FlightFixture.bookable(f,clock.instant());return FlightFixture.ancillaries(f);}
    public ProviderQuote quote(CustomerIdentity identity,String id,List<FlightFixture.Selection> input,String key,UUID correlation){
        key(key);var selections=FlightFixture.selections(input);var flight=FlightFixture.flight(id);var owner=owner(identity);
        return store.createQuote(owner,key,fingerprint(id+"|"+selections),()->ProviderQuote.create(owner,flight,selections,clock.instant()),correlation);
    }
    public ProviderQuote quote(CustomerIdentity identity,UUID id){return store.quote(owner(identity),id);}
    public ProviderQuote promotionalQuote(CustomerIdentity identity,UUID expected,String id,List<FlightFixture.Selection> input,String code,String key,UUID correlation){
        key(key);var owner=owner(identity);if(!owner.equals(expected))throw new DomainFailure(DomainFailure.Code.PROFILE_IDENTITY_CONFLICT,"Your account changed. Reload before requesting a quote.");
        if(code==null||code.length()>24)throw FlightFixture.invalid("Coupon codes contain at most 24 letters or digits.");var normalized=code.strip().toUpperCase(Locale.ROOT);if(!normalized.isEmpty()&&!normalized.matches("[A-Z0-9]{3,24}"))throw FlightFixture.invalid("Coupon codes contain letters and digits only.");
        var selections=FlightFixture.selections(input);var f=FlightFixture.flight(id);
        return store.promotionalQuote(owner,key,fingerprint("promotion|"+id+"|"+selections+"|"+normalized),()->ProviderQuote.create(owner,f,selections,clock.instant()),normalized,correlation);
    }
    public PromotionView promotion(CustomerIdentity identity,UUID quote){var owner=owner(identity);return new PromotionView(owner,store.promotion(owner,quote));}
    public ProviderReservation reserve(CustomerIdentity identity,UUID id,int version,TravelMoney total,boolean consent,String key,UUID correlation){
        key(key);FlightFixture.consent(consent);return store.reserve(owner(identity),id,key,fingerprint(id+"|"+version+"|"+total+"|"+consent),q->{q.accept(version,total,consent,clock.instant());return ProviderReservation.reserve(q,clock.instant());},correlation);
    }
    public ProviderReservation reservation(CustomerIdentity identity,UUID id){return store.reservation(owner(identity),id);}
    public Page reservations(CustomerIdentity identity,UUID after){return store.reservations(owner(identity),after);}
    public ProviderReservation act(CustomerIdentity identity,UUID id,ProviderReservation.Action action,List<String> input,boolean consent,String key,UUID correlation){
        key(key);FlightFixture.consent(consent);if(action==null)throw FlightFixture.invalid("An operation is required.");
        if(input==null || input.size()>6 || input.stream().anyMatch(p->p==null || !p.matches("P[1-6]")))throw FlightFixture.invalid("Invalid synthetic passenger slots.");
        var passengers=input.stream().sorted().toList();if(action!=ProviderReservation.Action.CHECK_IN && !passengers.isEmpty())throw FlightFixture.invalid("Passenger selection is only accepted for check-in.");
        // Payment outcome shares one operation namespace so changing outcome with the same key conflicts.
        String operation=switch(action){case PAY_SUCCESS,PAY_DECLINE->"PAYMENT";case TICKET_ISSUE,TICKET_REJECT->"TICKET";default->action.name();};
        return store.change(owner(identity),id,operation,key,fingerprint(id+"|"+action+"|"+passengers+"|"+consent),r->r.apply(action,passengers,consent,clock.instant()),correlation);
    }
    public List<ProviderReservation.Document> documents(CustomerIdentity identity,UUID id){return reservation(identity,id).documents();}
}
