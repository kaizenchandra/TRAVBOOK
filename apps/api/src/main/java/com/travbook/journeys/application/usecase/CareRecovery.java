package com.travbook.journeys.application.usecase;
import com.travbook.journeys.application.port.out.*;
import com.travbook.journeys.domain.model.*;
import com.travbook.shared.domain.Recovery.*;
import com.travbook.shared.domain.DomainFailure;
import java.util.*;
public final class CareRecovery implements JourneyCareRecovery {
 private final JourneyCareStore store;private final JourneyCareEffects effects;private final JourneyStore bookings;
 public CareRecovery(JourneyCareStore s,JourneyCareEffects e,JourneyStore b){store=s;effects=e;bookings=b;}
 public Optional<Case> get(Kind kind,UUID id){return store.reviewCase(Journey.Kind.valueOf(kind.name()),id);}
 public Optional<Action> replay(String actor,String key,String f){return store.recoveryReplay(actor,key,f);}
 public Action resume(Kind kind,UUID id,long version,String actor,Reason reason,String key,String f,UUID correlation){var c=get(kind,id).orElseThrow();if(c.version()!=version||!c.recoverable())Journey.conflict("Care review changed or exhausted its recovery budget.");var a=store.reviewCheckpoint(Journey.Kind.valueOf(kind.name()),id,version);try{if(a.step()==Care.Phase.WAIT_REPLACEMENT){var child=bookings.booking(a.booking().quote().owner(),a.replacementId());if(child.status()!=Journey.Status.CONFIRMED&&!(child.status()==Journey.Status.FAILED&&child.step()==Journey.Step.DONE))throw new IllegalStateException("Replacement unresolved");}else if(a.step()!=Care.Phase.VALIDATE&&!effects.recoveryEvidence(a))throw new IllegalStateException("Unverified effect");}catch(RuntimeException unknown){throw new DomainFailure(DomainFailure.Code.BOOKING_CONFLICT,"Matching current provider/payment evidence is required; no new effect was requested.");}return store.resume(Journey.Kind.valueOf(kind.name()),id,version,actor,reason,key,f,correlation);}
}
