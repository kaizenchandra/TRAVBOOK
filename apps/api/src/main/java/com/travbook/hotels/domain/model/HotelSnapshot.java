package com.travbook.hotels.domain.model;
import java.time.Instant;
import java.util.*;
public record HotelSnapshot(UUID id,HotelCriteria criteria,List<HotelProperty> properties,List<HotelOffer> offers,
                            List<Coverage> coverage,Instant createdAt,Instant expiresAt) {
    public record Coverage(String supplier,Status status) {public enum Status {AVAILABLE,UNAVAILABLE}}
    public HotelSnapshot {properties=List.copyOf(properties);offers=List.copyOf(offers);coverage=List.copyOf(coverage);}
}
