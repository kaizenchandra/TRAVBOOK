package com.travbook;
import com.travbook.rail.domain.model.Rail.*;
import com.travbook.rail.application.port.out.RailSupplier;
import com.travbook.rail.adapter.out.supplier.*;
import com.travbook.shared.domain.DomainFailure;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
class RailDomainTest {
    final Station from=new Station("NDLS","New Delhi","Delhi","Asia/Kolkata"),to=new Station("MMCT","Mumbai Central","Mumbai","Asia/Kolkata");
    final Instant now=Instant.parse("2026-11-01T00:00:00Z");final MockRailSupplier supplier=new MockRailSupplier("sample",0);
    Criteria criteria(LocalDate day,Quota quota){return new Criteria("NDLS","MMCT",day,quota);}
    @Test void invalidRoutesAndDemonstrationDateWindowAreRejected(){
        assertThatThrownBy(()->new Criteria("NDLS","NDLS",LocalDate.parse("2026-11-03"),Quota.GENERAL)).isInstanceOf(DomainFailure.class);
        assertThatThrownBy(()->criteria(LocalDate.parse("2026-10-31"),Quota.GENERAL).validate(Clock.fixed(now,ZoneOffset.UTC))).isInstanceOf(DomainFailure.class);
        assertThatThrownBy(()->criteria(LocalDate.parse("2027-02-01"),Quota.GENERAL).validate(Clock.fixed(now,ZoneOffset.UTC))).isInstanceOf(DomainFailure.class);
    }
    @Test void operatingDaysAndOvernightJourneyDatesAreExplicit(){
        var sunday=supplier.search(criteria(LocalDate.parse("2026-11-01"),Quota.GENERAL),from,to,now);assertThat(sunday).hasSize(1);
        var monday=supplier.search(criteria(LocalDate.parse("2026-11-02"),Quota.GENERAL),from,to,now);assertThat(monday).hasSize(2);assertThat(monday.get(1).arrival().toLocalDate()).isEqualTo(LocalDate.parse("2026-11-03"));assertThat(monday.get(1).arrival().getOffset()).isEqualTo(ZoneOffset.ofHoursMinutes(5,30));
    }
    @Test void availabilityRacWaitlistAndFareChangeNeverBecomeTicketConfirmation(){
        var j=supplier.search(criteria(LocalDate.parse("2026-11-03"),Quota.GENERAL),from,to,now).getFirst();assertThat(j.availability()).extracting(a->a.status()).containsExactly(Status.AVAILABLE,Status.RAC,Status.WAITLIST);
        var a=supplier.availability(j,TravelClass.SL,Quota.GENERAL,now);assertThat(a.status()).isEqualTo(Status.RAC);assertThat(a.position()).isEqualTo(3);assertThat(a.total().amount()).isEqualByComparingTo("730.00");assertThat(a.differs(j.availability().getFirst())).isTrue();assertThat(a.restrictions()).anyMatch(v->v.contains("do not guarantee"));
        assertThat(supplier.availability(j,TravelClass.SL,Quota.GENERAL,now)).isEqualTo(a);
        assertThatThrownBy(()->new Availability(a.travelClass(),a.quota(),Status.RAC,0,a.base(),a.fees(),a.total(),a.observedAt(),a.validUntil(),null,a.restrictions())).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void syntheticTatkalWindowHonorsExactOpeningAndDepartureBoundary(){
        var j=supplier.search(criteria(LocalDate.parse("2026-11-03"),Quota.TATKAL),from,to,now).getFirst();var before=j.availability().getFirst();assertThat(before.status()).isEqualTo(Status.NOT_OPEN);assertThat(before.position()).isZero();assertThat(before.quotaOpensAt()).isEqualTo(Instant.parse("2026-11-02T02:30:00Z"));
        assertThat(supplier.availability(j,TravelClass.THREE_AC,Quota.TATKAL,before.quotaOpensAt().minusMillis(1)).status()).isEqualTo(Status.NOT_OPEN);
        assertThat(supplier.availability(j,TravelClass.THREE_AC,Quota.TATKAL,before.quotaOpensAt()).status()).isEqualTo(Status.RAC);
        var last=supplier.availability(j,TravelClass.TWO_AC,Quota.GENERAL,j.departure().toInstant().minusSeconds(10));assertThat(last.validUntil()).isEqualTo(j.departure().toInstant());
        assertThatThrownBy(()->supplier.availability(j,TravelClass.SL,Quota.GENERAL,j.departure().toInstant())).isInstanceOf(DomainFailure.class);
    }
    @Test void slowProviderDoesNotMaskHealthyResultsAndFreshReadsAreBounded(){
        RailSupplier slow=new RailSupplier(){public String id(){return "slow";}public List<Journey> search(Criteria q,Station o,Station d,Instant n){try{Thread.sleep(10_000);}catch(InterruptedException e){Thread.currentThread().interrupt();}return List.of();}public Availability availability(Journey j,TravelClass c,Quota q,Instant n){search(null,null,null,n);return j.availability().getFirst();}};
        try(var pool=Executors.newFixedThreadPool(2)){var gateway=new BoundedRailGateway(List.of(slow,supplier),pool,150,new SimpleMeterRegistry());long start=System.nanoTime();var r=gateway.search(criteria(LocalDate.parse("2026-11-03"),Quota.GENERAL),from,to,now);assertThat(Duration.ofNanos(System.nanoTime()-start)).isLessThan(Duration.ofSeconds(2));assertThat(r.journeys()).hasSize(2);assertThat(r.coverage()).extracting(c->c.available()).containsExactly(false,true);
            var j=new MockRailSupplier("slow",0).search(criteria(LocalDate.parse("2026-11-03"),Quota.GENERAL),from,to,now).getFirst();assertThatThrownBy(()->gateway.availability(j,TravelClass.SL,Quota.GENERAL,now)).isInstanceOf(DomainFailure.class).hasMessageContaining("time budget");}
    }
}
