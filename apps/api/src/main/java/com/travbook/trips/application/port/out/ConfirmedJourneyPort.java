package com.travbook.trips.application.port.out;
import com.travbook.trips.domain.model.JourneySnapshot;
import com.travbook.shared.domain.CustomerIdentity;
import java.util.UUID;
public interface ConfirmedJourneyPort { JourneySnapshot load(CustomerIdentity identity,UUID bookingId); }
