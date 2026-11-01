package com.travbook.rail.application.port.out;
import com.travbook.rail.domain.model.Rail.*;
import java.time.Instant;
import java.util.List;
public interface RailSupplier {
    String id();
    List<Journey> search(Criteria criteria,Station origin,Station destination,Instant now);
    Availability availability(Journey journey,TravelClass travelClass,Quota quota,Instant now);
}
