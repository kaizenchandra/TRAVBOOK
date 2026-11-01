package com.travbook.hotels.domain.model;

import com.travbook.shared.domain.DomainFailure;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static com.travbook.shared.domain.DomainFailure.Code.VALIDATION_FAILED;

public record HotelCriteria(String destination, LocalDate checkIn, LocalDate checkOut, List<Occupancy> rooms) {
    public record Occupancy(int adults,List<Integer> childAges) {
        public Occupancy {
            if(adults<1||adults>3||childAges==null||childAges.size()>2||childAges.stream().anyMatch(a->a==null||a<0||a>17))
                throw new DomainFailure(VALIDATION_FAILED,"Each room needs 1–3 adults (18+) and at most two children aged 0–17 at check-in.");
            childAges=List.copyOf(childAges);
        }
    }
    public HotelCriteria {
        if(destination==null||destination.strip().length()<2||destination.length()>80||checkIn==null||checkOut==null
            ||ChronoUnit.DAYS.between(checkIn,checkOut)<1||ChronoUnit.DAYS.between(checkIn,checkOut)>30
            ||rooms==null||rooms.isEmpty()||rooms.size()>4||rooms.stream().anyMatch(Objects::isNull))
            throw new DomainFailure(VALIDATION_FAILED,"Choose a destination, a stay of 1–30 nights and 1–4 rooms.");
        destination=destination.strip();rooms=List.copyOf(rooms);
    }
    public void validateDates(Clock clock) {
        LocalDate today=LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata")));
        if(checkIn.isBefore(today)||checkOut.isAfter(today.plusDays(330)))
            throw new DomainFailure(VALIDATION_FAILED,"Stay dates must be today through 330 days ahead in the property's timezone.");
    }
    public Instant lastSearchInstant() {return checkIn.plusDays(1).atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant();}
}
