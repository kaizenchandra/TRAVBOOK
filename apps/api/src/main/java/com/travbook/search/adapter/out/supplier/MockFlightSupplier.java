package com.travbook.search.adapter.out.supplier;
import com.travbook.search.application.port.out.FlightSupplier;
import com.travbook.search.domain.model.*;
import com.travbook.shared.domain.DomainFailure;
import java.math.*;
import java.time.*;
import java.util.*;
import static com.travbook.shared.domain.DomainFailure.Code.*;
/** Original synthetic data. No commercial schedule, inventory, airline or fare claim. */
public final class MockFlightSupplier implements FlightSupplier {
    private final String id;
    private final String airline;
    private final int offset;
    public MockFlightSupplier(String id,String airline,int offset) { this.id=id; this.airline=airline; this.offset=offset; }
    public String id() { return id; }
    public List<FlightOffer> search(FlightQuery q,Instant now) {
        if(q.destination().equals("GOI") && offset>0) throw new DomainFailure(SUPPLIERS_UNAVAILABLE,"Simulated provider outage.");
        if(q.destination().equals("JAI")) return List.of();
        var offers=new ArrayList<FlightOffer>();
        for(int index=0;index<4;index++) {
            int hour=new int[]{8,11,15,19}[index];
            int stops=index%2;
            int minutes=130+offset+index*15+stops*75;
            var legs=new ArrayList<FlightOffer.Leg>();
            legs.add(leg(q.origin(),q.destination(),q.departureDate(),hour,minutes,stops,index));
            if(q.returnDate()!=null) {
                var inbound=leg(q.destination(),q.origin(),q.returnDate(),hour+1,minutes,stops,index+10);
                var earliestReturn=legs.getFirst().arrival().plusMinutes(60);
                if(inbound.departure().isBefore(earliestReturn)) {
                    if(!earliestReturn.toLocalDate().equals(q.returnDate())) continue;
                    inbound=new FlightOffer.Leg(inbound.origin(),inbound.destination(),earliestReturn,earliestReturn.plusMinutes(minutes),
                        inbound.timezone(),minutes,stops,inbound.flightNumber());
                }
                legs.add(inbound);
            }
            if(legs.getFirst().departure().toInstant().isBefore(now)) continue;
            BigDecimal factor=BigDecimal.valueOf(q.adults()).add(BigDecimal.valueOf(q.children()).multiply(new BigDecimal("0.75")))
                .add(BigDecimal.valueOf(q.infants()).multiply(new BigDecimal("0.10")));
            BigDecimal base=BigDecimal.valueOf(3800+offset*20+index*420).multiply(factor)
                .multiply(BigDecimal.valueOf(q.cabin().ordinal()+1)).multiply(BigDecimal.valueOf(legs.size())).setScale(2,RoundingMode.HALF_UP);
            Money taxes=Money.inr(base.multiply(new BigDecimal("0.05")).setScale(2,RoundingMode.HALF_UP));
            offers.add(new FlightOffer(UUID.randomUUID(),id,"SIM-"+id+"-"+(index+1),airline,legs,Money.inr(base),taxes,q.passengers(),q.cabin().name(),
                "Simulated allowance: 7 kg cabin + 15 kg checked per seated traveller.",
                (index<2?com.travbook.shared.domain.MockCancellationPolicy.FULL:com.travbook.shared.domain.MockCancellationPolicy.NONE).text(),
                now,now.plusSeconds(900)));
        }
        return offers;
    }
    private FlightOffer.Leg leg(String from,String to,LocalDate date,int hour,int minutes,int stops,int index) {
        var depart=date.atTime(hour,0).atZone(ZoneId.of("Asia/Kolkata")).toOffsetDateTime();
        return new FlightOffer.Leg(from,to,depart,depart.plusMinutes(minutes),"Asia/Kolkata",minutes,stops,"SIM-"+(100+offset+index));
    }
    public FlightOffer reprice(FlightOffer offer,Instant now) {
        if(!id.equals(offer.supplier())) throw new IllegalArgumentException("Wrong provider");
        if(offer.supplierReference().endsWith("-4")) throw new DomainFailure(OFFER_UNAVAILABLE,"This simulated fare is no longer available. Choose another flight.");
        BigDecimal extra=offer.supplierReference().endsWith("-2") ? new BigDecimal("250.00") : BigDecimal.ZERO;
        return offer.repriced(Money.inr(offer.base().amount().add(extra)),now);
    }
}

