package com.travbook.hotels.domain.model;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
public record HotelOffer(UUID id,String propertyId,String supplier,int roomIndex,String roomType,Plan planCode,Meal mealPlan,
                         int maxAdults,int maxChildren,HotelCriteria.Occupancy occupancy,List<String> amenities,List<Night> nights,
                         HotelMoney subtotal,HotelMoney taxes,HotelMoney fees,HotelMoney total,boolean refundable,
                         Instant freeCancellationUntil,String cancellationPolicy,String paymentCondition,Instant observedAt,Instant expiresAt) {
    public enum Plan { FLEX,VALUE,FAMILY,STUDIO }
    public enum Meal { BREAKFAST,ROOM_ONLY }
    public record Night(LocalDate date,HotelMoney amount) {}
    public HotelOffer {
        amenities=List.copyOf(amenities);nights=List.copyOf(nights);
        if(id==null||roomIndex<0||roomIndex>3||occupancy.adults()>maxAdults||occupancy.childAges().size()>maxChildren
            ||nights.isEmpty()||nights.size()>30||!expiresAt.isAfter(observedAt)||!Set.of("MOCK_PAY_NOW","MOCK_PAY_AT_PROPERTY").contains(paymentCondition)
            ||nights.stream().map(n->n.amount().amount()).reduce(BigDecimal.ZERO,(a,b)->a.add(b)).compareTo(subtotal.amount())!=0
            ||subtotal.amount().add(taxes.amount()).add(fees.amount()).compareTo(total.amount())!=0
            ||refundable&&(freeCancellationUntil==null||!freeCancellationUntil.isAfter(observedAt)))
            throw new IllegalArgumentException("Invalid hotel room rate or occupancy");
    }
    public HotelOffer collection(boolean deferred) { return new HotelOffer(id,propertyId,supplier,roomIndex,roomType,planCode,mealPlan,maxAdults,maxChildren,occupancy,amenities,nights,subtotal,taxes,fees,total,refundable,freeCancellationUntil,cancellationPolicy,deferred?"MOCK_PAY_AT_PROPERTY":"MOCK_PAY_NOW",observedAt,expiresAt); }
    public boolean sameTerms(HotelOffer other) {
        return id.equals(other.id)&&propertyId.equals(other.propertyId)&&supplier.equals(other.supplier)&&roomIndex==other.roomIndex
            &&roomType.equals(other.roomType)&&planCode==other.planCode&&mealPlan==other.mealPlan&&maxAdults==other.maxAdults&&maxChildren==other.maxChildren
            &&occupancy.equals(other.occupancy)&&amenities.equals(other.amenities)&&nights.equals(other.nights)&&subtotal.equals(other.subtotal)
            &&taxes.equals(other.taxes)&&fees.equals(other.fees)&&total.equals(other.total)&&refundable==other.refundable
            &&Objects.equals(freeCancellationUntil,other.freeCancellationUntil)&&cancellationPolicy.equals(other.cancellationPolicy)&&paymentCondition.equals(other.paymentCondition);
    }
}
