package com.travbook.journeys.application.port.out;
import com.travbook.shared.domain.Recovery.*;
import java.util.*;
public interface JourneyCareRecovery {
 Optional<Case> get(Kind kind,UUID id);
 Optional<Action> replay(String actor,String key,String fingerprint);
 Action resume(Kind kind,UUID id,long version,String actor,Reason reason,String key,String fingerprint,UUID correlation);
}
