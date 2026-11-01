package com.travbook.trips.application.usecase;
import com.travbook.trips.application.port.in.TripDocuments;
import com.travbook.trips.application.port.out.*;
import com.travbook.trips.domain.model.*;
import com.travbook.shared.domain.*;
import java.time.Clock;
import java.util.*;
public final class DocumentService implements TripDocuments {
    private final ConfirmedJourneyPort journeys;private final DocumentRenderer renderer;private final DocumentStore store;private final Clock clock;
    public DocumentService(ConfirmedJourneyPort journeys,DocumentRenderer renderer,DocumentStore store,Clock clock){this.journeys=journeys;this.renderer=renderer;this.store=store;this.clock=clock;}
    private JourneySnapshot eligible(CustomerIdentity identity,UUID id){var s=journeys.load(identity,id);s.requireEligible();return s;}
    public TripDocument create(CustomerIdentity identity,UUID bookingId,String key,UUID correlation){
        if(key==null||!key.matches("[A-Za-z0-9_-]{16,100}"))throw new DomainFailure(DomainFailure.Code.VALIDATION_FAILED,"Use an Idempotency-Key of 16–100 letters, digits, underscores or hyphens.");
        var s=eligible(identity,bookingId);var replay=store.replay(s.customerId(),key,bookingId);if(replay.isPresent())return replay.get();
        var now=clock.instant();var body=renderer.render(s,now);
        return store.create(new TripDocument(UUID.randomUUID(),s.customerId(),bookingId,s.checkoutId(),s.checkoutVersion(),now,body,TripDocument.digest(body)),key,correlation);
    }
    public List<TripDocument> list(CustomerIdentity identity,UUID bookingId){var s=eligible(identity,bookingId);return store.list(s.customerId(),bookingId);}
    public TripDocument download(CustomerIdentity identity,UUID bookingId,UUID id){
        var s=eligible(identity,bookingId);return store.find(s.customerId(),bookingId,id).orElseThrow(()->new DomainFailure(DomainFailure.Code.NOT_FOUND,"Document not found."));
    }
}
