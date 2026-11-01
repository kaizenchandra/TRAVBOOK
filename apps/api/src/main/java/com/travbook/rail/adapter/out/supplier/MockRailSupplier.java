package com.travbook.rail.adapter.out.supplier;
import com.travbook.rail.application.port.out.RailSupplier;
import com.travbook.rail.domain.model.Rail.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
public final class MockRailSupplier implements RailSupplier {
    private final String id;private final int variant;
    public MockRailSupplier(String id,int variant){this.id=id;this.variant=variant;}public String id(){return id;}
    public List<Journey> search(Criteria q,Station origin,Station destination,Instant now){
        if(q.destination().equals("HWH")||q.destination().equals("MAO")&&variant==1)throw new IllegalStateException("Simulated railway supplier outage");
        if(q.destination().equals("JP"))return List.of();
        var result=new ArrayList<Journey>();
        for(int i=0;i<2;i++){
            List<DayOfWeek> days=i==0?List.of(DayOfWeek.values()):List.of(DayOfWeek.MONDAY,DayOfWeek.TUESDAY,DayOfWeek.WEDNESDAY,DayOfWeek.THURSDAY,DayOfWeek.FRIDAY,DayOfWeek.SATURDAY);
            if(!days.contains(q.departureDate().getDayOfWeek()))continue;
            var depart=q.departureDate().atTime(i==0?8:18,30+variant*10).atZone(ZoneId.of(origin.timezone())).toOffsetDateTime();if(!depart.toInstant().isAfter(now))continue;
            int duration=690+i*210+variant*30;var arrival=depart.plusMinutes(duration);var availability=new ArrayList<Availability>();
            for(var c:TravelClass.values())availability.add(observe(c,q.quota(),i,depart,now,false));
            result.add(new Journey(UUID.randomUUID(),id,"MOCK-"+(variant==0?"A":"B")+(i+1),i==0?"Sample Meridian Express":"Sample Starlight Express",origin,destination,depart,arrival,duration,days,List.of(new Stop(origin,depart,depart),new Stop(destination,arrival,arrival)),availability));
        }
        return result;
    }
    public Availability availability(Journey journey,TravelClass travelClass,Quota quota,Instant now){journey.requireFuture(now);return observe(travelClass,quota,journey.number().endsWith("1")?0:1,journey.departure(),now,true);}
    private Availability observe(TravelClass c,Quota quota,int service,OffsetDateTime departure,Instant now,boolean fresh){
        Status status=service==0?switch(c){case SL->Status.AVAILABLE;case THREE_AC->Status.RAC;case TWO_AC->Status.WAITLIST;}:switch(c){case SL->Status.WAITLIST;case THREE_AC->Status.AVAILABLE;case TWO_AC->Status.UNAVAILABLE;};
        int position=switch(status){case AVAILABLE->24-variant*6;case RAC->8+variant*4;case WAITLIST->18+service*20;default->0;};
        Instant opens=quota==Quota.TATKAL?departure.toLocalDate().minusDays(1).atTime(8,0).atZone(ZoneId.of("Asia/Kolkata")).toInstant():null;
        if(opens!=null&&now.isBefore(opens)){status=Status.NOT_OPEN;position=0;}
        else if(fresh&&service==0&&c==TravelClass.SL){status=Status.RAC;position=3;}
        BigDecimal base=new BigDecimal(switch(c){case SL->"650";case THREE_AC->"1700";case TWO_AC->"2450";}).add(BigDecimal.valueOf(variant*75L));
        if(quota==Quota.TATKAL)base=base.add(new BigDecimal("200"));if(fresh&&c==TravelClass.SL)base=base.add(new BigDecimal("50"));
        BigDecimal fees=new BigDecimal("30.00");Instant expiry=now.plusSeconds(60);if(departure.toInstant().isBefore(expiry))expiry=departure.toInstant();
        return new Availability(c,quota,status,position,Money.inr(base),Money.inr(fees),Money.inr(base.add(fees)),now,expiry,opens,
            List.of("MOCK fare for one adult only; no passenger eligibility or concession validation has occurred.","RAC and waitlist do not guarantee a confirmed berth. AVAILABLE is an observation, not a reservation.","Tatkal fixture opens at 08:00 IST the day before departure; this is NOT an official railway rule.","Ticketing, PNR, berth assignment and railway credential collection are disabled pending authorized integration."));
    }
}
