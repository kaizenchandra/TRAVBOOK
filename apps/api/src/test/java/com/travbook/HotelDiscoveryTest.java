package com.travbook;
import com.travbook.hotels.domain.model.*;
import com.travbook.hotels.application.port.out.*;
import com.travbook.hotels.application.usecase.HotelDiscovery;
import com.travbook.hotels.adapter.out.supplier.*;
import com.travbook.shared.domain.DomainFailure;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
class HotelDiscoveryTest {
    final Instant now=Instant.parse("2026-11-01T00:00:00Z");
    HotelCriteria query(List<HotelCriteria.Occupancy> rooms){return new HotelCriteria("Delhi",LocalDate.parse("2026-11-03"),LocalDate.parse("2026-11-05"),rooms);}
    final HotelCriteria.Occupancy adults=new HotelCriteria.Occupancy(2,List.of());
    @Test void stayAndRoomAgeConstraintsAreIndependentOfFlightPassengerRules(){
        assertThatThrownBy(()->new HotelCriteria.Occupancy(0,List.of())).isInstanceOf(DomainFailure.class);
        assertThatThrownBy(()->new HotelCriteria.Occupancy(1,List.of(18))).isInstanceOf(DomainFailure.class);
        assertThatThrownBy(()->new HotelCriteria.Occupancy(1,List.of(1,2,3))).isInstanceOf(DomainFailure.class);
        assertThatThrownBy(()->new HotelCriteria("Delhi",LocalDate.now(),LocalDate.now(),List.of(adults))).isInstanceOf(DomainFailure.class);
        assertThatThrownBy(()->query(Collections.nCopies(5,adults))).isInstanceOf(DomainFailure.class);
        assertThatThrownBy(()->query(List.of(adults)).validateDates(Clock.fixed(now.plusSeconds(86400*20),ZoneOffset.UTC))).isInstanceOf(DomainFailure.class);
        assertThatCode(()->new HotelCriteria.Occupancy(1,List.of(12,17))).doesNotThrowAnyException();
    }
    @Test void nightlyOccupancySupplementsTaxesAndFeesBalanceWithoutFloatingPoint(){
        var q=query(List.of(new HotelCriteria.Occupancy(2,List.of(2,12))));
        var offer=new MockHotelSupplier("test",0).search(q,now).offers().stream().filter(o->o.planCode()==HotelOffer.Plan.FLEX).findFirst().orElseThrow();
        assertThat(offer.subtotal().amount()).isEqualByComparingTo("9800.00");assertThat(offer.taxes().amount()).isEqualByComparingTo("980.00");assertThat(offer.total().amount()).isEqualByComparingTo("10880.00");
        assertThat(offer.freeCancellationUntil()).isEqualTo(Instant.parse("2026-11-01T08:30:00Z"));
        assertThat(offer.refundable()).isTrue();assertThat(offer.nights()).hasSize(2);
    }
    @Test void providerRepricesEveryRoomAndTracksSharedRoomInventory(){
        var provider=new MockHotelSupplier("test",0);var q=query(List.of(adults,adults,adults));var available=provider.search(q,now).offers();
        String property=available.getFirst().propertyId();
        var standard=available.stream().filter(o->o.propertyId().equals(property)&&o.planCode()==HotelOffer.Plan.VALUE).toList();
        assertThatThrownBy(()->provider.reprice(q,standard,now)).isInstanceOf(DomainFailure.class).hasMessageContaining("combination");
        var families=available.stream().filter(o->o.propertyId().equals(property)&&o.planCode()==HotelOffer.Plan.FAMILY).toList();
        var repriced=provider.reprice(q,families,now);assertThat(repriced).hasSize(3);
        for(int i=0;i<3;i++)assertThat(repriced.get(i).total().amount().subtract(families.get(i).total().amount())).isEqualByComparingTo("550.00");
        var studio=available.stream().filter(o->o.propertyId().equals(property)&&o.planCode()==HotelOffer.Plan.STUDIO).toList();
        assertThatThrownBy(()->provider.reprice(q,studio,now)).isInstanceOf(DomainFailure.class).hasMessageContaining("no longer available");
    }
    @Test void deadlineCrossingRemovesRefundabilityWithoutChangingPolicyHistory(){
        var provider=new MockHotelSupplier("test",0);var q=query(List.of(adults));var first=provider.search(q,now).offers().stream().filter(o->o.planCode()==HotelOffer.Plan.FLEX).findFirst().orElseThrow();
        var after=provider.reprice(q,List.of(first),first.freeCancellationUntil()).getFirst();assertThat(after.refundable()).isFalse();assertThat(first.refundable()).isTrue();assertThat(after.cancellationPolicy()).isEqualTo(first.cancellationPolicy());
    }
    @Test void fanOutTimeoutKeepsHealthyProviderAndRepriceHasABoundedDeadline()throws Exception{
        HotelSupplier stalled=new HotelSupplier(){public String id(){return "stalled";}public Inventory search(HotelCriteria c,Instant n){try{Thread.sleep(10_000);}catch(InterruptedException e){Thread.currentThread().interrupt();}return new Inventory(List.of(),List.of());}public List<HotelOffer> reprice(HotelCriteria c,List<HotelOffer> s,Instant n){search(c,n);return s;}};
        try(var pool=Executors.newFixedThreadPool(2)){
            var gateway=new BoundedHotelGateway(List.of(stalled,new MockHotelSupplier("healthy",0)),pool,150,new SimpleMeterRegistry());long started=System.nanoTime();var result=gateway.search(query(List.of(adults)),now);
            assertThat(Duration.ofNanos(System.nanoTime()-started)).isLessThan(Duration.ofSeconds(2));assertThat(result.properties()).hasSize(2);assertThat(result.coverage()).extracting(c->c.status()).containsExactly(HotelSnapshot.Coverage.Status.UNAVAILABLE,HotelSnapshot.Coverage.Status.AVAILABLE);
            var slow=new MockHotelSupplier("stalled",0).search(query(List.of(adults)),now).offers().getFirst();assertThatThrownBy(()->gateway.reprice(query(List.of(adults)),List.of(slow),now)).isInstanceOf(DomainFailure.class).hasMessageContaining("time budget");
        }
    }
    @Test void repeatedReadRevalidationNeverMutatesThePersistedSnapshot(){
        var saved=new HashMap<UUID,HotelSnapshot>();HotelSearchRepository repo=new HotelSearchRepository(){public void save(HotelSnapshot s){saved.put(s.id(),s);}public Optional<HotelSnapshot> find(UUID id){return Optional.ofNullable(saved.get(id));}public Optional<HotelSnapshot> findByOffer(UUID id){return saved.values().stream().filter(s->s.offers().stream().anyMatch(o->o.id().equals(id))).findFirst();}};
        try(var pool=Executors.newFixedThreadPool(2)){
            var service=new HotelDiscovery(repo,new BoundedHotelGateway(List.of(new MockHotelSupplier("test",0)),pool,1000,new SimpleMeterRegistry()),Clock.fixed(now,ZoneOffset.UTC));
            var s=service.search(query(List.of(adults))).snapshot();var offer=s.offers().stream().filter(o->o.planCode()==HotelOffer.Plan.FAMILY).findFirst().orElseThrow();var a=service.reprice(offer.id(),List.of(offer.id()));var b=service.reprice(offer.id(),List.of(offer.id()));
            assertThat(a.total()).isEqualTo(b.total());assertThat(a.changed()).isTrue();assertThat(repo.find(s.id()).orElseThrow()).isEqualTo(s);assertThat(saved).hasSize(1);
            var expired=new HotelDiscovery(repo,null,Clock.fixed(s.expiresAt(),ZoneOffset.UTC));assertThatThrownBy(()->expired.rooms(s.id(),offer.propertyId())).isInstanceOf(DomainFailure.class).hasMessageContaining("expired");
        }
    }
}
