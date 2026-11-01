package com.travbook.railtickets.application.usecase;
import com.travbook.shared.application.port.in.ReviewRecovery;
import com.travbook.shared.domain.Recovery.*;
import com.travbook.shared.domain.DomainFailure;
import com.travbook.railtickets.application.port.out.*;
import com.travbook.railtickets.domain.model.RailTicket.Step;
import java.util.*;
public final class RailTicketRecovery implements ReviewRecovery {
 private final RailTicketStore store;private final RailTicketEffects effects;
 public RailTicketRecovery(RailTicketStore store,RailTicketEffects effects){this.store=store;this.effects=effects;}
 public Kind kind(){return Kind.RAIL;}
 public List<Case> list(int page){return store.reviews(page);}
 public Case get(UUID id){return store.review(id);}
 public Action resume(UUID id,long version,String actor,Reason reason,String key,UUID correlation){String fingerprint=id+":"+version+":"+reason;var replay=store.recoveryReplay(actor,key,fingerprint);if(replay.isPresent())return replay.get();var current=store.review(id);if(current.version()!=version||!current.recoverable())throw new DomainFailure(DomainFailure.Code.BOOKING_CONFLICT,"Review is not recoverable at this version.");var checkpoint=store.checkpoint(id,version);if(checkpoint.step()!=Step.VALIDATE){try{if(effects.lookup(checkpoint,checkpoint.step()).isEmpty())throw new IllegalStateException("No verified receipt");}catch(RuntimeException unavailable){throw new DomainFailure(DomainFailure.Code.BOOKING_CONFLICT,"A matching provider receipt is required before recovery; no operation was replayed.");}}return store.resume(id,version,actor,reason,key,fingerprint,correlation);}

 public Action reconcileLegacy(UUID id,long version,String actor,Reason reason,String key,UUID correlation){String fingerprint="LEGACY:"+id+":"+version+":"+reason;var replay=store.recoveryReplay(actor,key,fingerprint);if(replay.isPresent())return replay.get();var b=store.legacyReview(id,version);com.travbook.shared.domain.Recovery.Proof proof;try{proof=effects.verifiedLegacyOutcome(b).orElseThrow();}catch(RuntimeException unavailable){throw new DomainFailure(DomainFailure.Code.BOOKING_CONFLICT,"Current provider state and immutable receipts do not prove a terminal outcome. No effects were requested.");}return store.reconcileLegacy(id,version,actor,reason,key,fingerprint,correlation,proof);}
}
