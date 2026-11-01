package com.travbook.search.adapter.in.web;
import com.travbook.search.application.port.in.DiscoverFlights;
import com.travbook.search.domain.model.*;
import java.time.*;
import java.util.*;
final class FlightViews {
    record MoneyView(String amount,String currency) {
        static MoneyView of(Money m) { return new MoneyView(m.amount().toPlainString(),m.currency()); }
    }
    record LegView(String origin,String destination,OffsetDateTime departure,OffsetDateTime arrival,String timezone,int durationMinutes,int stops,String flightNumber) {
        static LegView of(FlightOffer.Leg l) { return new LegView(l.origin(),l.destination(),l.departure(),l.arrival(),l.timezone(),l.durationMinutes(),l.stops(),l.flightNumber()); }
    }
    record OfferView(UUID id,String supplier,String supplierReference,String airline,String mode,List<LegView> legs,
                     MoneyView base,MoneyView taxes,MoneyView total,int passengers,String cabin,String baggage,String cancellationPolicy,Instant observedAt,Instant expiresAt) {
        static OfferView of(FlightOffer o) { return new OfferView(o.id(),o.supplier(),o.supplierReference(),o.airline(),"MOCK",o.legs().stream().map(LegView::of).toList(),
            MoneyView.of(o.base()),MoneyView.of(o.taxes()),MoneyView.of(o.total()),o.passengers(),o.cabin(),o.baggage(),o.cancellationPolicy(),o.observedAt(),o.expiresAt()); }
    }
    record CriteriaView(String origin,String destination,LocalDate departureDate,LocalDate returnDate,int adults,int children,int infants,FlightQuery.Cabin cabin) {
        static CriteriaView of(FlightQuery q) { return new CriteriaView(q.origin(),q.destination(),q.departureDate(),q.returnDate(),q.adults(),q.children(),q.infants(),q.cabin()); }
    }
    record CoverageView(String supplier,SearchSnapshot.Coverage.Status status) {}
    record PageView(UUID id,CriteriaView criteria,List<OfferView> offers,List<CoverageView> coverage,boolean partial,String mode,Instant createdAt,Instant expiresAt,int total,int page,int size) {
        static PageView of(DiscoverFlights.Page p) {
            var s=p.snapshot();
            return new PageView(s.id(),CriteriaView.of(s.criteria()),p.offers().stream().map(OfferView::of).toList(),
                s.coverage().stream().map(c -> new CoverageView(c.supplier(),c.status())).toList(),s.partial(),"MOCK",s.createdAt(),s.expiresAt(),p.total(),p.page(),p.size());
        }
    }
    record RepriceView(OfferView offer,MoneyView previousTotal,boolean changed,Instant checkedAt,Instant validUntil,String mode) {
        static RepriceView of(DiscoverFlights.Reprice r) { return new RepriceView(OfferView.of(r.offer()),MoneyView.of(r.previousTotal()),r.changed(),r.checkedAt(),r.validUntil(),"MOCK"); }
    }
}

