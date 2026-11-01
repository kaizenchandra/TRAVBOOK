package com.travbook.hotels.adapter.out.supplier;
import com.travbook.hotels.application.port.out.HotelSupplier;
import com.travbook.hotels.domain.model.*;
import com.travbook.shared.domain.DomainFailure;
import java.math.*;
import java.time.*;
import java.util.*;
import static com.travbook.shared.domain.DomainFailure.Code.*;
public final class MockHotelSupplier implements HotelSupplier {
    private final String id;private final int variant;
    private final com.travbook.hotels.application.port.out.HotelRoomStock stock;
    public MockHotelSupplier(String id,int variant){this(id,variant,(p,plan,from,to)->com.travbook.hotels.adapter.out.supplier.MockRoomPolicy.capacity(com.travbook.hotels.adapter.out.supplier.MockRoomPolicy.group(plan)));}
    public MockHotelSupplier(String id,int variant,com.travbook.hotels.application.port.out.HotelRoomStock stock){this.id=id;this.variant=variant;this.stock=stock;}
    public String id(){return id;}
    public Inventory search(HotelCriteria query,Instant now) {
        String destination=query.destination().toLowerCase(Locale.ROOT);
        if(destination.equals("shimla")||variant==1&&destination.equals("goa"))throw new IllegalStateException("Simulated hotel supplier outage");
        var properties=catalog().stream().filter(p->p.matches(query.destination())).toList();var offers=new ArrayList<HotelOffer>();
        for(var p:properties)for(int room=0;room<query.rooms().size();room++)for(var plan:HotelOffer.Plan.values()) {
            int adults=plan==HotelOffer.Plan.FAMILY?3:2,children=plan==HotelOffer.Plan.STUDIO?0:2;
            if(query.rooms().get(room).adults()<=adults&&query.rooms().get(room).childAges().size()<=children&&stock.remaining(p.id(),plan,query.checkIn(),query.checkOut())>0)
                offers.add(rate(UUID.randomUUID(),p.id(),query,room,plan,now,false));
        }
        return new Inventory(properties,offers);
    }
    public List<HotelOffer> reprice(HotelCriteria query,List<HotelOffer> selection,Instant now) {
        query.validateDates(Clock.fixed(now,ZoneOffset.UTC));
        if(selection.stream().anyMatch(o->o.planCode()==HotelOffer.Plan.STUDIO))
            throw new DomainFailure(OFFER_UNAVAILABLE,"The simulated studio inventory is no longer available. Choose another room plan.");
        // FLEX and VALUE share the same physical standard-room pool; rates are not separate inventory.
        if(selection.stream().filter(o->o.planCode()==HotelOffer.Plan.FLEX||o.planCode()==HotelOffer.Plan.VALUE).count()>2)
            throw new DomainFailure(OFFER_UNAVAILABLE,"The selected standard-room combination is unavailable. Choose family rooms or fewer rooms and recheck.");
        for(var offer:selection){String group=com.travbook.hotels.adapter.out.supplier.MockRoomPolicy.group(offer.planCode());long requested=selection.stream().filter(o->com.travbook.hotels.adapter.out.supplier.MockRoomPolicy.group(o.planCode()).equals(group)).count();if(stock.remaining(offer.propertyId(),offer.planCode(),query.checkIn(),query.checkOut())<requested)throw new DomainFailure(OFFER_UNAVAILABLE,"Selected rooms are no longer available for every night. Choose another stay or room plan.");}
        return selection.stream().map(o->rate(o.id(),o.propertyId(),query,o.roomIndex(),o.planCode(),now,true,o.paymentCondition().equals("MOCK_PAY_AT_PROPERTY"))).toList();
    }
    public List<HotelOffer> changeRates(UUID reservation,String property,HotelCriteria criteria,List<HotelOffer.Plan> plans,List<UUID> ids,boolean deferred,Instant now){
     criteria.validateDates(Clock.fixed(now,ZoneOffset.UTC));if(catalog().stream().noneMatch(p->p.id().equals(property)&&p.matches(criteria.destination()))||plans.size()!=criteria.rooms().size()||ids.size()!=plans.size())throw new DomainFailure(VALIDATION_FAILED,"Invalid property or room selection.");
     var result=new ArrayList<HotelOffer>();for(int i=0;i<plans.size();i++){var plan=plans.get(i);if(plan==HotelOffer.Plan.STUDIO)throw new DomainFailure(OFFER_UNAVAILABLE,"Studio changes are unavailable.");String group=MockRoomPolicy.group(plan);long needed=plans.stream().filter(p->MockRoomPolicy.group(p).equals(group)).count();if(stock.remaining(property,plan,criteria.checkIn(),criteria.checkOut(),reservation)<needed)throw new DomainFailure(OFFER_UNAVAILABLE,"Replacement rooms are unavailable for every night.");var occupancy=criteria.rooms().get(i);if(occupancy.adults()>(plan==HotelOffer.Plan.FAMILY?3:2)||occupancy.childAges().size()>2)throw new DomainFailure(VALIDATION_FAILED,"Room plan capacity does not match occupancy.");result.add(rate(ids.get(i),property,criteria,i,plan,now,true,deferred));}return List.copyOf(result);
    }
    private HotelOffer rate(UUID offerId,String propertyId,HotelCriteria query,int room,HotelOffer.Plan plan,Instant now,boolean reprice) {
        return rate(offerId,propertyId,query,room,plan,now,reprice,false);
    }
    private HotelOffer rate(UUID offerId,String propertyId,HotelCriteria query,int room,HotelOffer.Plan plan,Instant now,boolean reprice,boolean deferred) {
        if(deferred&&plan!=HotelOffer.Plan.FLEX)throw new DomainFailure(OFFER_UNAVAILABLE,"Pay at property is supported only by the simulated FLEX room plan.");
        var occupancy=query.rooms().get(room);boolean flexible=plan==HotelOffer.Plan.FLEX||plan==HotelOffer.Plan.FAMILY;
        BigDecimal base=new BigDecimal(switch(plan){case FLEX->"4000";case VALUE->"3200";case FAMILY->"5200";case STUDIO->"2800";}).add(BigDecimal.valueOf(variant*350L));
        if(occupancy.adults()==3)base=base.add(new BigDecimal("800"));
        for(int age:occupancy.childAges())base=base.add(new BigDecimal(age<3?"0":age<12?"500":"900"));
        if(reprice&&plan==HotelOffer.Plan.FAMILY)base=base.add(new BigDecimal("250"));
        var nights=new ArrayList<HotelOffer.Night>();BigDecimal subtotal=BigDecimal.ZERO;
        for(LocalDate day=query.checkIn();day.isBefore(query.checkOut());day=day.plusDays(1)) {
            BigDecimal nightly=base.add(day.getDayOfWeek()==DayOfWeek.SATURDAY?new BigDecimal("400"):BigDecimal.ZERO);
            nights.add(new HotelOffer.Night(day,HotelMoney.inr(nightly)));subtotal=subtotal.add(nightly);
        }
        BigDecimal taxes=subtotal.multiply(new BigDecimal("0.10")).setScale(2,RoundingMode.HALF_UP),fees=new BigDecimal("100.00");
        Instant deadline=flexible?query.checkIn().minusDays(2).atTime(14,0).atZone(ZoneId.of("Asia/Kolkata")).toInstant():null;
        boolean refundable=deadline!=null&&deadline.isAfter(now);
        String policy=flexible?"MOCK hotel policy: full stay total, including simulated taxes and fees, refundable before "+deadline+" (48 hours before local check-in). No refund at or after that deadline.":"MOCK hotel policy: no refund of the stay total, including simulated taxes and fees.";
        if(deferred){deadline=query.checkIn().atTime(14,0).atZone(ZoneId.of("Asia/Kolkata")).toInstant();refundable=true;policy="MOCK pay-at-property: no deposit or platform collection. Full total due at property. Free cancellation before local check-in; no refund because nothing was collected. No automatic no-show charge.";}
        Instant expiry=now.plusSeconds(reprice?300:900);if(query.lastSearchInstant().isBefore(expiry))expiry=query.lastSearchInstant();
        return new HotelOffer(offerId,propertyId,id,room,plan==HotelOffer.Plan.FAMILY?"Family room":plan==HotelOffer.Plan.STUDIO?"Studio":"Standard double",plan,
            flexible?HotelOffer.Meal.BREAKFAST:HotelOffer.Meal.ROOM_ONLY,plan==HotelOffer.Plan.FAMILY?3:2,plan==HotelOffer.Plan.STUDIO?0:2,occupancy,
            List.of("WIFI","AIR_CONDITIONING","PRIVATE_BATHROOM"),nights,HotelMoney.inr(subtotal),HotelMoney.inr(taxes),HotelMoney.inr(fees),HotelMoney.inr(subtotal.add(taxes).add(fees)),refundable,
            deadline,policy,deferred?"MOCK_PAY_AT_PROPERTY":"MOCK_PAY_NOW",now,expiry);
    }
    private List<HotelProperty> catalog() {
        var result=new ArrayList<HotelProperty>();
        for(String city:List.of("Delhi","Mumbai","Goa","Bengaluru"))for(int kind=0;kind<2;kind++) {
            boolean home=kind==1;String name=(variant==0?"Lotus":"Cove")+(home?" Courtyard Stay":" Garden Hotel");
            String locality=city.equals("Goa")?"Panaji":"Central district";
            result.add(new HotelProperty(id+"-"+city.toLowerCase(Locale.ROOT)+"-"+kind,id,name,home?HotelProperty.Type.HOMESTAY:HotelProperty.Type.HOTEL,
                city,locality,"12 Sample Lane, "+locality+", "+city,"Sample riverside park","Asia/Kolkata",
                "A fictional "+(home?"hosted courtyard stay":"garden hotel")+" for exploring room plans, family occupancy and clear stay pricing. All property details and illustrations are synthetic.",
                home?3:variant==0?5:4,new BigDecimal(home?"4.2":variant==0?"4.8":"4.5"),0,"Synthetic rating for this demonstration; no verified guest reviews.",
                home?List.of("WIFI","PARKING"):List.of("WIFI","POOL","PARKING"),
                List.of(new HotelProperty.Image("/hotels/garden.svg","Illustration of a sample garden property","Original Travbook illustration — not a property photograph"),
                    new HotelProperty.Image("/hotels/room.svg","Illustration of a sample guest room","Original Travbook illustration — not a property photograph")),
                "14:00","11:00",List.of("At least one adult aged 18 or over in every room.","Children's ages are their ages at check-in; 0–2 free, 3–11 and 12–17 have different simulated nightly supplements.","Non-smoking rooms. No pets. No whole-property rental in this fixture.")));
        }
        return result;
    }
}
