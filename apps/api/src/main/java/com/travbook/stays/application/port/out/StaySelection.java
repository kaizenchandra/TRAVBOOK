package com.travbook.stays.application.port.out;
import com.travbook.stays.domain.model.Stay.*;
import java.util.*;
public interface StaySelection {Selection reprice(List<UUID> ids);
 default Selection reprice(List<UUID> ids,CollectionMethod method){if(method!=CollectionMethod.PAY_NOW)throw new IllegalArgumentException("Unsupported collection method");return reprice(ids);}void validate(Quote quote);
 default Selection change(Booking booking,com.travbook.hotels.domain.model.HotelCriteria criteria,List<com.travbook.hotels.domain.model.HotelOffer.Plan> plans){throw new UnsupportedOperationException();}
 default void validateChange(Booking booking){throw new UnsupportedOperationException();}}
