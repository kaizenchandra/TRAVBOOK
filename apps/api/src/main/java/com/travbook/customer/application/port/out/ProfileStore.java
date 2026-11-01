package com.travbook.customer.application.port.out;

import com.travbook.customer.application.port.in.CustomerProfiles.Saved;
import com.travbook.customer.domain.model.*;
import java.time.Instant;
import java.util.UUID;

public interface ProfileStore {
    Profile get(UUID owner);
    Saved save(UUID owner, ProfileChange change, String key, UUID correlation, Instant now);
}
