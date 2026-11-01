package com.travbook.hotels.application.usecase;
import com.travbook.hotels.application.port.in.DiscoverHotels;
import com.travbook.hotels.application.port.out.*;
import com.travbook.hotels.domain.model.*;
import com.travbook.shared.domain.DomainFailure;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.travbook.shared.domain.DomainFailure.Code.*;
public final class HotelDiscovery implements DiscoverHotels {
    private final HotelSearchRepository repository;private final HotelSupplierGateway suppliers;private final Clock clock;
    public HotelDiscovery(HotelSearchRepository repository,HotelSupplierGateway suppliers,Clock clock){this.repository=repository;this.suppliers=suppliers;this.clock=clock;}
    public Page search(HotelCriteria criteria) {
        criteria.validateDates(clock);Instant now=clock.instant();var result=suppliers.search(criteria,now);
        if(result.coverage().stream().noneMatch(c->c.status()==HotelSnapshot.Coverage.Status.AVAILABLE))
            throw new DomainFailure(SUPPLIERS_UNAVAILABLE,"Hotel providers are unavailable. This is not an empty availability result. Please retry.");
        Instant expiry=now.plusSeconds(900).isBefore(criteria.lastSearchInstant())?now.plusSeconds(900):criteria.lastSearchInstant();
        var snapshot=new HotelSnapshot(UUID.randomUUID(),criteria,result.properties(),result.offers(),result.coverage(),now,expiry);
        repository.save(snapshot);return filter(snapshot,Filters.defaults());
    }
    private HotelSnapshot current(HotelSnapshot snapshot) {
        if(!snapshot.expiresAt().isAfter(clock.instant()))throw new DomainFailure(SEARCH_EXPIRED,"This hotel search expired. Search again for current rooms and prices.");
        return snapshot;
    }
    public Page page(UUID id,Filters filters){return filter(current(repository.find(id).orElseThrow(()->new DomainFailure(NOT_FOUND,"Hotel search not found."))),filters);}
    private Optional<HotelMoney> minimum(HotelSnapshot s,String property,boolean refundable) {
        BigDecimal total=BigDecimal.ZERO;
        for(int i=0;i<s.criteria().rooms().size();i++) {
            final int room=i;
            var value=s.offers().stream().filter(o->o.propertyId().equals(property)&&o.roomIndex()==room&&(!refundable||o.refundable()&&o.freeCancellationUntil().isAfter(clock.instant())))
                .map(o->o.total().amount()).min((a,b)->a.compareTo(b));
            if(value.isEmpty())return Optional.empty();total=total.add(value.get());
        }
        return Optional.of(HotelMoney.inr(total));
    }
    private Page filter(HotelSnapshot s,Filters f) {
        if(f.page()<0||f.page()>100||f.size()<1||f.size()>20||f.minStars()<0||f.minStars()>5||f.sort()==null||f.type()==null||f.amenity()==null
            ||f.maxTotal()==null||f.maxTotal().signum()<0||f.maxTotal().scale()>2||f.maxTotal().compareTo(new BigDecimal("9999999"))>0)
            throw new DomainFailure(VALIDATION_FAILED,"Invalid hotel filter or pagination.");
        var results=new ArrayList<Result>();
        for(var property:s.properties()) {
            if((f.type()!=TypeFilter.ALL&&!property.type().name().equals(f.type().name()))||property.stars()<f.minStars()
                ||(f.amenity()!=Amenity.ALL&&!property.amenities().contains(f.amenity().name())))continue;
            var price=minimum(s,property.id(),f.refundable());
            if(price.isPresent()&&price.get().amount().compareTo(f.maxTotal())<=0)results.add(new Result(property,price.get(),minimum(s,property.id(),true).isPresent()));
        }
        Comparator<Result> order=f.sort()==Sort.PRICE?Comparator.comparing(r->r.fromTotal().amount()):Comparator.comparing((Result r)->r.property().rating()).reversed();
        results.sort(order.thenComparing(r->r.property().id()));
        return new Page(s,results.stream().skip((long)f.page()*f.size()).limit(f.size()).toList(),results.size(),f.page(),f.size());
    }
    public List<HotelOffer> changeRates(UUID reservation,String property,HotelCriteria criteria,List<HotelOffer.Plan> plans,List<UUID> ids,boolean deferred){return suppliers.changeRates(reservation,property,criteria,plans,ids,deferred,clock.instant());}
    public Rooms rooms(UUID id,String propertyId) {
        var s=current(repository.find(id).orElseThrow(()->new DomainFailure(NOT_FOUND,"Hotel search not found.")));
        var p=s.properties().stream().filter(v->v.id().equals(propertyId)).findFirst().orElseThrow(()->new DomainFailure(NOT_FOUND,"Property was not returned for this search."));
        return new Rooms(s,p,s.offers().stream().filter(o->o.propertyId().equals(propertyId)).toList());
    }
    public Reprice reprice(UUID anchor,List<UUID> ids) {return reprice(anchor,ids,false);}
    public Reprice reprice(UUID anchor,List<UUID> ids,boolean deferred) {
        if(ids==null||ids.isEmpty()||ids.size()>4||ids.stream().anyMatch(Objects::isNull)||!ids.getFirst().equals(anchor)||new HashSet<>(ids).size()!=ids.size())
            throw new DomainFailure(VALIDATION_FAILED,"Select exactly one rate per room, in room order.");
        var s=current(repository.findByOffer(anchor).orElseThrow(()->new DomainFailure(NOT_FOUND,"Hotel room offer not found.")));
        if(ids.size()!=s.criteria().rooms().size())throw new DomainFailure(VALIDATION_FAILED,"Select a rate for every requested room.");
        var selected=new ArrayList<HotelOffer>();
        for(int i=0;i<ids.size();i++) {
            UUID id=ids.get(i);var offer=s.offers().stream().filter(o->o.id().equals(id)).findFirst().orElseThrow(()->new DomainFailure(VALIDATION_FAILED,"Room rates must belong to the same search."));
            if(offer.roomIndex()!=i||!selected.isEmpty()&&(!offer.propertyId().equals(selected.getFirst().propertyId())||!offer.supplier().equals(selected.getFirst().supplier())))
                throw new DomainFailure(VALIDATION_FAILED,"Choose one rate per room from the same property and provider.");
            selected.add(offer);
        }
        Instant now=clock.instant();var fresh=suppliers.reprice(s.criteria(),selected.stream().map(o->o.collection(deferred)).toList(),now);
        if(fresh.size()!=selected.size())throw new DomainFailure(SUPPLIERS_UNAVAILABLE,"Provider returned an incomplete room selection.");
        boolean changed=false;
        for(int i=0;i<fresh.size();i++) {
            var before=selected.get(i);var after=fresh.get(i);
            if(!before.id().equals(after.id())||!before.propertyId().equals(after.propertyId())||!before.supplier().equals(after.supplier())||before.roomIndex()!=after.roomIndex()||!before.occupancy().equals(after.occupancy())||before.planCode()!=after.planCode())
                throw new DomainFailure(SUPPLIERS_UNAVAILABLE,"Provider returned mismatched room rates.");
            changed|=!before.total().equals(after.total())||!before.cancellationPolicy().equals(after.cancellationPolicy())||before.refundable()!=after.refundable();
        }
        Instant expiry=fresh.stream().map(o->o.expiresAt()).min((a,b)->a.compareTo(b)).orElseThrow();
        if(s.expiresAt().isBefore(expiry))expiry=s.expiresAt();
        if(!expiry.isAfter(clock.instant()))throw new DomainFailure(OFFER_EXPIRED,"The room observation expired. Search again.");
        return new Reprice(s.id(),selected.getFirst().propertyId(),fresh,sum(selected),sum(fresh),changed,now,expiry);
    }
    private HotelMoney sum(List<HotelOffer> offers){return HotelMoney.inr(offers.stream().map(o->o.total().amount()).reduce(BigDecimal.ZERO,(a,b)->a.add(b)));}
}
