package com.travbook.search.application.usecase;
import com.travbook.catalog.application.port.in.LookupLocations;
import com.travbook.search.application.port.in.DiscoverFlights;
import com.travbook.search.application.port.out.*;
import com.travbook.search.domain.model.*;
import com.travbook.shared.domain.DomainFailure;
import java.time.*;
import java.util.*;
import static com.travbook.shared.domain.DomainFailure.Code.*;
public final class FlightDiscovery implements DiscoverFlights {
    private final LookupLocations locations;
    private final SupplierGateway suppliers;
    private final SearchRepository repository;
    private final Clock clock;
    public FlightDiscovery(LookupLocations locations,SupplierGateway suppliers,SearchRepository repository,Clock clock) {
        this.locations=locations; this.suppliers=suppliers; this.repository=repository; this.clock=clock;
    }
    public Page search(FlightQuery query) {
        query.validateDates(clock);
        locations.get(query.origin()); locations.get(query.destination());
        Instant now=clock.instant();
        var result=suppliers.search(query,now);
        if(result.coverage().stream().noneMatch(c -> c.status()==SearchSnapshot.Coverage.Status.AVAILABLE))
            throw new DomainFailure(SUPPLIERS_UNAVAILABLE,"Flight providers are unavailable. Please retry shortly.");
        var snapshot=new SearchSnapshot(UUID.randomUUID(),query,result.offers(),result.coverage(),now,now.plusSeconds(900));
        repository.save(snapshot);
        return paginate(snapshot,0,5,false,Sort.PRICE,"");
    }
    public Page page(UUID id,int page,int size,boolean nonstop,Sort sort,String airline) {
        var snapshot=repository.find(id).orElseThrow(() -> new DomainFailure(NOT_FOUND,"Search not found. Start a new search."));
        if(!snapshot.expiresAt().isAfter(clock.instant())) throw new DomainFailure(SEARCH_EXPIRED,"This search expired. Search again for current fares.");
        return paginate(snapshot,page,size,nonstop,sort,airline);
    }
    private Page paginate(SearchSnapshot snapshot,int page,int size,boolean nonstop,Sort sort,String airline) {
        if(page<0 || page>100 || size<1 || size>20 || sort==null || airline==null || airline.length()>60)
            throw new DomainFailure(VALIDATION_FAILED,"Invalid filter or pagination options.");
        Comparator<FlightOffer> order=switch(sort) {
            case PRICE -> Comparator.comparing(o -> o.total().amount());
            case DURATION -> Comparator.comparingInt(o -> o.durationMinutes());
            case DEPARTURE -> Comparator.comparing(o -> o.legs().getFirst().departure());
        };
        var filtered=snapshot.offers().stream().filter(o -> !nonstop || o.nonstop())
            .filter(o -> airline.isBlank() || o.airline().equals(airline))
            .sorted(order.thenComparing(o -> o.id())).toList();
        return new Page(snapshot,filtered.stream().skip((long)page*size).limit(size).toList(),filtered.size(),page,size);
    }
    public FlightOffer offer(UUID id) {
        var offer=repository.findOffer(id).orElseThrow(() -> new DomainFailure(NOT_FOUND,"Flight offer not found."));
        if(!offer.expiresAt().isAfter(clock.instant()) || !offer.legs().getFirst().departure().toInstant().isAfter(clock.instant()))
            throw new DomainFailure(OFFER_EXPIRED,"This offer expired. Search again for current fares.");
        return offer;
    }
    public Reprice reprice(UUID id) {
        var original=offer(id);
        Instant now=clock.instant();
        var current=suppliers.reprice(original,now);
        return new Reprice(current,original.total(),original.total().amount().compareTo(current.total().amount())!=0,now,current.expiresAt());
    }
    public FlightQuery criteriaForOffer(UUID id) {
        return repository.criteriaForOffer(id).orElseThrow(()->new DomainFailure(NOT_FOUND,"Original search criteria are unavailable. Search again."));
    }
    public FlightOffer revalidateForBooking(UUID id) {
        var original=repository.findOffer(id).orElseThrow(()->new DomainFailure(NOT_FOUND,"Original supplier reference is unavailable."));
        if(!original.legs().getFirst().departure().toInstant().isAfter(clock.instant())) throw new DomainFailure(OFFER_EXPIRED,"This flight has departed.");
        // A still-valid accepted quote may outlive the discovery cache. Query the provider afresh; never reuse cached availability.
        return suppliers.reprice(original,clock.instant());
    }
}

