package com.travbook.customer.application.usecase;
import com.travbook.customer.domain.model.TravelOrganizer;
import com.travbook.customer.application.port.in.*;
import com.travbook.customer.application.port.out.OrganizerStore;
import com.travbook.shared.domain.*;
import java.time.Clock;
import java.util.UUID;
public final class OrganizerService implements CustomerOrganizer {
    private final Customers customers;private final OrganizerStore store;private final Clock clock;
    public OrganizerService(Customers customers,OrganizerStore store,Clock clock){this.customers=customers;this.store=store;this.clock=clock;}
    public TravelOrganizer get(CustomerIdentity identity){return store.get(customers.resolve(identity).id());}
    public TravelOrganizer.Preferences preferences(UUID owner){return store.get(owner).fields().preferences();}
    public Saved save(CustomerIdentity identity,UUID expected,long version,TravelOrganizer.Fields fields,boolean consent,String key,UUID correlation){
        var owner=customers.resolve(identity).id();if(!owner.equals(expected))throw new DomainFailure(DomainFailure.Code.PROFILE_IDENTITY_CONFLICT,"Your account changed. Reload before saving.");
        if(!consent||version<0||fields==null||key==null||!key.matches("[A-Za-z0-9._:-]{16,128}"))throw new DomainFailure(DomainFailure.Code.VALIDATION_FAILED,"Consent, valid version, fields and a 16–128 character request key are required.");
        return store.save(owner,version,fields,key,correlation,clock.instant());
    }
}
