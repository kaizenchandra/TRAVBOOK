package com.travbook.holidays.application.port.in;
import com.travbook.holidays.domain.model.Holiday.*;
import com.travbook.shared.domain.CustomerIdentity;
import java.util.UUID;
public interface ExploreHolidays{
 Quote revalidateForBooking(UUID owner,UUID id);Page list(Filter filter);com.travbook.holidays.domain.model.Holiday.Package detail(String id);Quote quote(CustomerIdentity identity,Selection selection,String key,UUID correlation);Quote quote(CustomerIdentity identity,UUID id);Enquiry enquire(CustomerIdentity identity,UUID quote,Contact contact,boolean consent,String key,UUID correlation);UUID enquiryForQuote(CustomerIdentity identity,UUID quote);Enquiry enquiry(CustomerIdentity identity,UUID id);}
