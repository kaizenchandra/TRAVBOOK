package com.travbook.operations.application.usecase;
import com.travbook.operations.application.port.in.Operations;
import com.travbook.shared.application.port.in.ReviewRecovery;
import com.travbook.shared.domain.Recovery.*;
import com.travbook.shared.domain.DomainFailure;
import java.util.*;
public final class OperationsService implements Operations {
 private final Map<Kind,ReviewRecovery> providers=new EnumMap<>(Kind.class);
 public OperationsService(List<ReviewRecovery> providers){providers.forEach(p->{if(this.providers.put(p.kind(),p)!=null)throw new IllegalArgumentException("Duplicate recovery provider");});}
 private ReviewRecovery provider(Kind kind){if(!providers.containsKey(kind))throw new DomainFailure(DomainFailure.Code.VALIDATION_FAILED,"Unsupported recovery category.");return providers.get(kind);}
 public List<Case> list(Kind kind,int page){if(page<0||page>100)throw new DomainFailure(DomainFailure.Code.VALIDATION_FAILED,"Review page must be 0–100.");return provider(kind).list(page);}
 public Case get(Kind kind,UUID id){return provider(kind).get(id);}
 public Action resume(Kind kind,UUID id,long version,String actor,Reason reason,boolean consent,String key,UUID correlation){if(!consent||reason==null||version<1||actor==null||!actor.matches("[0-9a-f]{64}")||key==null||!key.matches("[A-Za-z0-9_-]{16,100}"))throw new DomainFailure(DomainFailure.Code.VALIDATION_FAILED,"Explicit consent, version, reason and idempotency key are required.");return provider(kind).resume(id,version,actor,reason,key,correlation);}
 public Action reconcileLegacy(Kind kind,UUID id,long version,String actor,Reason reason,boolean consent,String key,UUID correlation){if(!consent||reason==null||version<1||actor==null||!actor.matches("[0-9a-f]{64}")||key==null||!key.matches("[A-Za-z0-9_-]{16,100}"))throw new DomainFailure(DomainFailure.Code.VALIDATION_FAILED,"Explicit consent, version, reason and idempotency key are required.");return provider(kind).reconcileLegacy(id,version,actor,reason,key,correlation);}
}
