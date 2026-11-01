package com.travbook;
import com.travbook.booking.domain.model.*;
import com.travbook.shared.domain.DomainFailure;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class BookingDomainTest {
    BookingQuote quote() {
        var departure=OffsetDateTime.parse("2027-01-10T08:00:00+05:30");var back=departure.plusDays(10);
        var terms=new FlightTerms("sample","SIM-1","Sample",List.of(new FlightTerms.Leg("DEL","BOM",departure,departure.plusMinutes(130),"Asia/Kolkata",130,0,"SIM-1"),new FlightTerms.Leg("BOM","DEL",back,back.plusMinutes(130),"Asia/Kolkata",130,0,"SIM-2")),3,"ECONOMY","Fixture baggage","Fixture policy",new FlightTerms.Money(new BigDecimal("100.00"),"INR"),new FlightTerms.Money(new BigDecimal("5.00"),"INR"));
        return new BookingQuote(UUID.randomUUID(),1,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"MOCK",Instant.parse("2026-12-01T00:02:00Z"),1,1,1,terms);
    }
    List<Traveller> travellers(){return List.of(new Traveller("Alex","Example",LocalDate.of(1990,1,1)),new Traveller("Casey","Example",LocalDate.of(2018,1,1)),new Traveller("Jamie","Example",LocalDate.of(2026,1,1)));}
    @Test void normalizedNamesAndQuotedMixedAgesArePreserved() {
        assertThat(new Traveller("  E\u0301lodie   Anne ","O’Neil",LocalDate.of(1990,1,1)).givenName()).isEqualTo("Élodie Anne");
        assertThatCode(()->quote().validateTravellers(travellers())).doesNotThrowAnyException();
    }
    @Test void returnBirthdayCategoryChangesAndDuplicateTravellersAreRejected() {
        var changed=new ArrayList<>(travellers());changed.set(2,new Traveller("Jamie","Example",LocalDate.of(2025,1,15)));
        assertThatThrownBy(()->quote().validateTravellers(changed)).isInstanceOf(DomainFailure.class);
        assertThatThrownBy(()->quote().validateTravellers(List.of(travellers().getFirst(),travellers().getFirst(),travellers().getFirst()))).isInstanceOf(DomainFailure.class);
        assertThatThrownBy(()->new Traveller("A","B",LocalDate.of(2028,1,1)).categoryAt(LocalDate.of(2027,1,1))).isInstanceOf(DomainFailure.class);
    }
    @Test void onlyValidationCanAdvanceAndTerminalValidationCannotBeRestarted() {
        var now=Instant.parse("2026-12-01T00:00:00Z");var q=quote();
        var created=new Booking(UUID.randomUUID(),q,travellers(),"a".repeat(64),now,now,1,Booking.Status.CREATED,null,UUID.randomUUID(),UUID.randomUUID());
        assertThatThrownBy(()->created.complete(Booking.Decision.VALIDATED,now)).isInstanceOf(IllegalStateException.class);
        var validating=created.start(now);assertThat(validating.version()).isEqualTo(2);
        for(int attempt=1;attempt<=8;attempt++)assertThat(created.retryDelay(attempt)).isBetween(Duration.ofSeconds(1),Duration.ofMillis(8250));
        assertThat(created.retryDelay(2)).isEqualTo(created.retryDelay(2));
        var ready=validating.complete(Booking.Decision.VALIDATED,now);assertThat(ready.status()).isEqualTo(Booking.Status.AWAITING_PAYMENT);
        assertThatThrownBy(()->ready.start(now)).isInstanceOf(IllegalStateException.class);
        assertThat(validating.evaluate(q.terms(),q.expiresAt())).isEqualTo(Booking.Decision.EXPIRED);
    }
}
