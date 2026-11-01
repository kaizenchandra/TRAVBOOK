package com.travbook.rail.adapter.in.web;
import com.travbook.rail.domain.model.Rail.*;
import com.travbook.rail.application.port.in.DiscoverTrains;
import java.time.*;
import java.util.*;
final class RailViews {
    record MoneyView(String amount,String currency){static MoneyView of(Money m){return new MoneyView(m.amount().toPlainString(),m.currency());}}
    record AvailabilityView(TravelClass travelClass,Quota quota,Status status,int position,MoneyView base,MoneyView fees,MoneyView total,String fareBasis,Instant observedAt,Instant validUntil,Instant quotaOpensAt,List<String> restrictions){static AvailabilityView of(Availability a){return new AvailabilityView(a.travelClass(),a.quota(),a.status(),a.position(),MoneyView.of(a.base()),MoneyView.of(a.fees()),MoneyView.of(a.total()),"ONE_ADULT_DEMONSTRATION",a.observedAt(),a.validUntil(),a.quotaOpensAt(),a.restrictions());}}
    record JourneyView(UUID id,String supplier,String number,String name,Station origin,Station destination,OffsetDateTime departure,OffsetDateTime arrival,int durationMinutes,List<DayOfWeek> operatingDays,List<Stop> route,List<AvailabilityView> availability){static JourneyView of(Journey j){return new JourneyView(j.id(),j.supplier(),j.number(),j.name(),j.origin(),j.destination(),j.departure(),j.arrival(),j.durationMinutes(),j.operatingDays(),j.route(),j.availability().stream().map(a->AvailabilityView.of(a)).toList());}}
    record CoverageView(String supplier,String status){}
    record Page(UUID id,String mode,Criteria criteria,Instant createdAt,Instant expiresAt,boolean partial,List<CoverageView> coverage,List<JourneyView> items,int total,int page,int size){static Page of(DiscoverTrains.Page p){var s=p.snapshot();return new Page(s.id(),"MOCK",s.criteria(),s.createdAt(),s.expiresAt(),s.coverage().stream().anyMatch(c->!c.available()),s.coverage().stream().map(c->new CoverageView(c.supplier(),c.available()?"AVAILABLE":"UNAVAILABLE")).toList(),p.items().stream().map(j->JourneyView.of(j)).toList(),p.total(),p.page(),p.size());}}
    record Details(String mode,UUID searchId,JourneyView journey,Instant expiresAt,boolean ticketingEnabled){static Details of(DiscoverTrains.Details d){return new Details("MOCK",d.snapshot().id(),JourneyView.of(d.journey()),d.snapshot().expiresAt(),false);}}
    record Check(String mode,UUID journeyId,AvailabilityView previous,AvailabilityView current,boolean changed,boolean ticketingEnabled){static Check of(DiscoverTrains.Check c){return new Check("MOCK",c.journeyId(),AvailabilityView.of(c.previous()),AvailabilityView.of(c.current()),c.changed(),false);}}
}
