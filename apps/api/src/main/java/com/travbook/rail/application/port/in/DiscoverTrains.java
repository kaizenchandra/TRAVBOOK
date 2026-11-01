package com.travbook.rail.application.port.in;
import com.travbook.rail.domain.model.Rail.*;
import java.util.*;
public interface DiscoverTrains {
    enum Sort {DEPARTURE,DURATION}
    enum ClassFilter {ALL,SL,THREE_AC,TWO_AC}
    enum StatusFilter {ALL,AVAILABLE,RAC,WAITLIST,UNAVAILABLE,NOT_OPEN}
    record Page(Snapshot snapshot,List<Journey> items,int total,int page,int size) {}
    record Details(Snapshot snapshot,Journey journey) {}
    record Check(UUID journeyId,Availability previous,Availability current,boolean changed) {}
    List<Station> stations(String query);
    Page search(Criteria criteria);
    Page page(UUID id,int page,int size,Sort sort,ClassFilter travelClass,StatusFilter status);
    Details journey(UUID id);
    Check availability(UUID id,TravelClass travelClass,Quota quota);
}
