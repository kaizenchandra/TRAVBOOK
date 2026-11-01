package com.travbook.rail.domain.model;
import com.travbook.shared.domain.DomainFailure;
import java.math.*;
import java.time.*;
import java.util.*;
import static com.travbook.shared.domain.DomainFailure.Code.*;
/** Railway values intentionally separate availability observations from ticket/reservation states. */
public final class Rail {
    private Rail() {}
    public enum TravelClass {SL,THREE_AC,TWO_AC}
    public enum Quota {GENERAL,TATKAL}
    public enum Status {AVAILABLE,RAC,WAITLIST,UNAVAILABLE,NOT_OPEN}
    public record Station(String code,String name,String city,String timezone) {}
    public record Criteria(String origin,String destination,LocalDate departureDate,Quota quota){
        public Criteria {if(origin==null||destination==null||!origin.matches("[A-Z]{2,5}")||!destination.matches("[A-Z]{2,5}")||origin.equals(destination)||departureDate==null||quota==null)throw new DomainFailure(VALIDATION_FAILED,"Choose different origin and destination stations, a date and a supported quota.");}
        public void validate(Clock clock){LocalDate today=LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata")));if(departureDate.isBefore(today)||departureDate.isAfter(today.plusDays(60)))throw new DomainFailure(VALIDATION_FAILED,"This MOCK search supports today through 60 days ahead; this is not an official advance-booking rule.");}
    }
    public record Money(BigDecimal amount,String currency){public Money {if(amount==null||amount.signum()<0||!"INR".equals(currency))throw new IllegalArgumentException("Invalid rail fare");amount=amount.setScale(2,RoundingMode.UNNECESSARY);}public static Money inr(BigDecimal amount){return new Money(amount,"INR");}}
    public record Availability(TravelClass travelClass,Quota quota,Status status,int position,Money base,Money fees,Money total,Instant observedAt,Instant validUntil,Instant quotaOpensAt,List<String> restrictions){
        public Availability {restrictions=List.copyOf(restrictions);if(travelClass==null||quota==null||status==null||position<0||position>999||!validUntil.isAfter(observedAt)||base.amount().add(fees.amount()).compareTo(total.amount())!=0||((status==Status.UNAVAILABLE||status==Status.NOT_OPEN)&&position!=0)||((status==Status.AVAILABLE||status==Status.RAC||status==Status.WAITLIST)&&position==0))throw new IllegalArgumentException("Invalid railway observation");}
        public boolean differs(Availability other){return travelClass!=other.travelClass||quota!=other.quota||status!=other.status||position!=other.position||!total.equals(other.total);}
    }
    public record Stop(Station station,OffsetDateTime arrival,OffsetDateTime departure) {}
    public record Journey(UUID id,String supplier,String number,String name,Station origin,Station destination,OffsetDateTime departure,OffsetDateTime arrival,int durationMinutes,List<DayOfWeek> operatingDays,List<Stop> route,List<Availability> availability){
        public Journey {operatingDays=List.copyOf(operatingDays);route=List.copyOf(route);availability=List.copyOf(availability);if(id==null||!number.startsWith("MOCK-")||!arrival.isAfter(departure)||Duration.between(departure,arrival).toMinutes()!=durationMinutes||!operatingDays.contains(departure.getDayOfWeek()))throw new IllegalArgumentException("Invalid MOCK train journey");}
        public void requireFuture(Instant now){if(!departure.toInstant().isAfter(now))throw new DomainFailure(OFFER_EXPIRED,"This sample train has departed. Search again.");}
    }
    public record Coverage(String supplier,boolean available) {}
    public record Snapshot(UUID id,Criteria criteria,List<Journey> journeys,List<Coverage> coverage,Instant createdAt,Instant expiresAt){public Snapshot {journeys=List.copyOf(journeys);coverage=List.copyOf(coverage);}public void requireFresh(Instant now){if(!expiresAt.isAfter(now))throw new DomainFailure(SEARCH_EXPIRED,"This train search expired. Search again for current availability.");}}
}
