package com.travbook.customer.application.port.in;

import com.travbook.customer.domain.model.*;
import com.travbook.shared.domain.CustomerIdentity;
import java.util.UUID;

public interface CustomerProfiles {
    Profile get(CustomerIdentity identity);
    Saved save(CustomerIdentity identity, ProfileChange change, String key, UUID correlation);
    record Saved(long appliedVersion, boolean replayed, Profile profile) {}
}
