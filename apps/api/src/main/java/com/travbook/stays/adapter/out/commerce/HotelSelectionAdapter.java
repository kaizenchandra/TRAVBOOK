package com.travbook.stays.adapter.out.commerce;
import com.travbook.stays.application.port.out.StaySelection;
import com.travbook.stays.domain.model.Stay.*;
import com.travbook.hotels.application.port.in.DiscoverHotels;
import com.travbook.shared.domain.DomainFailure;
import java.util.*;
public final class HotelSelectionAdapter implements StaySelection {
 private final DiscoverHotels hotels;public HotelSelectionAdapter(DiscoverHotels hotels){this.hotels=hotels;}
 public Selection reprice(List<UUID> ids){return reprice(ids,CollectionMethod.PAY_NOW);}
 public Selection reprice(List<UUID> ids,CollectionMethod method){var r=hotels.reprice(ids.getFirst(),ids,method==CollectionMethod.PAY_AT_PROPERTY);var details=hotels.rooms(r.searchId(),r.propertyId());return new Selection(details.property(),details.snapshot().criteria(),r.offers(),r.previousTotal(),r.total(),r.checkedAt(),r.validUntil());}
 public Selection change(Booking b,com.travbook.hotels.domain.model.HotelCriteria criteria,List<com.travbook.hotels.domain.model.HotelOffer.Plan> plans){var ids=plans.stream().map(p->UUID.randomUUID()).toList();var offers=hotels.changeRates(b.id(),b.currentQuote().selection().property().id(),criteria,plans,ids,b.deferred());return new Selection(b.currentQuote().selection().property(),criteria,offers,b.currentQuote().selection().total(),com.travbook.hotels.domain.model.HotelMoney.inr(offers.stream().map(o->o.total().amount()).reduce(java.math.BigDecimal.ZERO,(a,v)->a.add(v))),offers.getFirst().observedAt(),offers.stream().map(o->o.expiresAt()).min((left,right)->left.compareTo(right)).orElseThrow());}
 public void validateChange(Booking b){var q=b.change().quote();var expected=q.selection().offers();var fresh=hotels.changeRates(b.id(),q.selection().property().id(),q.selection().criteria(),expected.stream().map(o->o.planCode()).toList(),expected.stream().map(o->o.id()).toList(),b.deferred());if(fresh.size()!=expected.size())throw new DomainFailure(DomainFailure.Code.BOOKING_CONFLICT,"Incomplete replacement rates.");for(int i=0;i<fresh.size();i++)if(!fresh.get(i).sameTerms(expected.get(i)))throw new DomainFailure(DomainFailure.Code.BOOKING_CONFLICT,"Replacement terms changed; request a fresh quote.");}
 public void validate(Quote quote){var fresh=reprice(quote.selection().offers().stream().map(o->o.id()).toList(),quote.collectionMethod());if(!fresh.total().equals(quote.selection().total()))throw new DomainFailure(DomainFailure.Code.BOOKING_CONFLICT,"Hotel price changed before payment. Request a fresh quote.");for(int i=0;i<fresh.offers().size();i++)if(!fresh.offers().get(i).sameTerms(quote.selection().offers().get(i)))throw new DomainFailure(DomainFailure.Code.BOOKING_CONFLICT,"Hotel room inclusions or cancellation conditions changed. Request a fresh quote.");}
}
