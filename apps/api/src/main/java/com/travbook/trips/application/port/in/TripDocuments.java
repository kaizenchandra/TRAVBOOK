package com.travbook.trips.application.port.in;
import com.travbook.trips.domain.model.TripDocument;
import com.travbook.shared.domain.CustomerIdentity;
import java.util.*;
public interface TripDocuments {
    TripDocument create(CustomerIdentity identity,UUID bookingId,String key,UUID correlationId);
    List<TripDocument> list(CustomerIdentity identity,UUID bookingId);
    TripDocument download(CustomerIdentity identity,UUID bookingId,UUID documentId);
}
