package com.travbook.search.domain.model;
import com.travbook.shared.domain.DomainFailure;
import java.time.*;
import static com.travbook.shared.domain.DomainFailure.Code.VALIDATION_FAILED;
public record FlightQuery(String origin, String destination, LocalDate departureDate, LocalDate returnDate,
                          int adults, int children, int infants, Cabin cabin) {
    public enum Cabin { ECONOMY, PREMIUM_ECONOMY, BUSINESS, FIRST }
    public FlightQuery {
        if(origin == null || destination == null || !origin.matches("[A-Z]{3}") || !destination.matches("[A-Z]{3}") || origin.equals(destination))
            throw new DomainFailure(VALIDATION_FAILED,"Choose two different airports.");
        if(departureDate == null || cabin == null) throw new DomainFailure(VALIDATION_FAILED,"Departure date and cabin are required.");
        if(adults < 1 || adults > 9 || children < 0 || children > 8 || infants < 0 || infants > adults || adults + children > 9)
            throw new DomainFailure(VALIDATION_FAILED,"Choose 1–9 seated travellers, at least one adult, and at most one infant per adult.");
        if(returnDate != null && returnDate.isBefore(departureDate)) throw new DomainFailure(VALIDATION_FAILED,"Return must be on or after departure.");
    }
    public void validateDates(Clock clock) {
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata")));
        if(departureDate.isBefore(today) || departureDate.isAfter(today.plusDays(330)) ||
            (returnDate != null && returnDate.isAfter(today.plusDays(330))))
            throw new DomainFailure(VALIDATION_FAILED,"Travel dates must be today through 330 days from today.");
    }
    public int passengers() { return adults + children + infants; }
}

