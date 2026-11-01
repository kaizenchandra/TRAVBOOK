package com.travbook.rail.application.port.out;
import com.travbook.rail.domain.model.Rail.*;
import java.time.Instant;
import java.util.List;
public interface RailGateway {
    record Result(List<Journey> journeys,List<Coverage> coverage) {}
    Result search(Criteria criteria,Station origin,Station destination,Instant now);
    Availability availability(Journey journey,TravelClass travelClass,Quota quota,Instant now);
}
