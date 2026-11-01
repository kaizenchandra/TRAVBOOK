package com.travbook;
import com.travbook.catalog.application.port.in.LookupLocations;
import com.travbook.catalog.domain.model.Location;
import com.travbook.search.application.usecase.FlightDiscovery;
import com.travbook.search.application.port.in.DiscoverFlights.Sort;
import com.travbook.search.application.port.out.*;
import com.travbook.search.adapter.out.supplier.*;
import com.travbook.search.domain.model.*;
import com.travbook.shared.domain.DomainFailure;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import java.math.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
class DiscoveryTest {
    private final Instant now=Instant.parse("2026-10-31T00:00:00Z");
    private final Clock clock=Clock.fixed(now,ZoneOffset.UTC);
    private FlightQuery query(String destination) { return new FlightQuery("DEL",destination,LocalDate.of(2026,11,1),null,1,0,0,FlightQuery.Cabin.ECONOMY); }
    @Test void validatesDatesRoutesAndPassengerRules() {
        assertThatThrownBy(() -> query("DEL")).isInstanceOf(DomainFailure.class);
        assertThatThrownBy(() -> new FlightQuery("DEL","BOM",LocalDate.of(2026,11,1),LocalDate.of(2026,10,31),1,0,0,FlightQuery.Cabin.ECONOMY)).isInstanceOf(DomainFailure.class);
        assertThatThrownBy(() -> new FlightQuery("DEL","BOM",LocalDate.of(2026,10,30),null,1,0,0,FlightQuery.Cabin.ECONOMY).validateDates(clock)).isInstanceOf(DomainFailure.class);
        assertThatThrownBy(() -> new FlightQuery("DEL","BOM",LocalDate.of(2027,12,1),null,1,0,0,FlightQuery.Cabin.ECONOMY).validateDates(clock)).isInstanceOf(DomainFailure.class);
        assertThatThrownBy(() -> new FlightQuery("DEL","BOM",LocalDate.of(2026,11,1),null,1,0,2,FlightQuery.Cabin.ECONOMY)).isInstanceOf(DomainFailure.class);
        assertThatThrownBy(() -> new FlightQuery("DEL","BOM",LocalDate.of(2026,11,1),null,9,1,0,FlightQuery.Cabin.ECONOMY)).isInstanceOf(DomainFailure.class);
    }
    @Test void moneyRejectsRoundingAndNegativeAmounts() {
        assertThatThrownBy(() -> Money.inr(new BigDecimal("1.001"))).isInstanceOf(ArithmeticException.class);
        assertThatThrownBy(() -> Money.inr(new BigDecimal("-1"))).isInstanceOf(IllegalArgumentException.class);
        assertThat(Money.inr(new BigDecimal("0.10")).plus(Money.inr(new BigDecimal("0.20"))).amount()).isEqualByComparingTo("0.30");
    }
    @Test void mockRoundTripPricesAllPassengersAndKeepsTimezones() {
        var q=new FlightQuery("DEL","BOM",LocalDate.of(2026,11,1),LocalDate.of(2026,11,3),2,1,1,FlightQuery.Cabin.ECONOMY);
        var offer=new MockFlightSupplier("sample","Sample",0).search(q,now).getFirst();
        assertThat(offer.legs()).hasSize(2);
        assertThat(offer.legs().getLast().origin()).isEqualTo("BOM");
        assertThat(offer.total().amount()).isEqualByComparingTo("22743.00");
        assertThat(offer.legs().getFirst().timezone()).isEqualTo("Asia/Kolkata");
    }
    @Test void repriceNeverMutatesOriginalAndSoldOutIsConflict() {
        var supplier=new MockFlightSupplier("sample","Sample",0);
        var offers=supplier.search(query("BOM"),now);
        var original=offers.get(1);
        var changed=supplier.reprice(original,now.plusSeconds(5));
        assertThat(changed.total().amount().subtract(original.total().amount())).isEqualByComparingTo("250");
        assertThat(original.observedAt()).isEqualTo(now);
        assertThatThrownBy(() -> supplier.reprice(offers.get(3),now)).isInstanceOf(DomainFailure.class).hasMessageContaining("no longer available");
    }
    @Test void sameDayReturnsFollowArrivalAndStayOnRequestedDate() {
        var q=new FlightQuery("DEL","BOM",LocalDate.of(2026,11,1),LocalDate.of(2026,11,1),1,0,0,FlightQuery.Cabin.ECONOMY);
        var offers=new MockFlightSupplier("sample","Sample",0).search(q,now);
        assertThat(offers).hasSize(3);
        for(var offer:offers) {
            assertThat(offer.legs().getLast().departure()).isAfterOrEqualTo(offer.legs().getFirst().arrival().plusMinutes(60));
            assertThat(offer.legs().getLast().departure().toLocalDate()).isEqualTo(q.returnDate());
        }
        var good=offers.getFirst();
        var impossible=new FlightOffer.Leg("BOM","DEL",good.legs().getFirst().departure(),good.legs().getFirst().arrival(),"Asia/Kolkata",130,0,"SIM-INVALID");
        assertThatThrownBy(()->new FlightOffer(good.id(),good.supplier(),good.supplierReference(),good.airline(),List.of(good.legs().getFirst(),impossible),
            good.base(),good.taxes(),1,good.cabin(),good.baggage(),good.cancellationPolicy(),now,now.plusSeconds(900))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void boundedDeadlinePreservesHealthyProviderAndDistinguishesEmpty() {
        FlightSupplier slow=new FlightSupplier() {
            public String id(){return "slow";}
            public List<FlightOffer> search(FlightQuery q,Instant n){try{Thread.sleep(3000);}catch(InterruptedException e){Thread.currentThread().interrupt();}return List.of();}
            public FlightOffer reprice(FlightOffer o,Instant n){return o;}
        };
        try(var pool=Executors.newFixedThreadPool(2)) {
            var gateway=new BoundedSupplierGateway(List.of(slow,new MockFlightSupplier("ok","Sample",0)),pool,100,new SimpleMeterRegistry());
            long start=System.nanoTime();
            var result=gateway.search(query("BOM"),now);
            assertThat(Duration.ofNanos(System.nanoTime()-start)).isLessThan(Duration.ofSeconds(1));
            assertThat(result.offers()).hasSize(4);
            assertThat(result.coverage().getFirst().status()).isEqualTo(SearchSnapshot.Coverage.Status.UNAVAILABLE);
            assertThat(result.coverage().getLast().status()).isEqualTo(SearchSnapshot.Coverage.Status.AVAILABLE);
            var empty=gateway.search(query("JAI"),now);
            assertThat(empty.offers()).isEmpty();
            assertThat(empty.coverage().getLast().status()).isEqualTo(SearchSnapshot.Coverage.Status.AVAILABLE);
        }
    }
    @Test void expiryAllOutageAndStablePagesAreEnforcedByApplication() {
        Map<UUID,SearchSnapshot> stored=new HashMap<>();
        SearchRepository repository=new SearchRepository() {
            public void save(SearchSnapshot s){stored.put(s.id(),s);}
            public Optional<SearchSnapshot> find(UUID id){return Optional.ofNullable(stored.get(id));}
            public Optional<FlightOffer> findOffer(UUID id){return stored.values().stream().flatMap(s->s.offers().stream()).filter(o->o.id().equals(id)).findFirst();}
            public Optional<FlightQuery> criteriaForOffer(UUID id){return stored.values().stream().filter(s->s.offers().stream().anyMatch(o->o.id().equals(id))).map(s->s.criteria()).findFirst();}
        };
        LookupLocations locations=new LookupLocations() {
            public List<Location> search(String q){return List.of();}
            public Location get(String id){return new Location(id,"Sample","Sample","IN","Asia/Kolkata");}
        };
        try(var pool=Executors.newFixedThreadPool(2)) {
            var gateway=new BoundedSupplierGateway(List.of(new MockFlightSupplier("sample","Sample",0)),pool,100,new SimpleMeterRegistry());
            var discovery=new FlightDiscovery(locations,gateway,repository,clock);
            var first=discovery.search(query("BOM"));
            var page0=discovery.page(first.snapshot().id(),0,2,false,Sort.PRICE,"");
            var page1=discovery.page(first.snapshot().id(),1,2,false,Sort.PRICE,"");
            assertThat(page0.offers()).doesNotContainAnyElementsOf(page1.offers());
            assertThat(discovery.page(first.snapshot().id(),0,2,false,Sort.PRICE,"").offers()).isEqualTo(page0.offers());
            var later=new FlightDiscovery(locations,gateway,repository,Clock.offset(clock,Duration.ofSeconds(901)));
            assertThatThrownBy(()->later.offer(first.offers().getFirst().id())).isInstanceOf(DomainFailure.class).hasMessageContaining("expired");
            assertThatThrownBy(()->later.page(first.snapshot().id(),0,5,false,Sort.PRICE,"")).isInstanceOf(DomainFailure.class).hasMessageContaining("expired");
            var nearDeparture=Clock.fixed(Instant.parse("2026-11-01T02:25:00Z"),ZoneOffset.UTC);
            var near=new FlightDiscovery(locations,gateway,repository,nearDeparture).search(query("BOM"));
            var afterDeparture=new FlightDiscovery(locations,gateway,repository,Clock.offset(nearDeparture,Duration.ofMinutes(6)));
            assertThatThrownBy(()->afterDeparture.reprice(near.offers().getFirst().id())).isInstanceOf(DomainFailure.class).hasMessageContaining("expired");
            SupplierGateway outage=new SupplierGateway() {
                public Results search(FlightQuery q,Instant n){return new Results(List.of(),List.of(new SearchSnapshot.Coverage("offline",SearchSnapshot.Coverage.Status.UNAVAILABLE)));}
                public FlightOffer reprice(FlightOffer o,Instant n){throw new UnsupportedOperationException();}
            };
            assertThatThrownBy(()->new FlightDiscovery(locations,outage,repository,clock).search(query("BOM"))).isInstanceOf(DomainFailure.class).hasMessageContaining("unavailable");
        }
    }
}

