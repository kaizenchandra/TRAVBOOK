package com.travbook.hotels.application.port.out;
import com.travbook.hotels.domain.model.HotelSnapshot;
import java.util.*;
public interface HotelSearchRepository {
    void save(HotelSnapshot snapshot);
    Optional<HotelSnapshot> find(UUID id);
    Optional<HotelSnapshot> findByOffer(UUID offerId);
}
