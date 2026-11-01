package com.travbook.flightprovider.domain.model;

import com.travbook.shared.domain.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public record ProviderQuote(UUID id,UUID owner,int version,FlightFixture.Flight flight,List<FlightFixture.Selection> selections,
    List<Line> lines,TravelMoney total,String cancellationPolicy,Instant createdAt,Instant expiresAt) {
    public static final String POLICY="MOCK-FP-FULL-BEFORE-CHECKIN-V1";
    public ProviderQuote {selections=List.copyOf(selections);lines=List.copyOf(lines);}
    public record Line(String passengerId,TravelMoney base,TravelMoney tax,TravelMoney seat,TravelMoney meal,TravelMoney baggage,TravelMoney total) {}
    public static ProviderQuote create(UUID owner,FlightFixture.Flight flight,List<FlightFixture.Selection> input,Instant now) {
        FlightFixture.bookable(flight,now);var selections=FlightFixture.selections(input);var lines=new ArrayList<Line>();
        for(var s:selections) {
            var base=TravelMoney.inr(4000);var tax=TravelMoney.inr(200);var seat=FlightFixture.seatPrice(s.seat());var meal=FlightFixture.mealPrice(s.meal());var baggage=FlightFixture.baggagePrice(s.baggage());
            var total=TravelMoney.inr(base.decimal().add(tax.decimal()).add(seat.decimal()).add(meal.decimal()).add(baggage.decimal()));
            lines.add(new Line(s.passengerId(),base,tax,seat,meal,baggage,total));
        }
        var expiry=now.plusSeconds(600).isBefore(flight.checkInCloses())?now.plusSeconds(600):flight.checkInCloses();
        return new ProviderQuote(UUID.randomUUID(),owner,1,flight,selections,lines,TravelMoney.inr(lines.stream().map(l->l.total().decimal()).reduce(BigDecimal.ZERO,(a,b)->a.add(b))),POLICY,now,expiry);
    }
    public void accept(int acceptedVersion,TravelMoney acceptedTotal,boolean consent,Instant now) {
        FlightFixture.consent(consent);
        if(acceptedVersion!=version)throw new DomainFailure(DomainFailure.Code.QUOTE_VERSION_CONFLICT,"Accept the current immutable quote version.");
        if(!total.equals(acceptedTotal))throw FlightFixture.conflict("Explicit acceptance must match the exact quoted total and currency.");
        if(!now.isBefore(expiresAt))throw new DomainFailure(DomainFailure.Code.QUOTE_EXPIRED,"The fictional quotation has expired; request a new quote.");
        FlightFixture.bookable(flight,now);
    }
    public ProviderQuote discounted(FlightPromotion promotion){
        if(!promotion.quoteId().equals(id)||!promotion.owner().equals(owner)||!promotion.originalTotal().equals(total))throw FlightFixture.conflict("Coupon is not bound to this quotation.");
        var remaining=promotion.discount().decimal();var netLines=new ArrayList<Line>();
        for(var line:lines){var reduction=remaining.min(line.base().decimal());remaining=remaining.subtract(reduction);netLines.add(new Line(line.passengerId(),TravelMoney.inr(line.base().decimal().subtract(reduction)),line.tax(),line.seat(),line.meal(),line.baggage(),TravelMoney.inr(line.total().decimal().subtract(reduction))));}
        var net=TravelMoney.inr(netLines.stream().map(l->l.total().decimal()).reduce(BigDecimal.ZERO,(a,b)->a.add(b)));
        if(remaining.signum()!=0||!net.equals(promotion.total()))throw FlightFixture.conflict("Coupon amount is not valid for the base fare.");
        return new ProviderQuote(id,owner,version,flight,selections,netLines,net,cancellationPolicy,createdAt,promotion.validUntil());
    }
}
