package com.travbook.cabs.application.usecase;
import com.travbook.cabs.application.port.in.DiscoverCabs;
import com.travbook.cabs.application.port.out.CabSupplier;
import com.travbook.cabs.domain.model.Cab.*;
import com.travbook.shared.application.port.out.SnapshotStore;
import com.travbook.shared.domain.*;
import java.time.*;
import java.math.*;
import java.util.*;
public final class CabDiscovery implements DiscoverCabs{
 private final SnapshotStore<Snapshot> store;private final CabSupplier supplier;private final Clock clock;
 public CabDiscovery(SnapshotStore<Snapshot> store,CabSupplier supplier,Clock clock){this.store=store;this.supplier=supplier;this.clock=clock;}
 public Page search(Criteria c){c.valid(clock);var now=clock.instant();var inventory=supplier.search(c,now);if(inventory.coverage().stream().noneMatch(v->v.status()==SupplierCoverage.Status.AVAILABLE))throw new DomainFailure(DomainFailure.Code.SUPPLIERS_UNAVAILABLE,"All cab providers are unavailable, not sold out.");var s=new Snapshot(UUID.randomUUID(),c,inventory.offers(),inventory.coverage(),now,c.pickupAt().minusSeconds(7200).isBefore(now.plusSeconds(600))?c.pickupAt().minusSeconds(7200):now.plusSeconds(600),"MOCK");store.save(s);return filtered(s,CategoryFilter.ALL,new BigDecimal("99999999"),Sort.PRICE);}
 private Snapshot current(UUID id){var s=store.find(id).orElseThrow(()->new DomainFailure(DomainFailure.Code.NOT_FOUND,"Cab search not found."));s.fresh(clock.instant());return s;}
 public Page page(UUID id,CategoryFilter category,BigDecimal maxFare,Sort sort){return filtered(current(id),category,maxFare,sort);}
 private Page filtered(Snapshot s,CategoryFilter category,BigDecimal maxFare,Sort sort){if(category==null||sort==null||maxFare==null||maxFare.signum()<0||maxFare.scale()>2||maxFare.compareTo(new BigDecimal("99999999"))>0)throw new DomainFailure(DomainFailure.Code.VALIDATION_FAILED,"Invalid cab filters.");var offers=s.offers().stream().filter(o->category==CategoryFilter.ALL||o.category().name().equals(category.name())).filter(o->o.total().decimal().compareTo(maxFare)<=0).sorted(sort==Sort.PRICE?Comparator.comparing(o->o.total().decimal()):Comparator.comparingInt(o->o.passengerCapacity())).toList();return new Page(s.id(),s.criteria(),offers,s.coverage(),s.createdAt(),s.expiresAt(),"MOCK",offers.size());}
 public Detail detail(UUID search,UUID id){var s=current(search);return new Detail(search,s.criteria(),s.offer(id),s.createdAt(),s.expiresAt(),"MOCK");}
 public FareCheck reprice(UUID search,UUID id){var s=current(search);s.criteria().valid(clock);var before=s.offer(id);var after=supplier.reprice(s.criteria(),before,clock.instant());s.fresh(clock.instant());if(!after.id().equals(before.id())||!after.supplier().equals(before.supplier())||after.category()!=before.category()||!after.pickupAt().equals(before.pickupAt()))throw new DomainFailure(DomainFailure.Code.SUPPLIERS_UNAVAILABLE,"Cab provider returned mismatched terms.");var until=clock.instant().plusSeconds(60);if(s.expiresAt().isBefore(until))until=s.expiresAt();return new FareCheck(after,before.total(),!after.total().equals(before.total()),clock.instant(),until,"MOCK");}
}
