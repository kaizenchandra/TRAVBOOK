package com.travbook.flightprovider.domain.model;

import com.travbook.shared.domain.DomainFailure;
import com.travbook.shared.domain.TravelMoney;
import java.time.*;
import java.util.*;
import static com.travbook.shared.domain.DomainFailure.Code.*;

/** Policy of the explicitly fictional provider, never an airline or GDS contract. */
public final class FlightFixture {
    private FlightFixture() {}
    public static final String NOTICE="MOCK ONLY — no real funds or inventory. NOT VALID FOR TRAVEL.";
    public enum Meal { NONE, VEG, STANDARD }
    public enum Baggage { NONE, KG20 }
    public record Flight(String id,Instant departure,String origin,String destination,String timezone,Instant checkInOpens,Instant checkInCloses) {}
    public record Selection(String passengerId,int age,String seat,Meal meal,Baggage baggage) {
        public Selection {
            if(passengerId==null || !passengerId.matches("P[1-6]") || age<2 || age>120 || seat==null || !seat.matches("[1-3][A-F]") || meal==null || baggage==null)
                throw invalid("Use synthetic passenger slots P1–P6, ages 2–120 and supported seats/extras.");
            if(seat.equals("3F")) throw invalid("Seat 3F is reserved for crew in this fixture.");
            if(seat.startsWith("3") && age<18) throw invalid("The fixture exit row requires age 18 or older.");
        }
    }
    public record Seat(String id,int row,String column,boolean exitRow,boolean available,TravelMoney price) {}
    public record SeatMap(Flight flight,List<Seat> seats,Instant asOf) {}
    public record Ancillary(String code,String kind,String description,TravelMoney price) {}
    public record Catalog(Flight flight,List<Ancillary> items) {}
    public static Flight flight(String id) {
        if(id==null || !id.matches("MOCK-FA-\\d{4}-\\d{2}-\\d{2}")) throw new DomainFailure(NOT_FOUND,"Fictional flight not found.");
        try {
            var date=LocalDate.parse(id.substring(8));
            var departure=date.atTime(18,0).atZone(ZoneId.of("Asia/Kolkata")).toInstant();
            return new Flight(id,departure,"DEL","BOM","Asia/Kolkata",departure.minusSeconds(86400),departure.minusSeconds(3600));
        } catch(DateTimeException e) {throw new DomainFailure(NOT_FOUND,"Fictional flight not found.");}
    }
    public static void bookable(Flight flight,Instant now) {
        if(!now.isBefore(flight.checkInCloses())) throw new DomainFailure(OFFER_EXPIRED,"The fictional departure booking/check-in cutoff has passed.");
        var today=now.atZone(ZoneId.of("Asia/Kolkata")).toLocalDate();
        if(flight.departure().atZone(ZoneId.of("Asia/Kolkata")).toLocalDate().isAfter(today.plusDays(90))) throw invalid("Fixture dates are limited to 90 days ahead.");
    }
    public static List<Selection> selections(List<Selection> selections) {
        if(selections==null || selections.isEmpty() || selections.size()>6 || selections.stream().anyMatch(Objects::isNull)) throw invalid("Select one to six synthetic passengers.");
        var ids=new HashSet<String>();var seats=new HashSet<String>();
        for(var s:selections) if(!ids.add(s.passengerId()) || !seats.add(s.seat())) throw invalid("Passenger slots and seats must be unique.");
        if(selections.stream().noneMatch(s->s.age()>=18)) throw invalid("At least one adult is required.");
        return selections.stream().sorted((a,b)->a.passengerId().compareTo(b.passengerId())).toList();
    }
    public static TravelMoney seatPrice(String seat) {return TravelMoney.inr(seat.startsWith("1")?600:seat.startsWith("3")?400:200);}
    public static TravelMoney mealPrice(Meal meal) {return TravelMoney.inr(switch(meal){case NONE->0;case VEG->250;case STANDARD->300;});}
    public static TravelMoney baggagePrice(Baggage bag) {return TravelMoney.inr(bag==Baggage.KG20?1000:0);}
    public static SeatMap seats(Flight flight,Set<String> occupied,Instant now) {
        var seats=new ArrayList<Seat>();
        for(int row=1;row<=3;row++) for(char col='A';col<='F';col++) {String id=""+row+col;seats.add(new Seat(id,row,""+col,row==3,!id.equals("3F")&&!occupied.contains(id),seatPrice(id)));}
        return new SeatMap(flight,List.copyOf(seats),now);
    }
    public static Catalog ancillaries(Flight flight) {return new Catalog(flight,List.of(
        new Ancillary("VEG","MEAL","Fictional vegetarian meal; select NONE for no meal",mealPrice(Meal.VEG)),
        new Ancillary("STANDARD","MEAL","Fictional standard meal",mealPrice(Meal.STANDARD)),
        new Ancillary("NONE","BAGGAGE","No additional checked baggage",baggagePrice(Baggage.NONE)),
        new Ancillary("KG20","BAGGAGE","Fictional additional 20 kg allowance",baggagePrice(Baggage.KG20))));}
    public static DomainFailure invalid(String message){return new DomainFailure(VALIDATION_FAILED,message);}
    public static DomainFailure conflict(String message){return new DomainFailure(BOOKING_CONFLICT,message);}
    public static void consent(boolean consent){if(!consent)throw invalid("Explicit consent to the simulated operation is required.");}
}
