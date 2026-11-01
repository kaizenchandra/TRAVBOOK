package com.travbook.customer.application.port.in;
import com.travbook.customer.domain.model.TravelOrganizer;
import com.travbook.shared.domain.CustomerIdentity;
import java.util.UUID;
public interface CustomerOrganizer {
    record Saved(long appliedVersion,boolean replayed,TravelOrganizer organizer){}
    TravelOrganizer get(CustomerIdentity identity);
    /** Trusted internal read; callers must obtain the owner from a verified principal or owned lifecycle event. */
    TravelOrganizer.Preferences preferences(UUID owner);
    Saved save(CustomerIdentity identity,UUID expectedCustomerId,long expectedVersion,TravelOrganizer.Fields fields,boolean consent,String key,UUID correlation);
}
