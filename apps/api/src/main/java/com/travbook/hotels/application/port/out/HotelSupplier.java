package com.travbook.hotels.application.port.out;
import com.travbook.hotels.domain.model.*;
import java.time.Instant;
import java.util.List;
public interface HotelSupplier {
    record Inventory(List<HotelProperty> properties,List<HotelOffer> offers) {}
    String id();
    Inventory search(HotelCriteria criteria,Instant now);
    default List<HotelOffer> changeRates(java.util.UUID reservation,String property,HotelCriteria criteria,List<HotelOffer.Plan> plans,List<java.util.UUID> ids,boolean deferred,Instant now){throw new UnsupportedOperationException("Change rates unavailable");}
    List<HotelOffer> reprice(HotelCriteria criteria,List<HotelOffer> selection,Instant now);
}
