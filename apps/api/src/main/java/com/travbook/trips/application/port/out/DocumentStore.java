package com.travbook.trips.application.port.out;
import com.travbook.trips.domain.model.TripDocument;
import java.util.*;
public interface DocumentStore {
    Optional<TripDocument> replay(UUID owner,String key,UUID bookingId);
    TripDocument create(TripDocument document,String key,UUID correlationId);
    List<TripDocument> list(UUID owner,UUID bookingId);
    Optional<TripDocument> find(UUID owner,UUID bookingId,UUID id);
}
