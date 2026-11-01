package com.travbook.hotels.application.port.out;
import com.travbook.hotels.domain.model.*;
import java.time.Instant;
import java.util.List;
public interface HotelSupplierGateway {
    record Result(List<HotelProperty> properties,List<HotelOffer> offers,List<HotelSnapshot.Coverage> coverage) {}
    Result search(HotelCriteria criteria,Instant now);
    default List<HotelOffer> changeRates(java.util.UUID reservation,String property,HotelCriteria criteria,List<HotelOffer.Plan> plans,List<java.util.UUID> ids,boolean deferred,Instant now){throw new UnsupportedOperationException("Change rates unavailable");}
    List<HotelOffer> reprice(HotelCriteria criteria,List<HotelOffer> selection,Instant now);
}
