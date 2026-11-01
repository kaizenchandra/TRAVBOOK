package com.travbook.hotels.domain.model;
import java.math.BigDecimal;
import java.util.*;
public record HotelProperty(String id,String supplier,String name,Type type,String city,String locality,String address,String landmark,
                            String timezone,String description,int stars,BigDecimal rating,int reviewCount,String reviewSource,List<String> amenities,
                            List<Image> images,String checkInTime,String checkOutTime,List<String> houseRules) {
    public enum Type { HOTEL,HOMESTAY }
    public record Image(String url,String alt,String attribution) {}
    public HotelProperty {amenities=List.copyOf(amenities);images=List.copyOf(images);houseRules=List.copyOf(houseRules);}
    public boolean matches(String query) {
        return String.join(" ",id,name,city,locality,address,landmark).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
    }
}
