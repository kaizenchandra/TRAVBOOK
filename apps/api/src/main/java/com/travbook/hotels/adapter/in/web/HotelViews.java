package com.travbook.hotels.adapter.in.web;
import com.travbook.hotels.application.port.in.DiscoverHotels;
import com.travbook.hotels.domain.model.*;
import java.time.*;
import java.util.*;
final class HotelViews {
    record Money(String amount,String currency){static Money of(HotelMoney v){return new Money(v.amount().toPlainString(),v.currency());}}
    record Occupancy(int adults,List<Integer> childAges){static Occupancy of(HotelCriteria.Occupancy o){return new Occupancy(o.adults(),o.childAges());}}
    record Criteria(String destination,LocalDate checkIn,LocalDate checkOut,List<Occupancy> rooms){static Criteria of(HotelCriteria c){return new Criteria(c.destination(),c.checkIn(),c.checkOut(),c.rooms().stream().map(Occupancy::of).toList());}}
    record Image(String url,String alt,String attribution) {}
    record Property(String id,String supplier,String name,HotelProperty.Type type,String city,String locality,String address,String landmark,String timezone,
                    String description,int stars,String rating,int reviewCount,String reviewSource,List<String> amenities,List<Image> images,String checkInTime,String checkOutTime,List<String> houseRules) {
        static Property of(HotelProperty p){return new Property(p.id(),p.supplier(),p.name(),p.type(),p.city(),p.locality(),p.address(),p.landmark(),p.timezone(),p.description(),p.stars(),p.rating().toPlainString(),p.reviewCount(),p.reviewSource(),p.amenities(),p.images().stream().map(i->new Image(i.url(),i.alt(),i.attribution())).toList(),p.checkInTime(),p.checkOutTime(),p.houseRules());}
    }
    record Night(LocalDate date,Money amount) {}
    record Offer(UUID id,String propertyId,String supplier,int roomIndex,String roomType,HotelOffer.Plan planCode,HotelOffer.Meal mealPlan,int maxAdults,int maxChildren,Occupancy occupancy,
                 List<String> amenities,List<Night> nights,Money subtotal,Money taxes,Money fees,Money total,boolean refundable,Instant freeCancellationUntil,
                 String cancellationPolicy,String paymentCondition,Instant observedAt,Instant expiresAt) {
        static Offer of(HotelOffer o){return new Offer(o.id(),o.propertyId(),o.supplier(),o.roomIndex(),o.roomType(),o.planCode(),o.mealPlan(),o.maxAdults(),o.maxChildren(),Occupancy.of(o.occupancy()),o.amenities(),o.nights().stream().map(n->new Night(n.date(),Money.of(n.amount()))).toList(),Money.of(o.subtotal()),Money.of(o.taxes()),Money.of(o.fees()),Money.of(o.total()),o.refundable(),o.freeCancellationUntil(),o.cancellationPolicy(),o.paymentCondition(),o.observedAt(),o.expiresAt());}
    }
    record Coverage(String supplier,HotelSnapshot.Coverage.Status status) {}
    record Result(Property property,Money fromTotal,boolean refundableAvailable) {}
    record Page(UUID id,String mode,Criteria criteria,Instant createdAt,Instant expiresAt,boolean partial,List<Coverage> coverage,List<Result> items,int total,int page,int size){
        static Page of(DiscoverHotels.Page p){var s=p.snapshot();return new Page(s.id(),"MOCK",Criteria.of(s.criteria()),s.createdAt(),s.expiresAt(),s.coverage().stream().anyMatch(c->c.status()==HotelSnapshot.Coverage.Status.UNAVAILABLE),s.coverage().stream().map(c->new Coverage(c.supplier(),c.status())).toList(),p.items().stream().map(r->new Result(Property.of(r.property()),Money.of(r.fromTotal()),r.refundableAvailable())).toList(),p.total(),p.page(),p.size());}
    }
    record Rooms(UUID searchId,String mode,Criteria criteria,Property property,List<Offer> offers,Instant expiresAt){
        static Rooms of(DiscoverHotels.Rooms r){return new Rooms(r.snapshot().id(),"MOCK",Criteria.of(r.snapshot().criteria()),Property.of(r.property()),r.offers().stream().map(Offer::of).toList(),r.snapshot().expiresAt());}
    }
    record Reprice(String mode,UUID searchId,String propertyId,List<Offer> offers,Money previousTotal,Money total,boolean changed,Instant checkedAt,Instant validUntil,boolean reservationCreated){
        static Reprice of(DiscoverHotels.Reprice r){return new Reprice("MOCK",r.searchId(),r.propertyId(),r.offers().stream().map(Offer::of).toList(),Money.of(r.previousTotal()),Money.of(r.total()),r.changed(),r.checkedAt(),r.validUntil(),false);}
    }
}
