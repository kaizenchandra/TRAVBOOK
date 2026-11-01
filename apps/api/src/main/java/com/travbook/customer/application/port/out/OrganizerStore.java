package com.travbook.customer.application.port.out;
import com.travbook.customer.domain.model.TravelOrganizer;
import com.travbook.customer.application.port.in.CustomerOrganizer.Saved;
import java.time.Instant;
import java.util.UUID;
public interface OrganizerStore {
    TravelOrganizer get(UUID owner);
    Saved save(UUID owner,long expectedVersion,TravelOrganizer.Fields fields,String key,UUID correlation,Instant now);
}
