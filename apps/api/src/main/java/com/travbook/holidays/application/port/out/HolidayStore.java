package com.travbook.holidays.application.port.out;
import com.travbook.holidays.domain.model.Holiday.*;
import java.util.*;
public interface HolidayStore{Optional<UUID> replay(UUID owner,String operation,String key,String fingerprint);Quote save(Quote q,String key,String fingerprint,UUID correlation);Quote quote(UUID owner,UUID id);Enquiry saveEnquiry(UUID owner,UUID quote,Contact contact,String key,String fingerprint,UUID correlation);UUID enquiryForQuote(UUID owner,UUID quote);Enquiry enquiry(UUID owner,UUID id);}
