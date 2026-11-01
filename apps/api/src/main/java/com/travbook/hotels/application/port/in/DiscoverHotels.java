package com.travbook.hotels.application.port.in;
import com.travbook.hotels.domain.model.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
public interface DiscoverHotels {
    enum Sort {PRICE,RATING}
    enum TypeFilter {ALL,HOTEL,HOMESTAY}
    enum Amenity {ALL,WIFI,POOL,PARKING}
    record Filters(int page,int size,Sort sort,TypeFilter type,int minStars,Amenity amenity,boolean refundable,BigDecimal maxTotal) {
        public static Filters defaults(){return new Filters(0,5,Sort.PRICE,TypeFilter.ALL,0,Amenity.ALL,false,new BigDecimal("9999999"));}
    }
    record Result(HotelProperty property,HotelMoney fromTotal,boolean refundableAvailable) {}
    record Page(HotelSnapshot snapshot,List<Result> items,int total,int page,int size) {}
    record Rooms(HotelSnapshot snapshot,HotelProperty property,List<HotelOffer> offers) {}
    record Reprice(UUID searchId,String propertyId,List<HotelOffer> offers,HotelMoney previousTotal,HotelMoney total,boolean changed,Instant checkedAt,Instant validUntil) {}
    Page search(HotelCriteria criteria);
    Page page(UUID searchId,Filters filters);
    default List<HotelOffer> changeRates(UUID reservation,String property,HotelCriteria criteria,List<HotelOffer.Plan> plans,List<UUID> ids,boolean deferred){throw new UnsupportedOperationException();}
    Rooms rooms(UUID searchId,String propertyId);
    Reprice reprice(UUID anchorOfferId,List<UUID> offerIds);
    Reprice reprice(UUID anchorOfferId,List<UUID> offerIds,boolean deferred);
}
