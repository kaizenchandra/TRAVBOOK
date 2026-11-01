package com.travbook.journeys.application.usecase;
import com.travbook.shared.application.port.in.ReviewRecovery;
import com.travbook.shared.domain.Recovery.*;
import com.travbook.shared.domain.DomainFailure;
import com.travbook.journeys.application.port.out.*;
import com.travbook.journeys.domain.model.Journey;
import java.util.*;
/** Privileged resumption is evidence-gated, never a manual final-status override. */
public final class JourneyRecovery implements ReviewRecovery {
 private final Kind kind;private final Journey.Kind product;private final JourneyStore store;private final JourneyEffects effects;private final JourneyCareRecovery care;
 public JourneyRecovery(Kind kind,JourneyStore store,JourneyEffects effects,JourneyCareRecovery care){this.care=care;this.kind=kind;product=Journey.Kind.valueOf(kind.name());this.store=store;this.effects=effects;}
 public Kind kind(){return kind;}
 public List<Case> list(int page){return store.reviews(product,page).stream().map(c->care.get(kind,c.id()).orElse(c)).toList();}
 public Case get(UUID id){return care.get(kind,id).orElseGet(()->store.review(product,id));}
 public Action resume(UUID id,long version,String actor,Reason reason,String key,UUID correlation){String fingerprint=kind+":"+id+":"+version+":"+reason;var extra=care.replay(actor,key,fingerprint);if(extra.isPresent())return extra.get();var prior=store.recoveryReplay(actor,key,fingerprint);if(prior.isPresent())return prior.get();if(care.get(kind,id).isPresent())return care.resume(kind,id,version,actor,reason,key,fingerprint,correlation);var current=store.review(product,id);if(current.version()!=version||!current.recoverable())throw new DomainFailure(DomainFailure.Code.BOOKING_CONFLICT,"This review version has no recoverable checkpoint or has exhausted its recovery budget.");var b=store.checkpoint(product,id,version);if(b.step()!=Journey.Step.VALIDATE_PAYMENT&&b.step()!=Journey.Step.WAIT_PAYMENT){try{if(!effects.recoveryEvidence(b))throw new IllegalStateException("Missing provider receipt");}catch(RuntimeException unavailable){throw new DomainFailure(DomainFailure.Code.BOOKING_CONFLICT,"Matching provider evidence is required; no supplier or financial instruction was sent.");}}return store.resume(product,id,version,actor,reason,key,fingerprint,correlation);}
 public Action reconcileLegacy(UUID id,long version,String actor,Reason reason,String key,UUID correlation){store.review(product,id);throw new DomainFailure(DomainFailure.Code.BOOKING_CONFLICT,"Journey reviews without immutable checkpoints require investigation; legacy terminal correction is unsupported.");}
}
