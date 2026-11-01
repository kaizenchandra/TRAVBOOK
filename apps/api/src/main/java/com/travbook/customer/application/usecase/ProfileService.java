package com.travbook.customer.application.usecase;

import com.travbook.customer.application.port.in.*;
import com.travbook.customer.application.port.out.ProfileStore;
import com.travbook.customer.domain.model.*;
import com.travbook.shared.domain.*;
import java.time.Clock;
import java.util.UUID;

public final class ProfileService implements CustomerProfiles {
    private final Customers customers;
    private final ProfileStore store;
    private final Clock clock;
    public ProfileService(Customers customers, ProfileStore store, Clock clock) { this.customers=customers; this.store=store; this.clock=clock; }
    public Profile get(CustomerIdentity identity) { return store.get(customers.resolve(identity).id()); }
    public Saved save(CustomerIdentity identity, ProfileChange change, String key, UUID correlation) {
        if (key == null || !key.matches("[A-Za-z0-9._:-]{16,128}") || correlation == null)
            throw new DomainFailure(DomainFailure.Code.VALIDATION_FAILED, "Provide a valid idempotency key.");
        var owner=customers.resolve(identity).id();
        if (!owner.equals(change.expectedCustomerId()))
            throw new DomainFailure(DomainFailure.Code.PROFILE_IDENTITY_CONFLICT,"Your signed-in account changed. Reload the account page before saving.");
        return store.save(owner, change, key, correlation, clock.instant());
    }
}
